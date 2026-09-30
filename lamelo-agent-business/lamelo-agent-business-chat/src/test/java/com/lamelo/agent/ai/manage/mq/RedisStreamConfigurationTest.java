package com.lamelo.agent.ai.manage.mq;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.core.StreamOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RedisStreamConfigurationTest {

    @Test
    void createsBothGroupsWithMkstream() throws Exception {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        when(template.<String, String>opsForStream()).thenReturn(streams);
        RedisStreamProperties properties = new RedisStreamProperties();
        properties.setParseStream("test:parse");
        properties.setIndexStream("test:index");
        properties.setParseGroup("parse-group");
        properties.setIndexGroup("index-group");

        InitializingBean groups = new RedisStreamConfiguration().redisStreamGroups(template, properties);
        groups.afterPropertiesSet();

        verify(streams).createGroup("test:parse", ReadOffset.from("0"), "parse-group");
        verify(streams).createGroup("test:index", ReadOffset.from("0"), "index-group");
    }

    @Test
    void ignoresBusyGroupAndPropagatesOtherRedisErrors() throws Exception {
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        StreamOperations<String, String, String> streams = mock(StreamOperations.class);
        when(template.<String, String>opsForStream()).thenReturn(streams);
        RedisStreamProperties properties = new RedisStreamProperties();
        when(streams.createGroup(properties.getParseStream(), ReadOffset.from("0"), properties.getParseGroup()))
            .thenThrow(new IllegalStateException("BUSYGROUP Consumer Group name already exists"));
        when(streams.createGroup(properties.getIndexStream(), ReadOffset.from("0"), properties.getIndexGroup()))
            .thenThrow(new IllegalStateException("connection refused"));

        InitializingBean groups = new RedisStreamConfiguration().redisStreamGroups(template, properties);

        assertThatThrownBy(groups::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("connection refused");
    }
}
