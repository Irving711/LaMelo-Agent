package com.lamelo.agent.ai.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.data.PlatformAccount;
import com.lamelo.agent.ai.auth.mapper.PlatformAccountMapper;
import com.lamelo.agent.ai.auth.support.AdminAuthInterceptor;
import com.lamelo.agent.ai.auth.support.AdminJwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.List;

class AdminManagementAuthorizationTest {
    @Test
    void ordinaryAccountTokenCannotAccessManagementEndpoint() throws Exception {
        AdminAuthProperties properties = new AdminAuthProperties();
        AdminJwtTokenService tokens = new AdminJwtTokenService(properties);
        PlatformAccountMapper accounts = mock(PlatformAccountMapper.class);
        PlatformAccount ordinary = new PlatformAccount();
        ordinary.setId(2L);
        ordinary.setUsername("ordinary-user");
        ordinary.setEnabled(true);
        when(accounts.selectActiveByUsername("ordinary-user")).thenReturn(ordinary);
        when(accounts.selectRoleCodesByAccountId(2L)).thenReturn(List.of("USER"));
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(tokens, new ObjectMapper(), accounts, properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/manage/document/page/query");
        request.addHeader("Authorization", "Bearer " + tokens.generateToken("ordinary-user"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertEquals(403, response.getStatus());
    }

    @Test
    void administratorTokenCanAccessManagementEndpoint() throws Exception {
        AdminAuthProperties properties = new AdminAuthProperties();
        AdminJwtTokenService tokens = new AdminJwtTokenService(properties);
        PlatformAccountMapper accounts = mock(PlatformAccountMapper.class);
        PlatformAccount admin = new PlatformAccount();
        admin.setId(1L);
        admin.setUsername("admin");
        admin.setEnabled(true);
        when(accounts.selectActiveByUsername("admin")).thenReturn(admin);
        when(accounts.selectRoleCodesByAccountId(1L)).thenReturn(List.of("ADMIN"));
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(tokens, new ObjectMapper(), accounts, properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/manage/document/page/query");
        request.addHeader("Authorization", "Bearer " + tokens.generateToken("admin"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertEquals(true, interceptor.preHandle(request, response, new Object()));
        assertEquals(200, response.getStatus());
    }

    @Test
    void contextPathDoesNotBypassAdministratorRoleCheck() throws Exception {
        AdminAuthProperties properties = new AdminAuthProperties();
        AdminJwtTokenService tokens = new AdminJwtTokenService(properties);
        PlatformAccountMapper accounts = mock(PlatformAccountMapper.class);
        PlatformAccount ordinary = new PlatformAccount();
        ordinary.setId(3L);
        ordinary.setUsername("ordinary-user");
        ordinary.setEnabled(true);
        when(accounts.selectActiveByUsername("ordinary-user")).thenReturn(ordinary);
        when(accounts.selectRoleCodesByAccountId(3L)).thenReturn(List.of("USER"));
        AdminAuthInterceptor interceptor = new AdminAuthInterceptor(tokens, new ObjectMapper(), accounts, properties);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/app/manage/document/page/query");
        request.setContextPath("/app");
        request.addHeader("Authorization", "Bearer " + tokens.generateToken("ordinary-user"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        interceptor.preHandle(request, response, new Object());

        assertEquals(403, response.getStatus());
    }
}
