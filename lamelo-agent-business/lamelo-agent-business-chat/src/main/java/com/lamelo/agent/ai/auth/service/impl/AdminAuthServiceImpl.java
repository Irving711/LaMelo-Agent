package com.lamelo.agent.ai.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.data.PlatformAccount;
import com.lamelo.agent.ai.auth.mapper.PlatformAccountMapper;
import com.lamelo.agent.ai.auth.service.AdminAuthService;
import com.lamelo.agent.ai.auth.support.AdminJwtTokenService;
import com.lamelo.agent.ai.auth.support.AdminRequestContext;
import com.lamelo.agent.ai.auth.vo.AdminLoginVo;
import com.lamelo.agent.ai.auth.vo.AdminProfileVo;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 后台登录认证实现。
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final AdminAuthProperties adminAuthProperties;

    private final AdminJwtTokenService adminJwtTokenService;

    private final PlatformAccountMapper platformAccountMapper;

    public AdminAuthServiceImpl(AdminAuthProperties adminAuthProperties,
                                AdminJwtTokenService adminJwtTokenService) {
        this(adminAuthProperties, adminJwtTokenService, null);
    }

    @Autowired
    public AdminAuthServiceImpl(AdminAuthProperties adminAuthProperties,
                                AdminJwtTokenService adminJwtTokenService,
                                PlatformAccountMapper platformAccountMapper) {
        this.adminAuthProperties = adminAuthProperties;
        this.adminJwtTokenService = adminJwtTokenService;
        this.platformAccountMapper = platformAccountMapper;
    }

    @Override
    public AdminLoginVo login(AdminLoginRequest request) {
        String username = StrUtil.trim(request.getUsername());
        String password = StrUtil.trim(request.getPassword());
        PlatformAccount account = findOrCreateConfiguredAccount(username);
        boolean valid = platformAccountMapper == null
            ? StrUtil.equals(username, adminAuthProperties.getUsername()) && passwordMatchesConfigured(password)
            : account != null && Boolean.TRUE.equals(account.getEnabled())
                && passwordMatches(password, account.getPasswordHash());
        if (!valid) {
            throw new LaMeloAgentFrameException(401, "账号或密码不正确");
        }

        String token = adminJwtTokenService.generateToken(username);
        return new AdminLoginVo(username, token, adminAuthProperties.getTokenExpireMinutes());
    }

    @Override
    public AdminProfileVo currentProfile(HttpServletRequest request) {
        String username = AdminRequestContext.resolveUsername(request);
        return new AdminProfileVo(username);
    }

    private PlatformAccount findOrCreateConfiguredAccount(String username) {
        if (platformAccountMapper == null) {
            return StrUtil.equals(username, adminAuthProperties.getUsername()) ? null : null;
        }
        PlatformAccount account = platformAccountMapper.selectActiveByUsername(username);
        if (account == null && StrUtil.equals(username, adminAuthProperties.getUsername())) {
            account = new PlatformAccount();
            account.setUsername(username);
            account.setPasswordHash(StrUtil.isNotBlank(adminAuthProperties.getPasswordHash())
                ? adminAuthProperties.getPasswordHash()
                : BCryptPasswordEncoderHolder.ENCODER.encode(adminAuthProperties.getPassword()));
            account.setEnabled(true);
            try {
                platformAccountMapper.insertAccount(account);
            } catch (RuntimeException ignored) {
                // A concurrent login may have inserted the deterministic account.
            }
            account = platformAccountMapper.selectActiveByUsername(username);
            ensureAdminRole(account);
        } else if (account != null && StrUtil.equals(username, adminAuthProperties.getUsername())) {
            reconcileConfiguredPassword(account);
        }
        return account;
    }

    private void reconcileConfiguredPassword(PlatformAccount account) {
        if (!Boolean.TRUE.equals(account.getEnabled())) {
            platformAccountMapper.updateEnabledById(account.getId(), true);
            account.setEnabled(true);
        }
        String configuredPassword = adminAuthProperties.getPassword();
        if (StrUtil.isNotBlank(adminAuthProperties.getPasswordHash())) {
            if (!StrUtil.equals(account.getPasswordHash(), adminAuthProperties.getPasswordHash())) {
                account.setPasswordHash(adminAuthProperties.getPasswordHash());
                platformAccountMapper.updatePasswordHashById(account.getId(), account.getPasswordHash());
            }
        } else if (!PASSWORD_ENCODER.matches(configuredPassword, account.getPasswordHash())) {
            account.setPasswordHash(BCryptPasswordEncoderHolder.ENCODER.encode(configuredPassword));
            platformAccountMapper.updatePasswordHashById(account.getId(), account.getPasswordHash());
        }
    }

    private void ensureAdminRole(PlatformAccount account) {
        if (account == null || account.getId() == null || platformAccountMapper == null) {
            return;
        }
        Long roleId = platformAccountMapper.selectRoleIdByCode(adminAuthProperties.getAdminRoleCode());
        if (roleId == null) {
            try {
                platformAccountMapper.insertRole(adminAuthProperties.getAdminRoleCode(), "管理员");
            } catch (RuntimeException ignored) {
                // Another request may have created the role.
            }
            roleId = platformAccountMapper.selectRoleIdByCode(adminAuthProperties.getAdminRoleCode());
        }
        if (roleId != null) {
            platformAccountMapper.insertAccountRole(account.getId(), roleId);
        }
    }

    private boolean passwordMatches(String rawPassword, String persistedHash) {
        if (StrUtil.isNotBlank(persistedHash)) {
            try {
                return PASSWORD_ENCODER.matches(rawPassword, persistedHash);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return passwordMatchesConfigured(rawPassword);
    }

    private boolean passwordMatchesConfigured(String rawPassword) {
        String passwordHash = adminAuthProperties.getPasswordHash();
        if (StrUtil.isNotBlank(passwordHash)) {
            try {
                return PASSWORD_ENCODER.matches(rawPassword, passwordHash);
            } catch (IllegalArgumentException exception) {
                return false;
            }
        }
        return StrUtil.equals(rawPassword, adminAuthProperties.getPassword());
    }

    private static final class BCryptPasswordEncoderHolder {
        private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();
    }
}
