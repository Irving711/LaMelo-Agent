package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.mq.message.DocumentIndexBuildMessage;
import com.lamelo.agent.ai.manage.mq.message.DocumentParseRouteMessage;
import com.lamelo.agent.enums.DocumentManageCode;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;

@RequiredArgsConstructor
public class RedisStreamProducer implements DocumentTaskPublisher {

    private final StringRedisTemplate redisTemplate;
    private final RedisStreamProperties properties;

    @Override
    public void sendParseRoute(DocumentParseRouteMessage message) {
        Map<String, String> fields = envelope(message.getDocumentId(), message.getTaskId());
        append(properties.getParseStream(), fields);
    }

    @Override
    public void sendIndexBuild(DocumentIndexBuildMessage message) {
        Map<String, String> fields = envelope(message.getDocumentId(), message.getTaskId());
        fields.put("planId", String.valueOf(message.getPlanId()));
        append(properties.getIndexStream(), fields);
    }

    private Map<String, String> envelope(Long documentId, Long taskId) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("messageId", UUID.randomUUID().toString());
        fields.put("documentId", String.valueOf(documentId));
        fields.put("taskId", String.valueOf(taskId));
        fields.put("createdAt", Instant.now().toString());
        fields.put("retryCount", "0");
        return fields;
    }

    private void append(String stream, Map<String, String> fields) {
        try {
            RecordId id = redisTemplate.opsForStream().add(StreamRecords.newRecord()
                .in(stream).ofMap(fields));
            if (id == null) {
                throw new IllegalStateException("Redis stream append returned no acknowledgment");
            }
        } catch (Exception exception) {
            throw new LaMeloAgentFrameException(DocumentManageCode.TASK_PUBLISH_FAILED.getCode(),
                "Redis Stream 消息发送失败: " + exception.getMessage(), exception);
        }
    }
}
