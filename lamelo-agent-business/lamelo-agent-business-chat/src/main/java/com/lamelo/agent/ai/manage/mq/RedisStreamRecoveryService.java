package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentTask;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskMapper;
import com.lamelo.agent.enums.DocumentTaskStatusEnum;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.manage.mq.redis-stream", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamRecoveryService {
    private static final long PAGE_SIZE = 100;
    private static final RedisScript<Long> DELETE_IF_NOT_PENDING = new DefaultRedisScript<>(
        "local ok, groups = pcall(redis.call, 'XINFO', 'GROUPS', KEYS[1]) "
            + "if not ok then return 0 end "
            + "local last = nil "
            + "for _, group in ipairs(groups) do "
            + "  local name = nil "
            + "  for i = 1, #group, 2 do "
            + "    if group[i] == 'name' then name = group[i + 1] end "
            + "    if group[i] == 'last-delivered-id' then last = group[i + 1] end "
            + "  end "
            + "  if name == ARGV[1] then break end "
            + "  last = nil "
            + "end "
            + "if not last then return 0 end "
            + "local candidateMs, candidateSeq = string.match(ARGV[2], '^(%d+)%-(%d+)$') "
            + "local lastMs, lastSeq = string.match(last, '^(%d+)%-(%d+)$') "
            + "if not candidateMs or not lastMs then return 0 end "
            + "if tonumber(candidateMs) > tonumber(lastMs) or "
            + "   (tonumber(candidateMs) == tonumber(lastMs) and tonumber(candidateSeq) > tonumber(lastSeq)) then return 0 end "
            + "local pending = redis.call('XPENDING', KEYS[1], ARGV[1], ARGV[2], ARGV[2], 1) "
            + "if #pending > 0 then return 0 else return redis.call('XDEL', KEYS[1], ARGV[2]) end", Long.class);

    private final StringRedisTemplate redisTemplate;
    private final RedisStreamProperties properties;
    private final RedisStreamDeadLetterService deadLetters;
    private final RedisStreamConsumer consumer;
    private final LaMeloAgentDocumentTaskMapper taskMapper;
    private final String recoveryConsumer = "recovery:" + ProcessHandle.current().pid() + ":" + UUID.randomUUID();

    @EventListener(ApplicationReadyEvent.class)
    public void recoverAtStartup() {
        recoverAll();
    }

    @Scheduled(fixedDelay = 30000)
    public void recoverAll() {
        recover(StreamKey.PARSE, properties.getParseGroup());
        recover(StreamKey.INDEX, properties.getIndexGroup());
    }

    public void recover(StreamKey streamKey, String group) {
        String stream = streamKey.stream(properties);
        Range<String> range = Range.unbounded();
        try {
            while (true) {
                PendingMessages pending = redisTemplate.opsForStream().pending(stream, group, range, PAGE_SIZE);
                if (pending == null || pending.isEmpty()) break;
                for (PendingMessage item : pending) {
                    if (item.getElapsedTimeSinceLastDelivery().getSeconds() <= properties.getClaimIdleSeconds()) continue;
                    recoverOne(streamKey, group, item);
                }
                if (pending.size() < PAGE_SIZE) break;
                range = Range.rightUnbounded(Range.Bound.exclusive(pending.get(pending.size() - 1).getIdAsString()));
            }
            trimAcknowledgedHistory(stream, group);
        } catch (RuntimeException error) {
            log.error("Redis Stream recovery scan failed, stream={}", stream, error);
        }
    }

    private void recoverOne(StreamKey streamKey, String group, PendingMessage pending) {
        String stream = streamKey.stream(properties);
        try {
            List<MapRecord<String, String, String>> claimed = redisTemplate.<String, String>opsForStream().claim(
                stream, group, recoveryConsumer, Duration.ofSeconds(properties.getClaimIdleSeconds()), pending.getId());
            if (claimed == null || claimed.isEmpty()) return;
            for (MapRecord<String, String, String> record : claimed) {
                Map<String, String> fields = new LinkedHashMap<>(record.getValue());
                fields.put("originalRecordId", record.getId().getValue());
                int retryCount;
                try {
                    retryCount = Integer.parseInt(fields.get("retryCount"));
                    if (retryCount < 0) throw new NumberFormatException("negative retryCount");
                    if (fields.containsKey("nextAttemptAt")) Instant.parse(fields.get("nextAttemptAt"));
                } catch (RuntimeException malformed) {
                    deadLetters.move(streamKey, group, fields, malformed);
                    continue;
                }
                LaMeloAgentDocumentTask task = taskFor(fields);
                if (task != null && DocumentTaskStatusEnum.SUCCESS.getCode().equals(task.getTaskStatus())) {
                    acknowledge(stream, group, record.getId());
                    continue;
                }
                if (task != null && DocumentTaskStatusEnum.RUNNING.getCode().equals(task.getTaskStatus())
                    && task.getLeaseUntil() != null && task.getLeaseUntil().toInstant().isAfter(Instant.now())) {
                    continue;
                }
                if (retryCount >= properties.getMaximumRetries()) {
                    deadLetters.move(streamKey, group, fields,
                        new IllegalStateException(task == null ? "Task is still missing after retry limit"
                            : task.getErrorMsg() == null ? "Retry limit exhausted" : task.getErrorMsg()));
                } else if (fields.containsKey("nextAttemptAt")
                    && !Boolean.TRUE.equals(redisTemplate.hasKey(attemptKey(stream, record.getId())))) {
                    consumer.handleRecovered(streamKey, record);
                } else {
                    requeue(stream, group, record, fields, retryCount + 1);
                }
            }
        } catch (RuntimeException error) {
            log.error("Redis Stream pending recovery failed; original remains pending, stream={}, id={}",
                stream, pending.getId(), error);
        }
    }

    private LaMeloAgentDocumentTask taskFor(Map<String, String> fields) {
        try {
            return taskMapper.selectById(Long.parseLong(fields.get("taskId")));
        } catch (NumberFormatException malformed) {
            return null;
        }
    }

    private void acknowledge(String stream, String group, RecordId id) {
        Long acknowledged = redisTemplate.opsForStream().acknowledge(stream, group, id);
        if (acknowledged != null && acknowledged > 0) {
            redisTemplate.opsForStream().delete(stream, id);
            redisTemplate.delete(attemptKey(stream, id));
        }
    }

    static String attemptKey(String stream, RecordId id) {
        return stream + ":attempt:" + id.getValue();
    }

    private void trimAcknowledgedHistory(String stream, String group) {
        long maxLength = properties.getAcknowledgedHistoryMaxLength();
        if (maxLength < 1) return;
        Long size = redisTemplate.opsForStream().size(stream);
        if (size == null || size <= maxLength) return;

        long toRemove = size - maxLength;
        Range<String> range = Range.unbounded();
        while (toRemove > 0) {
            List<MapRecord<String, String, String>> records = redisTemplate.<String, String>opsForStream()
                .range(stream, range, Limit.limit().count((int) PAGE_SIZE));
            if (records == null || records.isEmpty()) break;
            for (MapRecord<String, String, String> record : records) {
                if (toRemove <= 0) break;
                if (deleteIfNotPending(stream, group, record.getId()) > 0) toRemove--;
            }
            if (records.size() < PAGE_SIZE) break;
            range = Range.rightUnbounded(Range.Bound.exclusive(
                records.get(records.size() - 1).getId().getValue()));
        }
    }

    private long deleteIfNotPending(String stream, String group, RecordId id) {
        Long deleted = redisTemplate.execute(DELETE_IF_NOT_PENDING, List.of(stream), group, id.getValue());
        return deleted == null ? 0 : deleted;
    }

    private void requeue(String stream, String group, MapRecord<String, String, String> record,
                         Map<String, String> fields, int retryCount) {
        fields.put("retryCount", Integer.toString(retryCount));
        fields.put("nextAttemptAt", Instant.now().plusSeconds(properties.getRetryDelaySeconds()).toString());
        RecordId retryId = redisTemplate.opsForStream().add(StreamRecords.newRecord().in(stream).ofMap(fields));
        if (retryId == null) throw new IllegalStateException("Retry append returned no record ID");
        acknowledge(stream, group, record.getId());
    }
}
