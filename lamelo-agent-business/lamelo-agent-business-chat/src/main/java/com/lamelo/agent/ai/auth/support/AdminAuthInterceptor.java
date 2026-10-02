package com.lamelo.agent.ai.auth.support;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.data.PlatformAccount;
import com.lamelo.agent.ai.auth.mapper.PlatformAccountMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import com.lamelo.agent.common.ApiResponse;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 后台管理接口鉴权拦截器。
 */
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    private final AdminJwtTokenService adminJwtTokenService;

    private final ObjectMapper objectMapper;
    private final PlatformAccountMapper accountMapper;
    private final AdminAuthProperties properties;

    public AdminAuthInterceptor(AdminJwtTokenService adminJwtTokenService,
                                ObjectMapper objectMapper,
                                PlatformAccountMapper accountMapper,
                                AdminAuthProperties properties) {
        this.adminJwtTokenService = adminJwtTokenService;
        this.objectMapper = objectMapper;
        this.accountMapper = accountMapper;
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        String token = resolveToken(authorization);
        if (StrUtil.isBlank(token)) {
            writeUnauthorized(response, "请先登录后台管理台");
            return false;
        }

        try {
            Claims claims = adminJwtTokenService.parseToken(token);
            String username = claims.getSubject();
            if (StrUtil.isBlank(username)) {
                writeUnauthorized(response, "后台登录无效，请重新登录");
                return false;
            }
            String requestPath = request.getRequestURI();
            String contextPath = request.getContextPath();
            if (StrUtil.isNotBlank(contextPath) && requestPath.startsWith(contextPath)) {
                requestPath = requestPath.substring(contextPath.length());
            }
            if (requestPath.startsWith("/manage/")) {
                PlatformAccount account = accountMapper.selectActiveByUsername(username);
                if (account == null || !Boolean.TRUE.equals(account.getEnabled())) {
                    writeUnauthorized(response, "账号已停用，请重新登录");
                    return false;
                }
                List<String> roles = accountMapper.selectRoleCodesByAccountId(account.getId());
                if (roles == null || !roles.contains(properties.getAdminRoleCode())) {
                    writeForbidden(response, "当前账号没有管理员权限");
                    return false;
                }
            }
            AdminRequestContext.storeUsername(request, username);
            return true;
        } catch (LaMeloAgentFrameException exception) {
            writeUnauthorized(response, exception.getMessage());
            return false;
        }
    }

    private String resolveToken(String authorization) {
        if (StrUtil.isBlank(authorization)) {
            return null;
        }
        if (StrUtil.startWithIgnoreCase(authorization, "Bearer ")) {
            return StrUtil.trim(authorization.substring(7));
        }
        return StrUtil.trim(authorization);
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws Exception {
        writeError(response, HttpServletResponse.SC_UNAUTHORIZED, message);
    }

    private void writeForbidden(HttpServletResponse response, String message) throws Exception {
        writeError(response, HttpServletResponse.SC_FORBIDDEN, message);
    }

    private void writeError(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(status, message)));
    }
}
