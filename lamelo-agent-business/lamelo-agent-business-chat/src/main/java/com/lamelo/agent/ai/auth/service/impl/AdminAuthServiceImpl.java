package com.lamelo.agent.ai.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import jakarta.servlet.http.HttpServletRequest;
import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.service.AdminAuthService;
import com.lamelo.agent.ai.auth.support.AdminJwtTokenService;
import com.lamelo.agent.ai.auth.support.AdminRequestContext;
import com.lamelo.agent.ai.auth.vo.AdminLoginVo;
import com.lamelo.agent.ai.auth.vo.AdminProfileVo;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 后台登录认证实现。
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final AdminAuthProperties adminAuthProperties;

    private final AdminJwtTokenService adminJwtTokenService;

    public AdminAuthServiceImpl(AdminAuthProperties adminAuthProperties,
                                AdminJwtTokenService adminJwtTokenService) {
        this.adminAuthProperties = adminAuthProperties;
        this.adminJwtTokenService = adminJwtTokenService;
    }

    @Override
    public AdminLoginVo login(AdminLoginRequest request) {
        String username = StrUtil.trim(request.getUsername());
        String password = StrUtil.trim(request.getPassword());
        if (!StrUtil.equals(username, adminAuthProperties.getUsername()) || !passwordMatches(password)) {
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

    private boolean passwordMatches(String rawPassword) {
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
}
