package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.config.DocumentManageProperties;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.RedisCallback;
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
        try {
            template.execute((RedisCallback<Object>) connection -> connection.execute("XGROUP",
                bytes("CREATE"), bytes(stream), bytes(group), bytes("0"), bytes("MKSTREAM")));
        } catch (RuntimeException exception) {
            if (!isBusyGroup(exception)) {
                throw exception;
            }
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

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
