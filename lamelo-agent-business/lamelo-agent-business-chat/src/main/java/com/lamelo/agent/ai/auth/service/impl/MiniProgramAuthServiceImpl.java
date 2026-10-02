package com.lamelo.agent.ai.auth.service.impl;

import cn.hutool.core.util.StrUtil;
import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.data.PlatformAccount;
import com.lamelo.agent.ai.auth.data.WechatIdentity;
import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.dto.BindWechatRequest;
import com.lamelo.agent.ai.auth.dto.SetCredentialsRequest;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    /** 无可用密码的哨兵哈希：BCrypt 永远无法匹配，用于表示"账号未设置密码"。 */
    private static final String UNUSABLE_PASSWORD_HASH = "!";
    private static final Logger log = LoggerFactory.getLogger(MiniProgramAuthServiceImpl.class);

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
    @Transactional(rollbackFor = Exception.class)
    public MiniProgramLoginVo wechatLogin(WechatCodeLoginRequest request) {
        String code = require(request == null ? null : request.getCode(), "微信登录码不能为空");
        WechatSession session = exchangeClient.exchange(code);
        String openid = require(session == null ? null : session.openid(), "微信登录码无效");
        WechatIdentity identity = identityMapper.selectActiveByOpenid(openid);
        if (identity == null && StrUtil.isNotBlank(session.unionid())) {
            identity = identityMapper.selectActiveByUnionid(session.unionid());
        }
        PlatformAccount account;
        if (identity == null) {
            // 首次登录：自动建号，直接发放 token，不再要求用户填写账号密码
            account = autoProvision(session);
        } else {
            account = accountMapper.selectActiveById(identity.getUserId());
            if (account == null || !Boolean.TRUE.equals(account.getEnabled())) {
                throw new LaMeloAgentFrameException(401, "账号已停用");
            }
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
    public MiniProgramLoginVo bind(BindWechatRequest request, HttpServletRequest currentRequest) {
        String currentUsername = AdminRequestContext.resolveUsername(currentRequest);
        if (StrUtil.isBlank(currentUsername)) {
            throw new LaMeloAgentFrameException(401, "请先登录");
        }
        PlatformAccount current = accountMapper.selectActiveByUsername(currentUsername);
        if (current == null) {
            throw new LaMeloAgentFrameException(401, "请先登录");
        }
        WechatIdentity identity = identityMapper.selectActiveByUserId(current.getId());
        if (identity == null) {
            throw new LaMeloAgentFrameException(400, "当前会话未绑定微信，请先微信登录");
        }
        String username = require(request == null ? null : request.getUsername(), "账号不能为空");
        PlatformAccount target = authenticate(username, require(request.getPassword(), "密码不能为空"));
        if (target.getId().equals(current.getId())) {
            throw new LaMeloAgentFrameException(400, "当前微信已绑定该账号");
        }
        if (identityMapper.selectActiveByUserId(target.getId()) != null) {
            throw new LaMeloAgentFrameException(409, "该账号已绑定其他微信");
        }
        // 必须原地改挂：先删后插会撞 active_openid / active_unionid 唯一索引
        identityMapper.updateUserIdById(identity.getId(), target.getId());
        log.info("微信身份 {} 已从账号 {} 改挂到账号 {}", identity.getOpenid(), current.getId(), target.getId());
        return tokenVo(target);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MiniProgramLoginVo setCredentials(SetCredentialsRequest request, HttpServletRequest currentRequest) {
        String currentUsername = AdminRequestContext.resolveUsername(currentRequest);
        if (StrUtil.isBlank(currentUsername)) {
            throw new LaMeloAgentFrameException(401, "请先登录");
        }
        PlatformAccount account = accountMapper.selectActiveByUsername(currentUsername);
        if (account == null) {
            throw new LaMeloAgentFrameException(401, "请先登录");
        }
        if (hasUsablePassword(account)) {
            throw new LaMeloAgentFrameException(409, "账号已设置密码，不能重复设置");
        }
        String username = require(request == null ? null : request.getUsername(), "账号不能为空");
        String password = require(request == null ? null : request.getPassword(), "密码不能为空");
        PlatformAccount existing = accountMapper.selectActiveByUsername(username);
        if (existing != null && !existing.getId().equals(account.getId())) {
            throw new LaMeloAgentFrameException(409, "账号已存在");
        }
        try {
            accountMapper.updateUsernameAndPasswordById(account.getId(), username,
                PASSWORD_ENCODER.encode(password));
        } catch (RuntimeException exception) {
            if (isDuplicateKey(exception)) {
                throw new LaMeloAgentFrameException(409, "账号已存在");
            }
            throw exception;
        }
        PlatformAccount updated = accountMapper.selectActiveById(account.getId());
        if (updated == null) {
            throw new LaMeloAgentFrameException(500, "账号更新失败");
        }
        return tokenVo(updated);
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
            roles, !hasUsablePassword(account), properties.getTokenExpireMinutes());
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

    /**
     * 首次微信登录时自动创建平台账号与微信身份。
     * 用户名固定为 wx_&lt;openid&gt;，密码写入哨兵值表示尚未设置密码，不分配任何角色。
     */
    private PlatformAccount autoProvision(WechatSession session) {
        String openid = session.openid();
        String username = "wx_" + openid;
        PlatformAccount account = new PlatformAccount();
        account.setUsername(username);
        account.setPasswordHash(UNUSABLE_PASSWORD_HASH);
        account.setEnabled(true);
        try {
            accountMapper.insertAccount(account);
        } catch (RuntimeException exception) {
            if (!isDuplicateKey(exception)) {
                throw exception;
            }
        }
        PlatformAccount persisted = accountMapper.selectActiveByUsername(username);
        if (persisted == null) {
            throw new LaMeloAgentFrameException(500, "账号创建失败");
        }
        WechatIdentity identity = new WechatIdentity();
        identity.setOpenid(openid);
        identity.setUnionid(session.unionid());
        identity.setUserId(persisted.getId());
        try {
            identityMapper.insertIdentity(identity);
        } catch (RuntimeException exception) {
            if (!isDuplicateKey(exception)) {
                throw exception;
            }
        }
        return persisted;
    }

    /** 账号是否已设置可用密码：哨兵值或空值都视为未设置。 */
    private boolean hasUsablePassword(PlatformAccount account) {
        String hash = account == null ? null : account.getPasswordHash();
        return StrUtil.isNotBlank(hash) && hash.startsWith("$2");
    }

    private boolean passwordMatches(String password, String hash) {
        if (StrUtil.isBlank(hash)) {
            return StrUtil.equals(password, properties.getPassword());
        }
        if (!hash.startsWith("$2")) {
            // 哨兵哈希等非 BCrypt 值：不可用于密码登录
            return false;
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
