package com.lamelo.agent.ai.auth;

import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.data.PlatformAccount;
import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.mapper.PlatformAccountMapper;
import com.lamelo.agent.ai.auth.service.impl.AdminAuthServiceImpl;
import com.lamelo.agent.ai.auth.support.AdminJwtTokenService;
import com.lamelo.agent.ai.auth.vo.AdminLoginVo;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class AdminAuthServiceCompatibilityTest {
    @Test
    void seededAdminHashAcceptsDefaultPasswordThroughLegacyLogin() {
        AdminAuthProperties properties = new AdminAuthProperties();
        properties.setUsername("admin");
        properties.setPassword("admin123456");
        PlatformAccount account = new PlatformAccount();
        account.setId(1L);
        account.setUsername("admin");
        account.setPasswordHash("$2a$10$J4R3O070wmA4LFufwJ.nuugjqxkW0vDt79IEr88TRjxWlrk3voLIi");
        account.setEnabled(true);
        PlatformAccountMapper accountMapper = mock(PlatformAccountMapper.class);
        when(accountMapper.selectActiveByUsername("admin")).thenReturn(account);
        AdminJwtTokenService tokenService = mock(AdminJwtTokenService.class);
        when(tokenService.generateToken("admin")).thenReturn("jwt");

        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123456");
        AdminLoginVo result = new AdminAuthServiceImpl(properties, tokenService, accountMapper).login(request);

        assertEquals("admin", result.getUsername());
        assertEquals("jwt", result.getToken());
    }

    @Test
    void configuredCustomAccountReconcilesPasswordHashBeforeLogin() {
        AdminAuthProperties properties = new AdminAuthProperties();
        properties.setUsername("operator");
        properties.setPassword("new-password");
        PlatformAccount account = new PlatformAccount();
        account.setId(2L);
        account.setUsername("operator");
        account.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder()
            .encode("old-password"));
        account.setEnabled(true);
        PlatformAccountMapper accountMapper = mock(PlatformAccountMapper.class);
        when(accountMapper.selectActiveByUsername("operator")).thenReturn(account);
        AdminJwtTokenService tokenService = mock(AdminJwtTokenService.class);
        when(tokenService.generateToken("operator")).thenReturn("jwt");

        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("operator");
        request.setPassword("new-password");
        AdminLoginVo result = new AdminAuthServiceImpl(properties, tokenService, accountMapper).login(request);

        assertEquals("jwt", result.getToken());
        verify(accountMapper).updatePasswordHashById(org.mockito.ArgumentMatchers.eq(2L),
            org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void configuredCredentialsReactivateTheAccountDisabledByLegacySeedMigration() {
        AdminAuthProperties properties = new AdminAuthProperties();
        properties.setUsername("admin");
        properties.setPassword("admin123");
        PlatformAccount account = new PlatformAccount();
        account.setId(3L);
        account.setUsername("admin");
        account.setPasswordHash(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode("old-password"));
        account.setEnabled(false);
        PlatformAccountMapper accountMapper = mock(PlatformAccountMapper.class);
        when(accountMapper.selectActiveByUsername("admin")).thenReturn(account);
        AdminJwtTokenService tokenService = mock(AdminJwtTokenService.class);
        when(tokenService.generateToken("admin")).thenReturn("jwt");
        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123");
        new AdminAuthServiceImpl(properties, tokenService, accountMapper).login(request);
        verify(accountMapper).updateEnabledById(3L, true);
    }
}
