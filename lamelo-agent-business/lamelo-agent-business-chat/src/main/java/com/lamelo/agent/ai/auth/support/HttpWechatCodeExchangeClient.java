package com.lamelo.agent.ai.auth.support;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;

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
        // 微信 jscode2session 实际返回 Content-Type: text/plain，
        // 默认 Jackson 转换器只接受 application/json，会导致反序列化失败并误报“微信登录服务暂不可用”。
        MappingJackson2HttpMessageConverter converter = new MappingJackson2HttpMessageConverter();
        converter.setSupportedMediaTypes(List.of(MediaType.APPLICATION_JSON, MediaType.TEXT_PLAIN,
            new MediaType("text", "javascript")));
        return RestClient.builder()
            .requestFactory(factory)
            .messageConverters(converters -> converters.add(0, converter))
            .build();
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
            if (response == null || response.errcode() != null && response.errcode() != 0) {
                throw new LaMeloAgentFrameException(401, "微信登录码无效");
            }
            if (response.openid() == null || response.openid().isBlank()) {
                throw new LaMeloAgentFrameException(401, "微信登录码无效");
            }
            return new WechatSession(response.openid(), response.unionid());
        } catch (LaMeloAgentFrameException exception) {
            throw exception;
        } catch (RestClientException exception) {
            throw new LaMeloAgentFrameException(502, "微信登录服务暂不可用");
        } catch (IllegalArgumentException exception) {
            throw new LaMeloAgentFrameException(502, "微信登录服务暂不可用");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record WechatResponse(Integer errcode, String errmsg, String openid, String unionid) {
    }
}
