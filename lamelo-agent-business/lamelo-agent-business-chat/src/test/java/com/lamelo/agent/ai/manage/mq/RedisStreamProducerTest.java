package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.mq.message.DocumentIndexBuildMessage;
import com.lamelo.agent.ai.manage.mq.message.DocumentParseRouteMessage;
import com.lamelo.agent.enums.DocumentManageCode;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisStreamProducerTest {

    private final StringRedisTemplate template = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final StreamOperations<String, String, String> streams = mock(StreamOperations.class);
    private final RedisStreamProperties properties = new RedisStreamProperties();
    private RedisStreamProducer producer;

    @BeforeEach
    void setUp() {
        properties.setParseStream("test:parse");
        properties.setIndexStream("test:index");
        doReturn(streams).when(template).opsForStream();
        producer = new RedisStreamProducer(template, properties);
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void appendsParseMessageWithEnvelope() {
        when(streams.add(any(MapRecord.class))).thenReturn(RecordId.of("1-0"));

        producer.sendParseRoute(new DocumentParseRouteMessage(11L, 22L));

        ArgumentCaptor<MapRecord> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streams).add(captor.capture());
        MapRecord<String, String, String> record = captor.getValue();
        assertThat(record.getStream()).isEqualTo("test:parse");
        assertThat(record.getValue()).containsEntry("documentId", "11")
            .containsEntry("taskId", "22")
            .containsEntry("retryCount", "0")
            .containsKeys("messageId", "createdAt")
            .doesNotContainKey("planId");
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void appendsIndexMessageWithPlanId() {
        when(streams.add(any(MapRecord.class))).thenReturn(RecordId.of("2-0"));

        producer.sendIndexBuild(new DocumentIndexBuildMessage(11L, 33L, 44L));

        ArgumentCaptor<MapRecord> captor = ArgumentCaptor.forClass(MapRecord.class);
        verify(streams).add(captor.capture());
        MapRecord<String, String, String> record = captor.getValue();
        assertThat(record.getStream()).isEqualTo("test:index");
        assertThat(record.getValue()).containsEntry("documentId", "11")
            .containsEntry("taskId", "33")
            .containsEntry("planId", "44")
            .containsEntry("retryCount", "0")
            .containsKeys("messageId", "createdAt");
    }

    @Test
    void translatesRedisAppendFailure() {
        when(streams.add(any(MapRecord.class))).thenThrow(new IllegalStateException("redis unavailable"));

        assertThatThrownBy(() -> producer.sendParseRoute(new DocumentParseRouteMessage(11L, 22L)))
            .isInstanceOf(LaMeloAgentFrameException.class)
            .hasMessageContaining("redis unavailable")
            .satisfies(exception -> assertThat(((LaMeloAgentFrameException) exception).getCode())
                .isEqualTo(DocumentManageCode.TASK_PUBLISH_FAILED.getCode()));
    }

    @Test
    void requiresAppendAcknowledgment() {
        when(streams.add(any(MapRecord.class))).thenReturn(null);

        assertThatThrownBy(() -> producer.sendParseRoute(new DocumentParseRouteMessage(11L, 22L)))
            .isInstanceOf(LaMeloAgentFrameException.class);
    }
}
