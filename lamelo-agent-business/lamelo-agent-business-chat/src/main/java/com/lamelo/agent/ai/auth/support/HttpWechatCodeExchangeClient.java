package com.lamelo.agent.ai.auth.support;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

@Component
public class HttpWechatCodeExchangeClient implements WechatCodeExchangeClient {
    private final AdminAuthProperties properties;
    private final RestClient client;

    @Autowired
    public HttpWechatCodeExchangeClient(AdminAuthProperties properties) {
        this(properties, createClient(properties));
    }

    public HttpWechatCodeExchangeClient(AdminAuthProperties properties, RestClient client) {
        this.properties = properties;
        this.client = client;
    }

    private static RestClient createClient(AdminAuthProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(properties.getWechatConnectTimeoutMs());
        factory.setReadTimeout(properties.getWechatReadTimeoutMs());
        return RestClient.builder().requestFactory(factory).build();
    }

    @Override
    public WechatSession exchange(String code) {
        if (properties.getWechatAppId() == null || properties.getWechatAppId().isBlank()
            || properties.getWechatAppSecret() == null || properties.getWechatAppSecret().isBlank()) {
            throw new LaMeloAgentFrameException(503, "微信登录未配置");
        }
        try {
            URI requestUri = UriComponentsBuilder.fromUriString(properties.getWechatCode2SessionUrl())
                .queryParam("appid", properties.getWechatAppId())
                .queryParam("secret", properties.getWechatAppSecret())
                .queryParam("js_code", code)
                .queryParam("grant_type", "authorization_code")
                .build()
                .encode()
                .toUri();
            WechatResponse response = client.get()
                .uri(requestUri)
                .retrieve()
                .body(WechatResponse.class);
            if (response == null || response.errcode != null && response.errcode != 0) {
                throw new LaMeloAgentFrameException(401, "微信登录码无效");
            }
            if (response.openid == null || response.openid.isBlank()) {
                throw new LaMeloAgentFrameException(401, "微信登录码无效");
            }
            return new WechatSession(response.openid, response.unionid);
        } catch (LaMeloAgentFrameException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new LaMeloAgentFrameException(502, "微信登录服务暂不可用");
        } catch (IllegalArgumentException exception) {
            throw new LaMeloAgentFrameException(502, "微信登录服务暂不可用");
        }
    }

    private static class WechatResponse {
        private Integer errcode;
        @JsonProperty("errmsg")
        private String errmsg;
        private String openid;
        private String unionid;
    }
}
