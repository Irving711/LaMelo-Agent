package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.config.DocumentManageProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@ConditionalOnProperty(prefix = "app.manage.mq.redis-stream", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({RedisStreamProperties.class, DocumentManageProperties.class})
@EnableScheduling
public class RedisStreamConfiguration {

    @Bean
    public RedisStreamProducer redisStreamProducer(StringRedisTemplate redisTemplate,
                                                   RedisStreamProperties properties) {
        return new RedisStreamProducer(redisTemplate, properties);
    }

    @Bean
    public InitializingBean redisStreamGroups(StringRedisTemplate redisTemplate,
                                               RedisStreamProperties properties) {
        return () -> {
            createGroup(redisTemplate, properties.getParseStream(), properties.getParseGroup());
            createGroup(redisTemplate, properties.getIndexStream(), properties.getIndexGroup());
        };
    }

    private void createGroup(StringRedisTemplate template, String stream, String group) {
        StreamOperations<String, String, String> streams = template.opsForStream();
        try {
            streams.createGroup(stream, ReadOffset.from("0"), group);
        } catch (RuntimeException exception) {
            if (!isBusyGroup(exception)) throw exception;
        }
    }

    private boolean isBusyGroup(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause.getMessage() != null && cause.getMessage().contains("BUSYGROUP")) {
                return true;
            }
        }
        return false;
    }
}
