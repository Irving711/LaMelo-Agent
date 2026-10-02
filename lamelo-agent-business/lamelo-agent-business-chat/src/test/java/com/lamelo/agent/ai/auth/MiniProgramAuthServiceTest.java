package com.lamelo.agent.ai.auth;

import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.dto.BindWechatRequest;
import com.lamelo.agent.ai.auth.dto.WechatCodeLoginRequest;
import com.lamelo.agent.ai.auth.mapper.PlatformAccountMapper;
import com.lamelo.agent.ai.auth.mapper.WechatIdentityMapper;
import com.lamelo.agent.ai.auth.service.impl.MiniProgramAuthServiceImpl;
import com.lamelo.agent.ai.auth.support.AdminRequestContext;
import com.lamelo.agent.ai.auth.support.AdminJwtTokenService;
import com.lamelo.agent.ai.auth.support.WechatCodeExchangeClient;
import com.lamelo.agent.ai.auth.support.WechatSession;
import com.lamelo.agent.ai.auth.vo.MiniProgramLoginVo;
import com.lamelo.agent.ai.auth.data.PlatformAccount;
import com.lamelo.agent.ai.auth.data.WechatIdentity;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.dao.DuplicateKeyException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

class MiniProgramAuthServiceTest {

    private PlatformAccountMapper accountMapper;
    private WechatIdentityMapper identityMapper;
    private WechatCodeExchangeClient exchangeClient;
    private AdminJwtTokenService tokenService;
    private MiniProgramAuthServiceImpl service;
    private PlatformAccount account;

    @BeforeEach
    void setUp() {
        AdminAuthProperties properties = new AdminAuthProperties();
        properties.setUsername("admin");
        properties.setPassword("admin123456");
        accountMapper = mock(PlatformAccountMapper.class);
        identityMapper = mock(WechatIdentityMapper.class);
        exchangeClient = mock(WechatCodeExchangeClient.class);
        account = new PlatformAccount();
        account.setId(7L);
        account.setUsername("admin");
        account.setPasswordHash(new BCryptPasswordEncoder().encode("admin123456"));
        account.setEnabled(true);
        when(accountMapper.selectActiveByUsername("admin")).thenReturn(account);
        when(accountMapper.selectActiveById(7L)).thenReturn(account);
        when(accountMapper.selectRoleCodesByAccountId(7L)).thenReturn(List.of("ADMIN"));
        tokenService = mock(AdminJwtTokenService.class);
        service = new MiniProgramAuthServiceImpl(properties, tokenService,
            accountMapper, identityMapper, exchangeClient);
    }

    @Test
    void firstWechatLoginReturnsNeedsBindingWithoutCreatingIdentity() {
        when(exchangeClient.exchange("code-1")).thenReturn(new WechatSession("openid-1", null));
        when(identityMapper.selectActiveByOpenid("openid-1")).thenReturn(null);

        MiniProgramLoginVo result = service.wechatLogin(new WechatCodeLoginRequest("code-1"));

        assertTrue(result.isNeedsBinding());
        assertNull(result.getToken());
        assertTrue(result.getRoles().isEmpty());
    }

    @Test
    void existingBindingReturnsTokenUsernameAndRoles() {
        WechatIdentity identity = new WechatIdentity();
        identity.setUserId(7L);
        when(exchangeClient.exchange("code-2")).thenReturn(new WechatSession("openid-2", "union-2"));
        when(identityMapper.selectActiveByOpenid("openid-2")).thenReturn(identity);
        when(identityMapper.selectActiveByUserId(7L)).thenReturn(identity);
        when(tokenService.generateToken("admin")).thenReturn("jwt-token");

        MiniProgramLoginVo result = service.wechatLogin(new WechatCodeLoginRequest("code-2"));

        assertEquals("jwt-token", result.getToken());
        assertEquals("admin", result.getUsername());
        assertEquals(List.of("ADMIN"), result.getRoles());
        assertFalse(result.isNeedsBinding());
    }

    @Test
    void invalidWechatCodeIsRejected() {
        when(exchangeClient.exchange("bad-code")).thenThrow(new LaMeloAgentFrameException(400, "微信登录码无效"));

        assertThrows(LaMeloAgentFrameException.class,
            () -> service.wechatLogin(new WechatCodeLoginRequest("bad-code")));
    }

    @Test
    void bindRejectsDuplicateActiveIdentity() {
        MockHttpServletRequest request = authenticatedRequest();
        when(exchangeClient.exchange("code-3")).thenReturn(new WechatSession("openid-3", null));
        WechatIdentity duplicate = new WechatIdentity();
        duplicate.setUserId(99L);
        when(identityMapper.selectActiveByOpenid("openid-3")).thenReturn(duplicate);

        assertThrows(LaMeloAgentFrameException.class,
            () -> service.bind(new BindWechatRequest("admin", "admin123456", "code-3"), request));
    }

    @Test
    void bindDoesNotExposeDatabaseExceptionCause() {
        MockHttpServletRequest request = authenticatedRequest();
        when(exchangeClient.exchange("code-sensitive"))
            .thenReturn(new WechatSession("openid-sensitive", "union-sensitive"));
        when(identityMapper.selectActiveByOpenid("openid-sensitive")).thenReturn(null);
        doThrow(new DuplicateKeyException("duplicate openid-sensitive union-sensitive"))
            .when(identityMapper).insertIdentity(any(WechatIdentity.class));

        LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
            () -> service.bind(new BindWechatRequest("admin", "admin123456", "code-sensitive"), request));

        assertNull(exception.getCause());
        assertFalse(exception.getMessage().contains("openid-sensitive"));
        assertFalse(exception.getMessage().contains("union-sensitive"));
    }

    @Test
    void bindPropagatesDatabaseOutageInsteadOfMappingItToConflict() {
        MockHttpServletRequest request = authenticatedRequest();
        when(exchangeClient.exchange("code-outage"))
            .thenReturn(new WechatSession("openid-outage", null));
        when(identityMapper.selectActiveByOpenid("openid-outage")).thenReturn(null);
        RuntimeException outage = new RuntimeException("database unavailable");
        doThrow(outage).when(identityMapper).insertIdentity(any(WechatIdentity.class));

        RuntimeException result = assertThrows(RuntimeException.class,
            () -> service.bind(new BindWechatRequest("admin", "admin123456", "code-outage"), request));

        assertEquals(outage, result);
    }

    @Test
    void bindRejectsWrongPassword() {
        MockHttpServletRequest request = authenticatedRequest();
        when(exchangeClient.exchange("code-4")).thenReturn(new WechatSession("openid-4", null));

        assertThrows(LaMeloAgentFrameException.class,
            () -> service.bind(new BindWechatRequest("admin", "wrong", "code-4"), request));
    }

    @Test
    void unbindRequiresCurrentToken() {
        HttpServletRequest request = new MockHttpServletRequest();

        assertThrows(LaMeloAgentFrameException.class, () -> service.unbind(request, "admin123456"));
    }

    @Test
    void passwordLoginUsesMiniProgramTokenEnvelope() {
        when(tokenService.generateToken("admin")).thenReturn("password-token");

        AdminLoginRequest request = new AdminLoginRequest();
        request.setUsername("admin");
        request.setPassword("admin123456");
        MiniProgramLoginVo result = service.passwordLogin(request);

        assertEquals("password-token", result.getToken());
        assertEquals("admin", result.getUsername());
        assertEquals(List.of("ADMIN"), result.getRoles());
        assertTrue(result.isNeedsBinding());
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AdminRequestContext.storeUsername(request, "admin");
        return request;
    }

}
