package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentTask;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskMapper;
import com.lamelo.agent.enums.DocumentTaskStatusEnum;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisStreamRecoveryServiceTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final StreamOperations<String, String, String> streams = mock(StreamOperations.class);
    private final RedisStreamProperties properties = new RedisStreamProperties();
    private final RedisStreamConsumer consumer = mock(RedisStreamConsumer.class);
    private final LaMeloAgentDocumentTaskMapper mapper = mock(LaMeloAgentDocumentTaskMapper.class);
    private RedisStreamDeadLetterService deadLetters;
    private RedisStreamRecoveryService recovery;

    @BeforeEach
    void setUp() {
        when(redis.<String, String>opsForStream()).thenReturn(streams);
        properties.setParseStream("test:parse");
        properties.setParseGroup("parse-group");
        properties.setClaimIdleSeconds(120);
        properties.setRetryDelaySeconds(30);
        properties.setMaximumRetries(3);
        deadLetters = new RedisStreamDeadLetterService(redis, properties);
        recovery = new RedisStreamRecoveryService(redis, properties, deadLetters, consumer, mapper);
    }

    @Test
    void claimsIdleMessageAndRequeuesWithIncrementedRetryCount() {
        pending(121);
        when(streams.claim(eq("test:parse"), eq("parse-group"), any(String.class),
            eq(Duration.ofSeconds(120)), eq(RecordId.of("1-0")))).thenReturn(List.of(record("0")));
        when(streams.add(any(MapRecord.class))).thenReturn(RecordId.of("2-0"));

        recovery.recover(StreamKey.PARSE, "parse-group");

        ArgumentCaptor<MapRecord> appended = ArgumentCaptor.forClass(MapRecord.class);
        verify(streams).add(appended.capture());
        assertThat(appended.getValue().getStream()).isEqualTo("test:parse");
        Map<?, ?> retryFields = (Map<?, ?>) appended.getValue().getValue();
        assertThat(retryFields.get("retryCount")).isEqualTo("1");
        assertThat(retryFields.get("originalRecordId")).isEqualTo("1-0");
        assertThat(retryFields.containsKey("nextAttemptAt")).isTrue();
        var ordered = inOrder(streams);
        ordered.verify(streams).add(any(MapRecord.class));
        ordered.verify(streams).acknowledge("test:parse", "parse-group", RecordId.of("1-0"));
    }

    @Test
    void exhaustedMessageAppendsDeadLetterBeforeAck() {
        pending(121);
        when(streams.claim(eq("test:parse"), eq("parse-group"), any(String.class),
            eq(Duration.ofSeconds(120)), eq(RecordId.of("1-0")))).thenReturn(List.of(record("3")));
        when(streams.add(any(MapRecord.class))).thenReturn(RecordId.of("3-0"));

        recovery.recover(StreamKey.PARSE, "parse-group");

        ArgumentCaptor<MapRecord> appended = ArgumentCaptor.forClass(MapRecord.class);
        verify(streams).add(appended.capture());
        assertThat(appended.getValue().getStream()).isEqualTo("test:parse:dead");
        Map<?, ?> deadFields = (Map<?, ?>) appended.getValue().getValue();
        assertThat(deadFields.get("retryCount")).isEqualTo("3");
        assertThat(deadFields.get("originalRecordId")).isEqualTo("1-0");
        var ordered = inOrder(streams);
        ordered.verify(streams).add(any(MapRecord.class));
        ordered.verify(streams).acknowledge("test:parse", "parse-group", RecordId.of("1-0"));
    }

    @Test
    void deadLetterAppendFailureLeavesOriginalPending() {
        pending(121);
        when(streams.claim(eq("test:parse"), eq("parse-group"), any(String.class),
            eq(Duration.ofSeconds(120)), eq(RecordId.of("1-0")))).thenReturn(List.of(record("3")));
        when(streams.add(any(MapRecord.class))).thenThrow(new IllegalStateException("redis unavailable"));

        recovery.recover(StreamKey.PARSE, "parse-group");

        verify(streams, never()).acknowledge(eq("test:parse"), eq("parse-group"), any(RecordId.class));
    }

    @Test
    void newServiceInstanceRecoversExistingPendingMessage() {
        pending(121);
        when(streams.claim(eq("test:parse"), eq("parse-group"), any(String.class),
            eq(Duration.ofSeconds(120)), eq(RecordId.of("1-0")))).thenReturn(List.of(record("0")));
        when(streams.add(any(MapRecord.class))).thenReturn(RecordId.of("2-0"));

        new RedisStreamRecoveryService(redis, properties, deadLetters, consumer, mapper)
            .recover(StreamKey.PARSE, "parse-group");

        verify(streams).claim(eq("test:parse"), eq("parse-group"), any(String.class),
            eq(Duration.ofSeconds(120)), eq(RecordId.of("1-0")));
    }

    @Test
    void validLeaseDoesNotConsumeRetryBudget() {
        pending(121);
        when(streams.claim(eq("test:parse"), eq("parse-group"), any(String.class),
            eq(Duration.ofSeconds(120)), eq(RecordId.of("1-0")))).thenReturn(List.of(record("0")));
        LaMeloAgentDocumentTask task = new LaMeloAgentDocumentTask();
        task.setTaskStatus(DocumentTaskStatusEnum.RUNNING.getCode());
        task.setLeaseUntil(Date.from(java.time.Instant.now().plusSeconds(900)));
        when(mapper.selectById(12L)).thenReturn(task);

        recovery.recover(StreamKey.PARSE, "parse-group");

        verify(streams, never()).add(any(MapRecord.class));
        verify(streams, never()).acknowledge(eq("test:parse"), eq("parse-group"), any(RecordId.class));
    }

    @Test
    void completedTaskIsAcknowledgedWithoutRetry() {
        pending(121);
        when(streams.claim(eq("test:parse"), eq("parse-group"), any(String.class),
            eq(Duration.ofSeconds(120)), eq(RecordId.of("1-0")))).thenReturn(List.of(record("3")));
        LaMeloAgentDocumentTask task = new LaMeloAgentDocumentTask();
        task.setTaskStatus(DocumentTaskStatusEnum.SUCCESS.getCode());
        when(mapper.selectById(12L)).thenReturn(task);

        recovery.recover(StreamKey.PARSE, "parse-group");

        verify(streams, never()).add(any(MapRecord.class));
        verify(streams).acknowledge("test:parse", "parse-group", RecordId.of("1-0"));
    }

    @Test
    void markerAbsentAtRetryLimitGoesToDeadLetterWithoutDispatch() {
        pending(121);
        when(streams.claim(eq("test:parse"), eq("parse-group"), any(String.class),
            eq(Duration.ofSeconds(120)), eq(RecordId.of("1-0")))).thenReturn(List.of(recordWithRetry("3", true)));
        when(streams.add(any(MapRecord.class))).thenReturn(RecordId.of("3-0"));
        when(redis.hasKey(any(String.class))).thenReturn(false);

        recovery.recover(StreamKey.PARSE, "parse-group");

        verify(consumer, never()).handleRecovered(any(), any(MapRecord.class));
        ArgumentCaptor<MapRecord> appended = ArgumentCaptor.forClass(MapRecord.class);
        verify(streams).add(appended.capture());
        assertThat(appended.getValue().getStream()).isEqualTo("test:parse:dead");
    }

    @Test
    void exactlyIdleThresholdIsNotClaimed() {
        pending(120);

        recovery.recover(StreamKey.PARSE, "parse-group");

        verify(streams, never()).claim(any(String.class), any(String.class), any(String.class),
            any(Duration.class), any(RecordId.class));
    }

    @Test
    void trimRemovesOnlyAcknowledgedSourceEntries() {
        properties.setAcknowledgedHistoryMaxLength(1);
        pending(121);
        when(streams.claim(eq("test:parse"), eq("parse-group"), any(String.class),
            eq(Duration.ofSeconds(120)), eq(RecordId.of("1-0")))).thenReturn(List.of());
        when(streams.size("test:parse")).thenReturn(3L);
        when(streams.range(eq("test:parse"), any(Range.class), any(Limit.class))).thenReturn(List.of(
            recordWithId("1-0"), recordWithId("2-0"), recordWithId("3-0")));
        org.mockito.Mockito.doAnswer(invocation -> {
            String id = (String) invocation.getArgument(3);
            return "1-0".equals(id) ? 0L : 1L;
        }).when(redis).execute(any(RedisScript.class), anyList(), any(), any());

        recovery.recover(StreamKey.PARSE, "parse-group");

        verify(redis, org.mockito.Mockito.times(3)).execute(any(RedisScript.class), anyList(), any(), any());
        verify(streams, never()).delete(any(String.class), any(RecordId.class));
    }

    @Test
    void trimPreservesUnconsumedBacklogWhenConsumerHasNotDeliveredIt() {
        properties.setAcknowledgedHistoryMaxLength(1);
        pending(121);
        when(streams.claim(eq("test:parse"), eq("parse-group"), any(String.class),
            eq(Duration.ofSeconds(120)), eq(RecordId.of("1-0")))).thenReturn(List.of());
        when(streams.size("test:parse")).thenReturn(3L);
        when(streams.range(eq("test:parse"), any(Range.class), any(Limit.class))).thenReturn(List.of(
            recordWithId("1-0"), recordWithId("2-0"), recordWithId("3-0")));
        org.mockito.Mockito.doAnswer(invocation -> 0L)
            .when(redis).execute(any(RedisScript.class), anyList(), any(), any());

        recovery.recover(StreamKey.PARSE, "parse-group");

        verify(streams, never()).delete(any(String.class), any(RecordId.class));
        verify(redis, org.mockito.Mockito.times(3)).execute(any(RedisScript.class), anyList(), any(), any());
    }

    private void pending(long idleSeconds) {
        PendingMessage message = new PendingMessage(RecordId.of("1-0"),
            Consumer.from("parse-group", "old-consumer"), Duration.ofSeconds(idleSeconds), 1);
        when(streams.pending(eq("test:parse"), eq("parse-group"), any(Range.class), eq(100L)))
            .thenReturn(new PendingMessages("parse-group", List.of(message)));
    }

    private MapRecord<String, String, String> record(String retryCount) {
        return recordWithRetry(retryCount, false);
    }

    private MapRecord<String, String, String> recordWithRetry(String retryCount, boolean delayed) {
        Map<String, String> fields = new java.util.LinkedHashMap<>(Map.of(
            "messageId", "message-1", "documentId", "11", "taskId", "12",
            "createdAt", "2026-09-29T00:00:00Z", "retryCount", retryCount));
        if (delayed) fields.put("nextAttemptAt", "2026-09-29T00:00:00Z");
        return MapRecord.<String, String, String>create("test:parse", fields)
            .withId(RecordId.of("1-0"));
    }

    private MapRecord<String, String, String> recordWithId(String id) {
        return MapRecord.<String, String, String>create("test:parse", Map.of(
            "messageId", "message-1", "documentId", "11", "taskId", "12",
            "createdAt", "2026-09-29T00:00:00Z", "retryCount", "0"))
            .withId(RecordId.of(id));
    }
}
