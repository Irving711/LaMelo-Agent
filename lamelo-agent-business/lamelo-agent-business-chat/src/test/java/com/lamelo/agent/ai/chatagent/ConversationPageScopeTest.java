package com.lamelo.agent.ai.chatagent;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamelo.agent.ai.chatagent.data.LaMeloAgentChatDialogue;
import com.lamelo.agent.ai.chatagent.mapper.LaMeloAgentChatDialogueMapper;
import com.lamelo.agent.ai.chatagent.mapper.LaMeloAgentChatExchangeMapper;
import com.lamelo.agent.ai.chatagent.service.MybatisConversationArchiveStore;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.session.Configuration;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConversationPageScopeTest {
    @Test
    void miniappPageUsesAccountScopeBeforePagination() {
        String sql = querySqlFor(7L);
        assertTrue(sql.contains("account_id = 7"), sql);
        assertTrue(sql.contains("lamelo_agent_conversation_owner"), sql);
    }

    @Test
    void legacyWebPageExcludesMiniappOwnedConversationsBeforePagination() {
        String sql = querySqlFor(null);
        assertTrue(sql.contains("NOT IN"), sql);
        assertTrue(sql.contains("lamelo_agent_conversation_owner"), sql);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private String querySqlFor(Long ownerId) {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), "scope-test"),
            LaMeloAgentChatDialogue.class);
        LaMeloAgentChatDialogueMapper dialogueMapper = mock(LaMeloAgentChatDialogueMapper.class);
        LaMeloAgentChatExchangeMapper exchangeMapper = mock(LaMeloAgentChatExchangeMapper.class);
        when(dialogueMapper.selectPage(any(), any())).thenAnswer(invocation -> invocation.getArgument(0));
        MybatisConversationArchiveStore store = new MybatisConversationArchiveStore(
            dialogueMapper, exchangeMapper, new ObjectMapper());
        store.listSessionRecordPageForClient(1, 20, null, null, null, ownerId);
        ArgumentCaptor<LambdaQueryWrapper<LaMeloAgentChatDialogue>> captor = ArgumentCaptor.forClass((Class) LambdaQueryWrapper.class);
        verify(dialogueMapper).selectPage(any(), captor.capture());
        return captor.getValue().getSqlSegment();
    }
}
