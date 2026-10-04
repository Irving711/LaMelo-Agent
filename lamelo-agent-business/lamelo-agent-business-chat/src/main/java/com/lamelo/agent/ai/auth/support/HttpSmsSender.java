package com.lamelo.agent.ai.auth.support;

import com.lamelo.agent.ai.auth.config.SmsProperties;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
public class HttpSmsSender implements SmsSender {
    private static final Logger log = LoggerFactory.getLogger(HttpSmsSender.class);
    private final SmsProperties properties;
    private final HttpClient client;

    public HttpSmsSender(SmsProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofMillis(Math.max(500, properties.getConnectTimeoutMs())))
            .build();
    }

    @Override
    public void send(String mobile, String purpose, String code) {
        if (properties.getGatewayUrl() == null || properties.getGatewayUrl().isBlank()) {
            log.info("SMS code generated (gateway disabled), mobile={}, purpose={}, code={}", mobile, purpose, code);
            return;
        }
        String body = "{\"mobile\":\"" + json(mobile) + "\",\"purpose\":\"" + json(purpose)
            + "\",\"code\":\"" + json(code) + "\"}";
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(properties.getGatewayUrl()))
            .timeout(Duration.ofMillis(Math.max(1000, properties.getReadTimeoutMs())))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body));
        if (properties.getBearerToken() != null && !properties.getBearerToken().isBlank()) {
            builder.header("Authorization", "Bearer " + properties.getBearerToken());
        }
        try {
            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new LaMeloAgentFrameException(502, "短信服务商发送失败");
            }
        } catch (LaMeloAgentFrameException exception) {
            throw exception;
        } catch (Exception exception) {
            log.warn("SMS gateway request failed, mobile={}, purpose={}", mobile, purpose, exception);
            throw new LaMeloAgentFrameException(502, "短信服务暂不可用，请稍后再试");
        }
    }

    private String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
