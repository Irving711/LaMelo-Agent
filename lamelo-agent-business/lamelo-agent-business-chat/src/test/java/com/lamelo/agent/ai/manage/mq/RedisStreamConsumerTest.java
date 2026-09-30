package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentTask;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskMapper;
import com.lamelo.agent.ai.manage.service.DocumentAsyncProcessService;
import com.lamelo.agent.enums.DocumentTaskStatusEnum;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.mockito.ArgumentCaptor;

class RedisStreamConsumerTest {
    private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
    private final StreamOperations<String, String, String> streams = mock(StreamOperations.class);
    private final LaMeloAgentDocumentTaskMapper mapper = mock(LaMeloAgentDocumentTaskMapper.class);
    private final DocumentTaskLeaseService leases = mock(DocumentTaskLeaseService.class);
    private final DocumentAsyncProcessService async = mock(DocumentAsyncProcessService.class);
    private final RedisStreamProperties properties = new RedisStreamProperties();
    private RedisStreamConsumer consumer;

    @BeforeEach
    void setUp() {
        when(redis.<String, String>opsForStream()).thenReturn(streams);
        when(leases.owns(eq(12L), anyString())).thenReturn(true);
        properties.setLeaseSeconds(900);
        consumer = new RedisStreamConsumer(redis, properties, mapper, leases, async,
            new RedisStreamDeadLetterService(redis, properties));
    }

    @Test
    void dispatchesParseAndAcknowledgesOnlyPersistedSuccess() {
        taskStatuses(DocumentTaskStatusEnum.NEW, DocumentTaskStatusEnum.SUCCESS);
        when(leases.tryAcquire(eq(12L), anyString(), eq(Duration.ofSeconds(900)))).thenReturn(true);

        consumer.handleParse(record(properties.getParseStream(), false));

        verify(async).handleParseRoute(eq(11L), eq(12L), anyString());
        verify(streams).acknowledge(properties.getParseStream(), properties.getParseGroup(), RecordId.of("1-0"));
    }

    @Test
    void dispatchesIndexWithPlanId() {
        LaMeloAgentDocumentTask initial = task(DocumentTaskStatusEnum.NEW);
        LaMeloAgentDocumentTask completed = task(DocumentTaskStatusEnum.SUCCESS);
        initial.setTaskType(2);
        completed.setTaskType(2);
        when(mapper.selectById(12L)).thenReturn(initial, completed);
        when(leases.tryAcquire(eq(12L), anyString(), any(Duration.class))).thenReturn(true);

        consumer.handleIndex(record(properties.getIndexStream(), true));

        verify(async).handleIndexBuild(eq(11L), eq(12L), eq(13L), anyString());
        verify(streams).acknowledge(properties.getIndexStream(), properties.getIndexGroup(), RecordId.of("1-0"));
    }

    @Test
    void alreadySuccessfulTaskIsAcknowledgedWithoutProcessing() {
        taskStatuses(DocumentTaskStatusEnum.SUCCESS);

        consumer.handleParse(record(properties.getParseStream(), false));

        verify(async, never()).handleParseRoute(any(), any(), anyString());
        verify(leases, never()).tryAcquire(any(), anyString(), any());
        verify(streams).acknowledge(properties.getParseStream(), properties.getParseGroup(), RecordId.of("1-0"));
    }

    @Test
    void leaseContentionLeavesMessagePending() {
        taskStatuses(DocumentTaskStatusEnum.RUNNING);
        when(leases.tryAcquire(eq(12L), anyString(), any(Duration.class))).thenReturn(false);

        consumer.handleParse(record(properties.getParseStream(), false));

        verify(async, never()).handleParseRoute(any(), any(), anyString());
        verify(streams, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void missingTaskRemainsPendingForTransactionVisibilityRetry() {
        when(mapper.selectById(12L)).thenReturn(null);

        consumer.handleParse(record(properties.getParseStream(), false));

        verify(streams, never()).add(any(MapRecord.class));
        verify(streams, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void wrongTaskTypeIsRejectedWithoutDispatch() {
        LaMeloAgentDocumentTask wrong = task(DocumentTaskStatusEnum.NEW);
        wrong.setTaskType(2);
        when(mapper.selectById(12L)).thenReturn(wrong);
        when(streams.add(any(MapRecord.class))).thenReturn(RecordId.of("3-0"));

        consumer.handleParse(record(properties.getParseStream(), false));

        verify(async, never()).handleParseRoute(any(), any(), anyString());
        verify(streams).add(any(MapRecord.class));
    }

    @Test
    void separateAcquisitionsUseDifferentOwnerTokens() {
        taskStatuses(DocumentTaskStatusEnum.RUNNING, DocumentTaskStatusEnum.RUNNING);
        when(leases.tryAcquire(eq(12L), anyString(), any(Duration.class))).thenReturn(false);

        consumer.handleParse(record(properties.getParseStream(), false));
        consumer.handleParse(record(properties.getParseStream(), false));

        ArgumentCaptor<String> owners = ArgumentCaptor.forClass(String.class);
        verify(leases, times(2)).tryAcquire(eq(12L), owners.capture(), any(Duration.class));
        org.assertj.core.api.Assertions.assertThat(owners.getAllValues().get(0))
            .isNotEqualTo(owners.getAllValues().get(1));
    }

    @Test
    void swallowedBusinessFailureDoesNotAcknowledge() {
        taskStatuses(DocumentTaskStatusEnum.NEW, DocumentTaskStatusEnum.FAILED);
        when(leases.tryAcquire(eq(12L), anyString(), any(Duration.class))).thenReturn(true);

        consumer.handleParse(record(properties.getParseStream(), false));

        verify(streams, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
        verify(leases).release(eq(12L), anyString());
    }

    @Test
    void thrownBusinessExceptionDoesNotAcknowledge() {
        taskStatuses(DocumentTaskStatusEnum.NEW);
        when(leases.tryAcquire(eq(12L), anyString(), any(Duration.class))).thenReturn(true);
        doThrow(new IllegalStateException("processor unavailable")).when(async)
            .handleParseRoute(eq(11L), eq(12L), anyString());

        consumer.handleParse(record(properties.getParseStream(), false));

        verify(streams, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
        verify(leases).release(eq(12L), anyString());
    }

    @Test
    void lostLeaseCannotAcknowledgeSuccessfulTask() {
        LaMeloAgentDocumentTask initial = task(DocumentTaskStatusEnum.NEW);
        LaMeloAgentDocumentTask completed = task(DocumentTaskStatusEnum.SUCCESS);
        when(leases.owns(eq(12L), anyString())).thenReturn(false);
        when(mapper.selectById(12L)).thenReturn(initial, completed);
        when(leases.tryAcquire(eq(12L), anyString(), any(Duration.class))).thenReturn(true);

        consumer.handleParse(record(properties.getParseStream(), false));

        verify(streams, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    @Test
    void malformedPayloadGoesToDeadLetterBeforeAcknowledgment() {
        MapRecord<String, String, String> bad = MapRecord.<String, String, String>create(
            properties.getParseStream(), Map.of("taskId", "no-number"))
            .withId(RecordId.of("2-0"));
        when(streams.add(any(MapRecord.class))).thenReturn(RecordId.of("3-0"));

        consumer.handleParse(bad);

        verify(streams).add(any(MapRecord.class));
        verify(streams).acknowledge(properties.getParseStream(), properties.getParseGroup(), RecordId.of("2-0"));
        verify(async, never()).handleParseRoute(any(), any(), anyString());
    }

    @Test
    void deadLetterAppendFailureLeavesMalformedMessagePending() {
        MapRecord<String, String, String> bad = MapRecord.<String, String, String>create(
            properties.getParseStream(), Map.of("taskId", "no-number"))
            .withId(RecordId.of("2-0"));
        when(streams.add(any(MapRecord.class))).thenThrow(new IllegalStateException("redis unavailable"));

        consumer.handleParse(bad);

        verify(streams, never()).acknowledge(anyString(), anyString(), any(RecordId.class));
    }

    private void taskStatuses(DocumentTaskStatusEnum... statuses) {
        LaMeloAgentDocumentTask[] tasks = new LaMeloAgentDocumentTask[statuses.length];
        for (int i = 0; i < statuses.length; i++) {
            tasks[i] = task(statuses[i]);
        }
        when(mapper.selectById(12L)).thenReturn(tasks[0], java.util.Arrays.copyOfRange(tasks, 1, tasks.length));
    }

    private LaMeloAgentDocumentTask task(DocumentTaskStatusEnum status) {
        LaMeloAgentDocumentTask task = new LaMeloAgentDocumentTask();
        task.setId(12L);
        task.setDocumentId(11L);
        task.setPlanId(13L);
        task.setTaskStatus(status.getCode());
        task.setTaskType(1);
        task.setLeaseOwner(properties.getConsumerName() + ":" + ProcessHandle.current().pid() + ":1-0");
        return task;
    }

    private MapRecord<String, String, String> record(String stream, boolean index) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("messageId", "message-1");
        fields.put("documentId", "11");
        fields.put("taskId", "12");
        fields.put("createdAt", Instant.now().toString());
        fields.put("retryCount", "0");
        if (index) fields.put("planId", "13");
        return MapRecord.<String, String, String>create(stream, fields).withId(RecordId.of("1-0"));
    }
}
