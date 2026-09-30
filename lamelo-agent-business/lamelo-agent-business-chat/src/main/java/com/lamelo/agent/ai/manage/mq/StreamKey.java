package com.lamelo.agent.ai.manage.mq;

public enum StreamKey {
    PARSE,
    INDEX;

    public String stream(RedisStreamProperties properties) {
        return this == PARSE ? properties.getParseStream() : properties.getIndexStream();
    }

    public String group(RedisStreamProperties properties) {
        return this == PARSE ? properties.getParseGroup() : properties.getIndexGroup();
    }
}
