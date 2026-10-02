package com.lamelo.agent.ai.chatagent;

import com.lamelo.agent.ai.chatagent.controller.BusinessChatController;
import com.lamelo.agent.ai.chatagent.dto.ConversationIdentityDto;
import com.lamelo.agent.ai.chatagent.dto.ConversationSessionListQueryDto;
import com.lamelo.agent.ai.chatagent.service.BusinessChatService;
import com.lamelo.agent.ai.chatagent.service.ConversationOwnershipService;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BusinessChatOwnershipControllerTest {
    private BusinessChatService chatService;
    private ConversationOwnershipService ownershipService;
    private BusinessChatController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        chatService = mock(BusinessChatService.class);
        ownershipService = mock(ConversationOwnershipService.class);
        controller = new BusinessChatController(chatService, ownershipService);
        request = new MockHttpServletRequest();
        request.addHeader("X-LaMelo-Client", "miniapp");
        when(ownershipService.isMiniapp(request)).thenReturn(true);
        when(ownershipService.requireMiniappAccount(request)).thenReturn(7L);
    }

    @Test
    void sessionDetailChecksOwnerBeforeReadingBusinessData() {
        ConversationIdentityDto query = new ConversationIdentityDto();
        query.setConversationId("other-user-id");
        doThrow(new LaMeloAgentFrameException(403, "无权访问该会话"))
            .when(ownershipService).check("other-user-id", 7L);

        assertThrows(LaMeloAgentFrameException.class, () -> controller.session(query, request));
        verify(chatService, never()).getSession("other-user-id");
    }

    @Test
    void sessionListUsesAccountScopedPagination() {
        ConversationSessionListQueryDto query = new ConversationSessionListQueryDto();
        controller.sessions(query, request);
        verify(chatService).listSessionsForClient(query, 7L);
        verify(chatService, never()).listSessions(query);
    }
}
