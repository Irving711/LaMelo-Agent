package com.lamelo.agent.ai.manage.mq;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class RedisStreamConfigurationTest {

    @Test
    void createsBothGroupsWithMkstream() throws Exception {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisConnection connection = mock(RedisConnection.class);
        doAnswer(invocation -> ((RedisCallback<?>) invocation.getArgument(0)).doInRedis(connection))
            .when(template).execute(any(RedisCallback.class));
        RedisStreamProperties properties = new RedisStreamProperties();
        properties.setParseStream("test:parse");
        properties.setIndexStream("test:index");
        properties.setParseGroup("parse-group");
        properties.setIndexGroup("index-group");

        InitializingBean groups = new RedisStreamConfiguration().redisStreamGroups(template, properties);
        groups.afterPropertiesSet();

        verify(connection).execute(eq("XGROUP"), eq(bytes("CREATE")), eq(bytes("test:parse")),
            eq(bytes("parse-group")), eq(bytes("0")), eq(bytes("MKSTREAM")));
        verify(connection).execute(eq("XGROUP"), eq(bytes("CREATE")), eq(bytes("test:index")),
            eq(bytes("index-group")), eq(bytes("0")), eq(bytes("MKSTREAM")));
    }

    @Test
    void ignoresOnlyBusyGroupAndPropagatesOtherRedisErrors() throws Exception {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        RedisStreamProperties properties = new RedisStreamProperties();
        doAnswer(invocation -> { throw new IllegalStateException("BUSYGROUP Consumer Group name already exists"); })
            .doAnswer(invocation -> { throw new IllegalStateException("connection refused"); })
            .when(template).execute(any(RedisCallback.class));

        InitializingBean groups = new RedisStreamConfiguration().redisStreamGroups(template, properties);

        assertThatThrownBy(groups::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("connection refused");
    }

    private byte[] bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8);
    }
}
