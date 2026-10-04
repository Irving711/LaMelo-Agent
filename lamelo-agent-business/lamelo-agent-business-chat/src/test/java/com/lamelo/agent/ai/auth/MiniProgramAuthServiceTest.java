package com.lamelo.agent.ai.auth;

import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.data.PlatformAccount;
import com.lamelo.agent.ai.auth.data.WechatIdentity;
import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.dto.BindWechatRequest;
import com.lamelo.agent.ai.auth.dto.SetCredentialsRequest;
import com.lamelo.agent.ai.auth.dto.WechatCodeLoginRequest;
import com.lamelo.agent.ai.auth.mapper.PlatformAccountMapper;
import com.lamelo.agent.ai.auth.mapper.WechatIdentityMapper;
import com.lamelo.agent.ai.auth.service.impl.MiniProgramAuthServiceImpl;
import com.lamelo.agent.ai.auth.support.AdminJwtTokenService;
import com.lamelo.agent.ai.auth.support.AdminRequestContext;
import com.lamelo.agent.ai.auth.support.WechatCodeExchangeClient;
import com.lamelo.agent.ai.auth.support.WechatSession;
import com.lamelo.agent.ai.auth.support.SmsCodeService;
import com.lamelo.agent.ai.auth.vo.MiniProgramLoginVo;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MiniProgramAuthServiceTest {

    private PlatformAccountMapper accountMapper;
    private WechatIdentityMapper identityMapper;
    private WechatCodeExchangeClient exchangeClient;
    private SmsCodeService smsCodeService;
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
        smsCodeService = mock(SmsCodeService.class);
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
            accountMapper, identityMapper, exchangeClient, smsCodeService);
    }

    @Test
    void firstWechatLoginAutoProvisionsAccountAndReturnsToken() {
        when(exchangeClient.exchange("code-1")).thenReturn(new WechatSession("openid-1", null));
        when(identityMapper.selectActiveByOpenid("openid-1")).thenReturn(null);
        doAnswer(invocation -> {
            ((PlatformAccount) invocation.getArgument(0)).setId(42L);
            return 1;
        }).when(accountMapper).insertAccount(org.mockito.ArgumentMatchers.any(PlatformAccount.class));
        PlatformAccount provisioned = new PlatformAccount();
        provisioned.setId(42L);
        provisioned.setUsername("wx_openid-1");
        provisioned.setPasswordHash("!");
        provisioned.setEnabled(true);
        when(accountMapper.selectActiveByUsername("wx_openid-1")).thenReturn(provisioned);
        when(accountMapper.selectRoleCodesByAccountId(42L)).thenReturn(List.of());
        when(tokenService.generateToken("wx_openid-1")).thenReturn("wx-token");

        MiniProgramLoginVo result = service.wechatLogin(new WechatCodeLoginRequest("code-1"));

        assertEquals("wx-token", result.getToken());
        assertEquals("wx_openid-1", result.getUsername());
        assertTrue(result.getRoles().isEmpty());
        assertTrue(result.isNeedsBinding());

        ArgumentCaptor<WechatIdentity> captor = ArgumentCaptor.forClass(WechatIdentity.class);
        verify(identityMapper, times(1)).insertIdentity(captor.capture());
        assertEquals(42L, captor.getValue().getUserId());
        assertEquals("openid-1", captor.getValue().getOpenid());
    }

    @Test
    void existingBindingReturnsTokenWithoutNeedsBindingWhenPasswordUsable() {
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
    void existingBindingWithoutUsablePasswordKeepsNeedsBindingTrue() {
        WechatIdentity identity = new WechatIdentity();
        identity.setUserId(7L);
        account.setPasswordHash("!");
        when(accountMapper.selectActiveById(7L)).thenReturn(account);
        when(exchangeClient.exchange("code-2")).thenReturn(new WechatSession("openid-2", null));
        when(identityMapper.selectActiveByOpenid("openid-2")).thenReturn(identity);
        when(identityMapper.selectActiveByUserId(7L)).thenReturn(identity);
        when(tokenService.generateToken("admin")).thenReturn("jwt-token");

        MiniProgramLoginVo result = service.wechatLogin(new WechatCodeLoginRequest("code-2"));

        assertTrue(result.isNeedsBinding());
        assertEquals("jwt-token", result.getToken());
    }

    @Test
    void disabledAccountIsRejected() {
        WechatIdentity identity = new WechatIdentity();
        identity.setUserId(7L);
        account.setEnabled(false);
        when(accountMapper.selectActiveById(7L)).thenReturn(account);
        when(exchangeClient.exchange("code-disabled")).thenReturn(new WechatSession("openid-disabled", null));
        when(identityMapper.selectActiveByOpenid("openid-disabled")).thenReturn(identity);

        LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
            () -> service.wechatLogin(new WechatCodeLoginRequest("code-disabled")));

        assertTrue(exception.getMessage().contains("账号已停用"));
    }

    @Test
    void invalidWechatCodeIsRejected() {
        when(exchangeClient.exchange("bad-code")).thenThrow(new LaMeloAgentFrameException(400, "微信登录码无效"));

        assertThrows(LaMeloAgentFrameException.class,
            () -> service.wechatLogin(new WechatCodeLoginRequest("bad-code")));
    }

    @Test
    void bindRepointsCurrentWechatIdentityAndReturnsTargetToken() {
        MockHttpServletRequest request = authenticatedRequest();
        WechatIdentity identity = new WechatIdentity();
        identity.setId(11L);
        identity.setOpenid("openid-x");
        identity.setUserId(7L);
        when(identityMapper.selectActiveByUserId(7L)).thenReturn(identity);
        PlatformAccount target = new PlatformAccount();
        target.setId(8L);
        target.setUsername("boss");
        target.setPasswordHash(new BCryptPasswordEncoder().encode("boss-pass"));
        target.setEnabled(true);
        when(accountMapper.selectActiveByUsername("boss")).thenReturn(target);
        when(identityMapper.selectActiveByUserId(8L)).thenReturn(null);
        when(accountMapper.selectRoleCodesByAccountId(8L)).thenReturn(List.of());
        when(tokenService.generateToken("boss")).thenReturn("boss-token");

        MiniProgramLoginVo result = service.bind(new BindWechatRequest("boss", "boss-pass"), request);

        assertEquals("boss-token", result.getToken());
        assertEquals("boss", result.getUsername());
        verify(identityMapper, times(1)).updateUserIdById(11L, 8L);
    }

    @Test
    void bindRejectsWhenCurrentSessionHasNoWechatIdentity() {
        MockHttpServletRequest request = authenticatedRequest();
        when(identityMapper.selectActiveByUserId(7L)).thenReturn(null);

        LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
            () -> service.bind(new BindWechatRequest("boss", "boss-pass"), request));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("未绑定微信"));
    }

    @Test
    void bindRejectsTargetAlreadyBoundToOtherWechat() {
        MockHttpServletRequest request = authenticatedRequest();
        WechatIdentity identity = new WechatIdentity();
        identity.setId(11L);
        identity.setOpenid("openid-x");
        identity.setUserId(7L);
        when(identityMapper.selectActiveByUserId(7L)).thenReturn(identity);
        PlatformAccount target = new PlatformAccount();
        target.setId(8L);
        target.setUsername("boss");
        target.setPasswordHash(new BCryptPasswordEncoder().encode("boss-pass"));
        target.setEnabled(true);
        when(accountMapper.selectActiveByUsername("boss")).thenReturn(target);
        WechatIdentity other = new WechatIdentity();
        other.setId(12L);
        other.setUserId(8L);
        when(identityMapper.selectActiveByUserId(8L)).thenReturn(other);

        LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
            () -> service.bind(new BindWechatRequest("boss", "boss-pass"), request));

        assertEquals(409, exception.getCode());
        assertTrue(exception.getMessage().contains("已绑定其他微信"));
    }

    @Test
    void bindRejectsSameAccount() {
        MockHttpServletRequest request = authenticatedRequest();
        WechatIdentity identity = new WechatIdentity();
        identity.setId(11L);
        identity.setOpenid("openid-x");
        identity.setUserId(7L);
        when(identityMapper.selectActiveByUserId(7L)).thenReturn(identity);

        LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
            () -> service.bind(new BindWechatRequest("admin", "admin123456"), request));

        assertEquals(400, exception.getCode());
        assertTrue(exception.getMessage().contains("已绑定该账号"));
    }

    @Test
    void bindRejectsWrongPassword() {
        MockHttpServletRequest request = authenticatedRequest();
        WechatIdentity identity = new WechatIdentity();
        identity.setId(11L);
        identity.setOpenid("openid-x");
        identity.setUserId(7L);
        when(identityMapper.selectActiveByUserId(7L)).thenReturn(identity);
        PlatformAccount target = new PlatformAccount();
        target.setId(8L);
        target.setUsername("boss");
        target.setPasswordHash(new BCryptPasswordEncoder().encode("boss-pass"));
        target.setEnabled(true);
        when(accountMapper.selectActiveByUsername("boss")).thenReturn(target);

        LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
            () -> service.bind(new BindWechatRequest("boss", "wrong"), request));

        assertEquals(401, exception.getCode());
        assertTrue(exception.getMessage().contains("账号或密码不正确"));
    }

    @Test
    void setCredentialsRejectsWhenPasswordAlreadyUsable() {
        MockHttpServletRequest request = authenticatedRequest();

        LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
            () -> service.setCredentials(new SetCredentialsRequest("newuser", "new-pass"), request));

        assertTrue(exception.getMessage().contains("已设置密码"));
    }

    @Test
    void setCredentialsUpdatesUsernameAndPasswordAndReturnsToken() {
        MockHttpServletRequest request = authenticatedRequest();
        account.setPasswordHash("!");
        when(accountMapper.selectActiveByUsername("admin")).thenReturn(account);
        PlatformAccount updatedAccount = new PlatformAccount();
        updatedAccount.setId(7L);
        updatedAccount.setUsername("newuser");
        updatedAccount.setPasswordHash(new BCryptPasswordEncoder().encode("new-pass"));
        updatedAccount.setEnabled(true);
        doReturn(updatedAccount).when(accountMapper).selectActiveById(7L);
        when(accountMapper.selectActiveByUsername("newuser")).thenReturn(null);
        doReturn(List.of()).when(accountMapper).selectRoleCodesByAccountId(7L);
        when(tokenService.generateToken("newuser")).thenReturn("new-token");

        MiniProgramLoginVo result = service.setCredentials(new SetCredentialsRequest("newuser", "new-pass"), request);

        verify(accountMapper, times(1)).updateUsernameAndPasswordById(eq(7L), eq("newuser"),
            argThat(hash -> hash != null && hash.startsWith("$2")));
        assertEquals("new-token", result.getToken());
        assertEquals("newuser", result.getUsername());
        assertFalse(result.isNeedsBinding());
    }

    @Test
    void setCredentialsRejectsDuplicateUsername() {
        MockHttpServletRequest request = authenticatedRequest();
        account.setPasswordHash("!");
        when(accountMapper.selectActiveByUsername("admin")).thenReturn(account);
        PlatformAccount taken = new PlatformAccount();
        taken.setId(99L);
        taken.setUsername("taken");
        taken.setPasswordHash(new BCryptPasswordEncoder().encode("other-pass"));
        taken.setEnabled(true);
        when(accountMapper.selectActiveByUsername("taken")).thenReturn(taken);

        LaMeloAgentFrameException exception = assertThrows(LaMeloAgentFrameException.class,
            () -> service.setCredentials(new SetCredentialsRequest("taken", "new-pass"), request));

        assertEquals(409, exception.getCode());
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
        assertFalse(result.isNeedsBinding());
    }

    private MockHttpServletRequest authenticatedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        AdminRequestContext.storeUsername(request, "admin");
        return request;
    }

}
