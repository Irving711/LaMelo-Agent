package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentTask;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskMapper;
import com.lamelo.agent.ai.manage.service.DocumentAsyncProcessService;
import com.lamelo.agent.enums.DocumentTaskStatusEnum;
import com.lamelo.agent.enums.DocumentTaskTypeEnum;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.manage.mq.redis-stream", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamConsumer implements SmartLifecycle {
    private final StringRedisTemplate redisTemplate;
    private final RedisStreamProperties properties;
    private final LaMeloAgentDocumentTaskMapper taskMapper;
    private final DocumentTaskLeaseService leaseService;
    private final DocumentAsyncProcessService asyncProcessService;
    private final RedisStreamDeadLetterService deadLetters;
    private final String instanceId = UUID.randomUUID().toString();

    private volatile boolean running;
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> parseContainer;
    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> indexContainer;
    private ScheduledExecutorService renewalExecutor;
    private ExecutorService parseExecutor;
    private ExecutorService indexExecutor;

    @Override
    public synchronized void start() {
        if (running) return;
        try {
            renewalExecutor = Executors.newSingleThreadScheduledExecutor();
            parseExecutor = executor("document-parse-stream");
            indexExecutor = executor("document-index-stream");
            parseContainer = container(parseExecutor);
            indexContainer = container(indexExecutor);
            String consumerName = consumerIdentity();
            parseContainer.receive(Consumer.from(properties.getParseGroup(), consumerName + ":parse"),
                StreamOffset.create(properties.getParseStream(), ReadOffset.lastConsumed()), this::handleParse);
            indexContainer.receive(Consumer.from(properties.getIndexGroup(), consumerName + ":index"),
                StreamOffset.create(properties.getIndexStream(), ReadOffset.lastConsumed()), this::handleIndex);
            parseContainer.start();
            indexContainer.start();
            running = true;
        } catch (RuntimeException error) {
            closeContainers();
            throw error;
        }
    }

    @Override
    public synchronized void stop() {
        if (!running) return;
        closeContainers();
        running = false;
    }

    private void closeContainers() {
        if (parseContainer != null) parseContainer.stop();
        if (indexContainer != null) indexContainer.stop();
        if (renewalExecutor != null) renewalExecutor.shutdownNow();
        if (parseExecutor != null) parseExecutor.shutdownNow();
        if (indexExecutor != null) indexExecutor.shutdownNow();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private ExecutorService executor(String threadName) {
        // Each stream has one poll thread, limiting parse and index consumption to one each.
        return Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    private StreamMessageListenerContainer<String, MapRecord<String, String, String>> container(ExecutorService executor) {
        var options = StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
            .serializer(new StringRedisSerializer())
            .pollTimeout(Duration.ofSeconds(1))
            .executor(executor)
            .errorHandler(error -> log.error("Redis Stream listener error", error))
            .build();
        return StreamMessageListenerContainer.create(redisTemplate.getConnectionFactory(), options);
    }

    void handleParse(MapRecord<String, String, String> record) {
        handle(record, properties.getParseStream(), properties.getParseGroup(), false);
    }

    void handleIndex(MapRecord<String, String, String> record) {
        handle(record, properties.getIndexStream(), properties.getIndexGroup(), true);
    }

    void handleRecovered(StreamKey streamKey, MapRecord<String, String, String> record) {
        if (streamKey == StreamKey.INDEX) handleIndex(record);
        else handleParse(record);
    }

    private void handle(MapRecord<String, String, String> record, String stream, String group, boolean index) {
        final long documentId;
        final long taskId;
        final Long planId;
        final Instant nextAttemptAt;
        try {
            Map<String, String> fields = record.getValue();
            requireText(fields, "messageId");
            Instant.parse(requireText(fields, "createdAt"));
            long retryCount = Long.parseLong(requireText(fields, "retryCount"));
            if (retryCount < 0) throw new IllegalArgumentException("retryCount must be nonnegative");
            nextAttemptAt = fields.containsKey("nextAttemptAt")
                ? Instant.parse(requireText(fields, "nextAttemptAt")) : null;
            documentId = positiveLong(fields, "documentId");
            taskId = positiveLong(fields, "taskId");
            planId = index ? positiveLong(fields, "planId") : null;
        } catch (RuntimeException malformed) {
            deadLetterMalformed(record, stream, group, malformed);
            return;
        }

        if (nextAttemptAt != null && Instant.now().isBefore(nextAttemptAt)) {
            try {
                long remainingMillis = Duration.between(Instant.now(), nextAttemptAt).toMillis();
                if (remainingMillis > Duration.ofSeconds(properties.getRetryDelaySeconds() + 60).toMillis()) {
                    deadLetterMalformed(record, stream, group,
                        new IllegalArgumentException("nextAttemptAt is too far in the future"));
                    return;
                }
                Thread.sleep(Math.max(0, remainingMillis));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            }
        }

        String owner = consumerIdentity() + ":" + UUID.randomUUID();
        Duration lease = Duration.ofSeconds(properties.getLeaseSeconds());
        try {
            if (nextAttemptAt != null) {
                redisTemplate.opsForValue().set(RedisStreamRecoveryService.attemptKey(stream, record.getId()),
                    "1", Duration.ofSeconds(Math.max(86400, properties.getDeadLetterRetentionSeconds())));
            }
            LaMeloAgentDocumentTask task = taskMapper.selectById(taskId);
            if (task == null) {
                log.warn("Redis Stream task is not visible yet; message remains pending, taskId={}", taskId);
                return;
            }
            int expectedType = index ? DocumentTaskTypeEnum.BUILD_INDEX.getCode()
                : DocumentTaskTypeEnum.PARSE_ROUTE.getCode();
            if (!Long.valueOf(documentId).equals(task.getDocumentId()) ||
                !Integer.valueOf(expectedType).equals(task.getTaskType()) ||
                (index && !planId.equals(task.getPlanId()))) {
                deadLetterMalformed(record, stream, group,
                    new IllegalArgumentException("Message references a missing or mismatched task"));
                return;
            }
            if (DocumentTaskStatusEnum.SUCCESS.getCode().equals(task.getTaskStatus())) {
                acknowledge(stream, group, record.getId());
                return;
            }
            if (!leaseService.tryAcquire(taskId, owner, lease)) return;

            ScheduledFuture<?> renewal = scheduleRenewal(taskId, owner, lease);
            try {
                if (index) {
                    asyncProcessService.handleIndexBuild(documentId, taskId, planId, owner);
                } else {
                    asyncProcessService.handleParseRoute(documentId, taskId, owner);
                }
            } finally {
                if (renewal != null) renewal.cancel(false);
            }

            LaMeloAgentDocumentTask completed = taskMapper.selectById(taskId);
            if (completed != null && DocumentTaskStatusEnum.SUCCESS.getCode().equals(completed.getTaskStatus())
                && leaseService.owns(taskId, owner)) {
                acknowledge(stream, group, record.getId());
            } else {
                leaseService.release(taskId, owner);
                log.warn("Redis Stream task did not succeed; message remains pending, taskId={}", taskId);
            }
        } catch (RuntimeException failure) {
            log.error("Redis Stream processing failed; message remains pending, stream={}, recordId={}",
                stream, record.getId(), failure);
            try {
                leaseService.release(taskId, owner);
            } catch (RuntimeException releaseFailure) {
                log.error("Could not release failed task lease, taskId={}", taskId, releaseFailure);
            }
        }
    }

    private ScheduledFuture<?> scheduleRenewal(long taskId, String owner, Duration lease) {
        long interval = Math.max(1, lease.getSeconds() / 3);
        ScheduledExecutorService executor = renewalExecutor;
        if (executor == null) {
            // Direct invocations in tests do not start the lifecycle.
            return null;
        }
        return executor.scheduleAtFixedRate(() -> {
            try {
                if (!leaseService.renew(taskId, owner, lease)) {
                    log.error("Lost Redis Stream task lease, taskId={}, owner={}", taskId, owner);
                }
            } catch (RuntimeException error) {
                log.error("Could not renew Redis Stream task lease, taskId={}", taskId, error);
            }
        }, interval, interval, TimeUnit.SECONDS);
    }

    private void deadLetterMalformed(MapRecord<String, String, String> record, String stream,
                                     String group, RuntimeException error) {
        try {
            Map<String, String> fields = new java.util.LinkedHashMap<>(record.getValue());
            fields.put("originalRecordId", record.getId().getValue());
            deadLetters.move(stream.equals(properties.getParseStream()) ? StreamKey.PARSE : StreamKey.INDEX,
                group, fields, new IllegalArgumentException("Malformed stream message: " + error.getMessage(), error));
        } catch (RuntimeException failure) {
            log.error("Could not dead-letter malformed Redis Stream message; it remains pending, recordId={}",
                record.getId(), failure);
        }
    }

    private void acknowledge(String stream, String group, RecordId id) {
        Long acknowledged = redisTemplate.opsForStream().acknowledge(stream, group, id);
        if (acknowledged != null && acknowledged > 0) {
            redisTemplate.opsForStream().delete(stream, id);
            redisTemplate.delete(RedisStreamRecoveryService.attemptKey(stream, id));
        }
    }

    private String requireText(Map<String, String> fields, String field) {
        String value = fields.get(field);
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is missing");
        return value;
    }

    private long positiveLong(Map<String, String> fields, String field) {
        long value = Long.parseLong(requireText(fields, field));
        if (value <= 0) throw new IllegalArgumentException(field + " must be positive");
        return value;
    }

    private String consumerIdentity() {
        return properties.getConsumerName() + ":" + ProcessHandle.current().pid() + ":" + instanceId;
    }

}
