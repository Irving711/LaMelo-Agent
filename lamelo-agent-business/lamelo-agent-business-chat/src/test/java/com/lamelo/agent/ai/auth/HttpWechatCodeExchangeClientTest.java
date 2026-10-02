package com.lamelo.agent.ai.auth;

import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.support.HttpWechatCodeExchangeClient;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
}
