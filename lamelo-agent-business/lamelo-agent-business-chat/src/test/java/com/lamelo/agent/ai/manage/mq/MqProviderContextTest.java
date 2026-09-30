package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.config.DocumentManageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MqProviderContextTest {

    @Test
    void defaultConfigurationProvidesExactlyOneRedisPublisher() {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        when(template.<String, String>opsForStream()).thenReturn(streams);
        new ApplicationContextRunner()
            .withUserConfiguration(RedisStreamConfiguration.class)
            .withBean(StringRedisTemplate.class, () -> template)
            .withBean(DocumentManageProperties.class, DocumentManageProperties::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBeansOfType(DocumentTaskPublisher.class)).hasSize(1);
                assertThat(context.getBean(DocumentTaskPublisher.class))
                    .isInstanceOf(RedisStreamProducer.class);
            });
    }
}
