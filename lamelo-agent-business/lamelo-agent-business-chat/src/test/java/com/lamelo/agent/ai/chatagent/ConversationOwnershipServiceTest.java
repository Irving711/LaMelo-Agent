package com.lamelo.agent.ai.chatagent;

import com.lamelo.agent.ai.auth.config.AdminAuthProperties;
import com.lamelo.agent.ai.auth.data.PlatformAccount;
import com.lamelo.agent.ai.auth.mapper.PlatformAccountMapper;
import com.lamelo.agent.ai.auth.support.AdminJwtTokenService;
import com.lamelo.agent.ai.chatagent.mapper.ConversationOwnerMapper;
import com.lamelo.agent.ai.chatagent.service.ConversationArchiveStore;
import com.lamelo.agent.ai.chatagent.service.ConversationOwnershipService;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationOwnershipServiceTest {
    private ConversationOwnerMapper ownerMapper;
    private ConversationArchiveStore archiveStore;
    private PlatformAccountMapper accountMapper;
    private ConversationOwnershipService service;
    private AdminJwtTokenService tokens;

    @BeforeEach
    void setUp() {
        ownerMapper = mock(ConversationOwnerMapper.class);
        archiveStore = mock(ConversationArchiveStore.class);
        accountMapper = mock(PlatformAccountMapper.class);
        tokens = new AdminJwtTokenService(new AdminAuthProperties());
        service = new ConversationOwnershipService(tokens, accountMapper, ownerMapper, archiveStore);
    }

    @Test
    void rejectsAnotherAccountAndAnonymousForOwnedConversation() {
        when(ownerMapper.selectOwnerId("private-id")).thenReturn(7L);
        assertThrows(LaMeloAgentFrameException.class, () -> service.check("private-id", 8L));
        assertThrows(LaMeloAgentFrameException.class, () -> service.check("private-id", null));
        service.check("private-id", 7L);
    }

    @Test
    void doesNotAllowClaimingAnExistingLegacyConversation() {
        when(archiveStore.getSessionRecord("legacy-id"))
            .thenReturn(Optional.of(mock(ConversationArchiveStore.ConversationArchiveRecord.class)));
        assertThrows(LaMeloAgentFrameException.class, () -> service.claimForStream("legacy-id", 7L));
    }

    @Test
    void claimsNewConversationAndChecksPersistedOwner() {
        when(archiveStore.getSessionRecord("new-id")).thenReturn(Optional.empty());
        when(ownerMapper.selectOwnerId("new-id")).thenReturn(null, 7L);
        service.claimForStream("new-id", 7L);
        verify(ownerMapper).claim("new-id", 7L);
    }

    @Test
    void requiresAnActiveAccountForMiniappRequests() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-LaMelo-Client", "miniapp");
        assertThrows(LaMeloAgentFrameException.class, () -> service.requireMiniappAccount(request));
        request.addHeader("Authorization", "Bearer " + tokens.generateToken("alice"));
        PlatformAccount account = new PlatformAccount();
        account.setId(7L);
        account.setEnabled(true);
        when(accountMapper.selectActiveByUsername("alice")).thenReturn(account);
        assertEquals(7L, service.requireMiniappAccount(request));
    }
}
