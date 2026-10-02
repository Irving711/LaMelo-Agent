package com.lamelo.agent.ai.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.data.PlatformAccount;
import com.lamelo.agent.ai.auth.data.WechatIdentity;
import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.dto.BindWechatRequest;
import com.lamelo.agent.ai.auth.dto.WechatCodeLoginRequest;
import com.lamelo.agent.ai.auth.mapper.PlatformAccountMapper;
import com.lamelo.agent.ai.auth.mapper.WechatIdentityMapper;
import com.lamelo.agent.ai.auth.service.MiniProgramAuthService;
import com.lamelo.agent.ai.auth.support.AdminJwtTokenService;
import com.lamelo.agent.ai.auth.support.AdminRequestContext;
import com.lamelo.agent.ai.auth.support.WechatCodeExchangeClient;
import com.lamelo.agent.ai.auth.support.WechatSession;
import com.lamelo.agent.ai.auth.vo.MiniProgramLoginVo;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
public class MiniProgramAuthServiceImpl implements MiniProgramAuthService {
    private static final BCryptPasswordEncoder PASSWORD_ENCODER = new BCryptPasswordEncoder();

    private final AdminAuthProperties properties;
    private final AdminJwtTokenService tokenService;
    private final PlatformAccountMapper accountMapper;
    private final WechatIdentityMapper identityMapper;
    private final WechatCodeExchangeClient exchangeClient;

    @Autowired
    public MiniProgramAuthServiceImpl(AdminAuthProperties properties,
                                      AdminJwtTokenService tokenService,
                                      PlatformAccountMapper accountMapper,
                                      WechatIdentityMapper identityMapper,
                                      WechatCodeExchangeClient exchangeClient) {
        this.properties = properties;
        this.tokenService = tokenService;
        this.accountMapper = accountMapper;
        this.identityMapper = identityMapper;
        this.exchangeClient = exchangeClient;
    }

    @Override
    public MiniProgramLoginVo wechatLogin(WechatCodeLoginRequest request) {
        String code = require(request == null ? null : request.getCode(), "微信登录码不能为空");
        WechatSession session = exchangeClient.exchange(code);
        String openid = require(session == null ? null : session.openid(), "微信登录码无效");
        WechatIdentity identity = identityMapper.selectActiveByOpenid(openid);
        if (identity == null && StrUtil.isNotBlank(session.unionid())) {
            identity = identityMapper.selectActiveByUnionid(session.unionid());
        }
        if (identity == null) {
            return new MiniProgramLoginVo(null, null, Collections.emptyList(), true,
                properties.getTokenExpireMinutes());
        }
        PlatformAccount account = accountMapper.selectActiveById(identity.getUserId());
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())) {
            throw new LaMeloAgentFrameException(401, "账号已停用");
        }
        return tokenVo(account);
    }

    @Override
    public MiniProgramLoginVo passwordLogin(AdminLoginRequest request) {
        String username = require(request == null ? null : request.getUsername(), "账号不能为空");
        String password = require(request == null ? null : request.getPassword(), "密码不能为空");
        return tokenVo(authenticate(username, password));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bind(BindWechatRequest request, HttpServletRequest currentRequest) {
        String currentUsername = AdminRequestContext.resolveUsername(currentRequest);
        if (StrUtil.isBlank(currentUsername)) {
            throw new LaMeloAgentFrameException(401, "请先登录");
        }
        String username = require(request == null ? null : request.getUsername(), "账号不能为空");
        if (!StrUtil.equals(currentUsername, username)) {
            throw new LaMeloAgentFrameException(403, "当前登录账号与绑定账号不一致");
        }
        PlatformAccount account = authenticate(username,
            require(request.getPassword(), "密码不能为空"));
        WechatSession session = exchangeClient.exchange(require(request.getCode(), "微信登录码不能为空"));
        String openid = require(session == null ? null : session.openid(), "微信登录码无效");
        WechatIdentity duplicate = identityMapper.selectActiveByOpenid(openid);
        if (duplicate == null && StrUtil.isNotBlank(session.unionid())) {
            duplicate = identityMapper.selectActiveByUnionid(session.unionid());
        }
        if (duplicate != null) {
            throw new LaMeloAgentFrameException(409, "该微信已绑定其他账号");
        }
        WechatIdentity identity = new WechatIdentity();
        identity.setOpenid(openid);
        identity.setUnionid(session.unionid());
        identity.setUserId(account.getId());
        try {
            identityMapper.insertIdentity(identity);
        } catch (RuntimeException exception) {
            if (isDuplicateKey(exception)) {
                throw new LaMeloAgentFrameException(409, "该微信已绑定其他账号");
            }
            throw exception;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unbind(HttpServletRequest currentRequest, String password) {
        String username = AdminRequestContext.resolveUsername(currentRequest);
        if (StrUtil.isBlank(username)) {
            throw new LaMeloAgentFrameException(401, "请先登录");
        }
        PlatformAccount account = authenticate(username, require(password, "密码不能为空"));
        if (identityMapper.logicalDeleteByUserId(account.getId()) == 0) {
            throw new LaMeloAgentFrameException(404, "当前账号未绑定微信");
        }
    }

    private MiniProgramLoginVo tokenVo(PlatformAccount account) {
        List<String> roles = accountMapper.selectRoleCodesByAccountId(account.getId());
        if (roles == null) {
            roles = Collections.emptyList();
        }
        return new MiniProgramLoginVo(tokenService.generateToken(account.getUsername()), account.getUsername(),
            roles, identityMapper.selectActiveByUserId(account.getId()) == null, properties.getTokenExpireMinutes());
    }

    private PlatformAccount authenticate(String username, String password) {
        PlatformAccount account = accountMapper.selectActiveByUsername(StrUtil.trim(username));
        if (account == null && StrUtil.equals(StrUtil.trim(username), properties.getUsername())) {
            account = createConfiguredAccount();
        } else if (account != null && StrUtil.equals(StrUtil.trim(username), properties.getUsername())) {
            reconcileConfiguredPassword(account);
        }
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())
            || !passwordMatches(password, account.getPasswordHash())) {
            throw new LaMeloAgentFrameException(401, "账号或密码不正确");
        }
        return account;
    }

    private void reconcileConfiguredPassword(PlatformAccount account) {
        if (!Boolean.TRUE.equals(account.getEnabled())) {
            accountMapper.updateEnabledById(account.getId(), true);
            account.setEnabled(true);
        }
        if (StrUtil.isNotBlank(properties.getPasswordHash())) {
            if (!StrUtil.equals(account.getPasswordHash(), properties.getPasswordHash())) {
                account.setPasswordHash(properties.getPasswordHash());
                accountMapper.updatePasswordHashById(account.getId(), account.getPasswordHash());
            }
        } else if (!PASSWORD_ENCODER.matches(properties.getPassword(), account.getPasswordHash())) {
            account.setPasswordHash(PASSWORD_ENCODER.encode(properties.getPassword()));
            accountMapper.updatePasswordHashById(account.getId(), account.getPasswordHash());
        }
    }

    private PlatformAccount createConfiguredAccount() {
        PlatformAccount account = new PlatformAccount();
        account.setUsername(properties.getUsername());
        account.setPasswordHash(StrUtil.isNotBlank(properties.getPasswordHash())
            ? properties.getPasswordHash() : PASSWORD_ENCODER.encode(properties.getPassword()));
        account.setEnabled(true);
        try {
            accountMapper.insertAccount(account);
        } catch (RuntimeException ignored) {
            // Idempotent migration: a concurrent request may have inserted the account.
        }
        PlatformAccount persisted = accountMapper.selectActiveByUsername(properties.getUsername());
        if (persisted != null) {
            Long roleId = accountMapper.selectRoleIdByCode(properties.getAdminRoleCode());
            if (roleId == null) {
                try {
                    accountMapper.insertRole(properties.getAdminRoleCode(), "管理员");
                } catch (RuntimeException ignored) {
                    // Another request may have inserted the role.
                }
                roleId = accountMapper.selectRoleIdByCode(properties.getAdminRoleCode());
            }
            if (roleId != null) {
                accountMapper.insertAccountRole(persisted.getId(), roleId);
            }
        }
        return persisted;
    }

    private boolean passwordMatches(String password, String hash) {
        if (StrUtil.isBlank(hash)) {
            return StrUtil.equals(password, properties.getPassword());
        }
        try {
            return PASSWORD_ENCODER.matches(password, hash);
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private String require(String value, String message) {
        if (StrUtil.isBlank(value)) {
            throw new LaMeloAgentFrameException(400, message);
        }
        return StrUtil.trim(value);
    }

    private boolean isDuplicateKey(Throwable exception) {
        Throwable current = exception;
        while (current != null) {
            if (current instanceof DuplicateKeyException) {
                return true;
            }
            String message = current.getMessage();
            if (message != null) {
                String normalized = message.toLowerCase(java.util.Locale.ROOT);
                if (normalized.contains("duplicate entry") || normalized.contains("duplicate key")
                    || normalized.contains("unique constraint")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }
}
