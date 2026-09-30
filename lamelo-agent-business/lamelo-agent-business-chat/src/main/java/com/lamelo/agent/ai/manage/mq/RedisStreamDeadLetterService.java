package com.lamelo.agent.ai.manage.mq;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.manage.mq.redis-stream", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisStreamDeadLetterService {
    private final StringRedisTemplate redisTemplate;
    private final RedisStreamProperties properties;

    public void move(StreamKey streamKey, String group, Map<String, String> message, Throwable error) {
        String stream = streamKey.stream(properties);
        String originalRecordId = message.get("originalRecordId");
        if (originalRecordId == null || originalRecordId.isBlank()) {
            throw new IllegalArgumentException("originalRecordId is required for dead-letter ACK");
        }
        Map<String, String> fields = new LinkedHashMap<>(message);
        fields.put("lastError", error == null ? "Retry limit exhausted" : String.valueOf(error.getMessage()));
        fields.put("lastFailedAt", Instant.now().toString());
        fields.putIfAbsent("createdAt", Instant.now().toString());
        RecordId deadId = redisTemplate.opsForStream().add(StreamRecords.newRecord()
            .in(stream + ":dead").ofMap(fields));
        if (deadId == null) throw new IllegalStateException("Dead-letter append returned no record ID");
        RecordId originalId = RecordId.of(originalRecordId);
        Long acknowledged = redisTemplate.opsForStream().acknowledge(stream, group, originalId);
        if (acknowledged != null && acknowledged > 0) {
            redisTemplate.opsForStream().delete(stream, originalId);
            redisTemplate.delete(RedisStreamRecoveryService.attemptKey(stream, originalId));
        }
    }
}
