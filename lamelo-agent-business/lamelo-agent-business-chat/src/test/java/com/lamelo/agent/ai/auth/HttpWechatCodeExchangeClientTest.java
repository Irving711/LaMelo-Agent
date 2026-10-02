package com.lamelo.agent.ai.auth;

import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.support.HttpWechatCodeExchangeClient;
import com.lamelo.agent.ai.auth.support.WechatSession;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpWechatCodeExchangeClientTest {
    @Test
    void transportFailureDoesNotExposeCodeSecretOrCause() {
        AdminAuthProperties properties = new AdminAuthProperties();
        properties.setWechatAppId("app-id");
        properties.setWechatAppSecret("secret-value");
        org.junit.jupiter.api.Assertions.assertFalse(properties.toString().contains("secret-value"));
        RestClient client = mock(RestClient.class);
        RestClient.RequestHeadersUriSpec request = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec headers = request;
        RestClient.ResponseSpec response = mock(RestClient.ResponseSpec.class);
        org.mockito.Mockito.doReturn(request).when(client).get();
        org.mockito.Mockito.doReturn(headers).when(request).uri(any(URI.class));
        when(headers.retrieve()).thenReturn(response);
        doThrow(new RestClientException("secret-value code-value"))
            .when(response).body(any(Class.class));

        LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
            () -> new HttpWechatCodeExchangeClient(properties, client).exchange("code-value"));

        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains("secret-value"));
        assertFalse(exception.getMessage().contains("code-value"));
    }

    @Test
    void textPlainSuccessResponseBindsOpenidAndUnionid() throws Exception {
        String payload = "{\"session_key\":\"sk\",\"unionid\":\"UNION123\",\"errmsg\":\"ok\",\"openid\":\"OPENID123\"}";
        try (MockWechatServer server = MockWechatServer.start("text/plain", payload)) {
            HttpWechatCodeExchangeClient client = new HttpWechatCodeExchangeClient(serverProperties(server.baseUrl()));

            WechatSession session = client.exchange("code-value");

            assertEquals("OPENID123", session.openid());
            assertEquals("UNION123", session.unionid());
        }
    }

    @Test
    void textPlainErrcodeResponseYieldsUnauthorized() throws Exception {
        String payload = "{\"errcode\":40029,\"errmsg\":\"invalid code\"}";
        try (MockWechatServer server = MockWechatServer.start("text/plain", payload)) {
            HttpWechatCodeExchangeClient client = new HttpWechatCodeExchangeClient(serverProperties(server.baseUrl()));

            LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
                () -> client.exchange("code-value"));

            assertEquals(401, exception.getCode());
        }
    }

    @Test
    void jsonSuccessResponseStillSupported() throws Exception {
        String payload = "{\"openid\":\"OPENID456\",\"unionid\":\"UNION456\"}";
        try (MockWechatServer server = MockWechatServer.start("application/json", payload)) {
            HttpWechatCodeExchangeClient client = new HttpWechatCodeExchangeClient(serverProperties(server.baseUrl()));

            assertEquals("OPENID456", client.exchange("code-value").openid());
        }
    }

    private static AdminAuthProperties serverProperties(String baseUrl) {
        AdminAuthProperties properties = new AdminAuthProperties();
        properties.setWechatAppId("app-id");
        properties.setWechatAppSecret("secret-value");
        properties.setWechatCode2SessionUrl(baseUrl);
        return properties;
    }

    /**
     * 微信 jscode2session 实际返回 Content-Type: text/plain，这里用 JDK 自带 HttpServer 复刻真实响应。
     */
    private static final class MockWechatServer implements AutoCloseable {
        private final HttpServer server;

        private MockWechatServer(HttpServer server) {
            this.server = server;
        }

        static MockWechatServer start(String contentType, String body) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            server.createContext("/sns/jscode2session", exchange -> {
                exchange.getResponseHeaders().add("Content-Type", contentType);
                exchange.sendResponseHeaders(200, bytes.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(bytes);
                }
            });
            server.start();
            return new MockWechatServer(server);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/sns/jscode2session";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }
}
