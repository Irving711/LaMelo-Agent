package com.lamelo.agent.ai.auth.support;

public interface WechatCodeExchangeClient {
    WechatSession exchange(String code);
}
