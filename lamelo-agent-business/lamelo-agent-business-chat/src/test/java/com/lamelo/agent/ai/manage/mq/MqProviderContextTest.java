package com.lamelo.agent.ai.manage.mq;

import com.lamelo.agent.ai.manage.config.DocumentManageProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class MqProviderContextTest {

    @Test
    void defaultConfigurationProvidesExactlyOneRedisPublisher() {
        new ApplicationContextRunner()
            .withUserConfiguration(RedisStreamConfiguration.class)
            .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
            .withBean(DocumentManageProperties.class, DocumentManageProperties::new)
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBeansOfType(DocumentTaskPublisher.class)).hasSize(1);
                assertThat(context.getBean(DocumentTaskPublisher.class))
                    .isInstanceOf(RedisStreamProducer.class);
            });
    }
}
