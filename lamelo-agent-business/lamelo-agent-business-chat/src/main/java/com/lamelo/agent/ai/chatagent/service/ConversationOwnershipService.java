package com.lamelo.agent.ai.chatagent.service;

import cn.hutool.core.util.StrUtil;
import com.lamelo.agent.ai.auth.data.PlatformAccount;
import com.lamelo.agent.ai.auth.mapper.PlatformAccountMapper;
import com.lamelo.agent.ai.auth.support.AdminJwtTokenService;
import com.lamelo.agent.ai.chatagent.mapper.ConversationOwnerMapper;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

import java.util.Objects;

@Service
public class ConversationOwnershipService {
    private final AdminJwtTokenService tokenService;
    private final PlatformAccountMapper accountMapper;
    private final ConversationOwnerMapper ownerMapper;
    private final ConversationArchiveStore archiveStore;

    public ConversationOwnershipService(AdminJwtTokenService tokenService,
                                        PlatformAccountMapper accountMapper,
                                        ConversationOwnerMapper ownerMapper,
                                        ConversationArchiveStore archiveStore) {
        this.tokenService = tokenService;
        this.accountMapper = accountMapper;
        this.ownerMapper = ownerMapper;
        this.archiveStore = archiveStore;
    }

    public Long resolveAccountId(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StrUtil.isBlank(authorization)) {
            return null;
        }
        String token = StrUtil.trim(authorization);
        if (StrUtil.startWithIgnoreCase(token, "Bearer ")) {
            token = StrUtil.trim(token.substring(7));
        }
        String username = tokenService.parseToken(token).getSubject();
        PlatformAccount account = accountMapper.selectActiveByUsername(username);
        if (account == null || !Boolean.TRUE.equals(account.getEnabled())) {
            throw new LaMeloAgentFrameException(401, "账号已停用，请重新登录");
        }
        return account.getId();
    }

    public boolean isMiniapp(HttpServletRequest request) {
        return "miniapp".equalsIgnoreCase(request.getHeader("X-LaMelo-Client"));
    }

    public Long requireMiniappAccount(HttpServletRequest request) {
        Long accountId = resolveAccountId(request);
        if (accountId == null) {
            throw new LaMeloAgentFrameException(401, "请先登录");
        }
        return accountId;
    }

    public void check(String conversationId, Long accountId) {
        Long ownerId = ownerMapper.selectOwnerId(conversationId);
        if (ownerId != null && !Objects.equals(ownerId, accountId)) {
            throw new LaMeloAgentFrameException(403, "无权访问该会话");
        }
    }

    public void claimForStream(String conversationId, Long accountId) {
        if (StrUtil.isBlank(conversationId)) {
            if (accountId != null) {
                throw new LaMeloAgentFrameException(400, "conversationId 不能为空");
            }
            return;
        }
        Long ownerId = ownerMapper.selectOwnerId(conversationId);
        if (accountId == null) {
            if (ownerId != null) {
                throw new LaMeloAgentFrameException(403, "无权访问该会话");
            }
            return;
        }
        if (ownerId == null && archiveStore.getSessionRecord(conversationId).isPresent()) {
            throw new LaMeloAgentFrameException(403, "无权访问该会话");
        }
        if (ownerId == null) {
            ownerMapper.claim(conversationId, accountId);
        }
        check(conversationId, accountId);
    }
}
