package com.lamelo.agent.ai.auth.support;

public interface SmsSender {
    void send(String mobile, String purpose, String code);
}
