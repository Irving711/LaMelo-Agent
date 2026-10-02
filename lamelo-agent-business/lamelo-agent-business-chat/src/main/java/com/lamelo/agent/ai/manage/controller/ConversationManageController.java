package com.lamelo.agent.ai.manage.controller;

import com.lamelo.agent.ai.chatagent.dto.ConversationExchangeDetailQueryDto;
import com.lamelo.agent.ai.chatagent.dto.ConversationIdentityDto;
import com.lamelo.agent.ai.chatagent.dto.ConversationSessionListQueryDto;
import com.lamelo.agent.ai.chatagent.dto.RetrievalObserveQueryDto;
import com.lamelo.agent.ai.chatagent.model.ChannelExecutionView;
import com.lamelo.agent.ai.chatagent.model.ConversationExchangeDetailView;
import com.lamelo.agent.ai.chatagent.model.ConversationSessionView;
import com.lamelo.agent.ai.chatagent.model.RetrievalResultView;
import com.lamelo.agent.ai.chatagent.model.StageBenchmarkView;
import com.lamelo.agent.ai.chatagent.service.BusinessChatService;
import com.lamelo.agent.ai.chatagent.vo.ConversationSessionListVo;
import com.lamelo.agent.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/manage/chat")
public class ConversationManageController {
    private final BusinessChatService chatService;

    public ConversationManageController(BusinessChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/session/list")
    public ApiResponse<ConversationSessionListVo> sessions(@RequestBody(required = false) ConversationSessionListQueryDto query) {
        return ApiResponse.ok(chatService.listSessions(query));
    }

    @PostMapping("/session/detail")
    public ApiResponse<ConversationSessionView> session(@Valid @RequestBody ConversationIdentityDto query) {
        return ApiResponse.ok(chatService.getSession(query.getConversationId()));
    }

    @PostMapping("/exchange/detail")
    public ApiResponse<ConversationExchangeDetailView> exchange(@Valid @RequestBody ConversationExchangeDetailQueryDto query) {
        return ApiResponse.ok(chatService.getExchangeDetail(query.getConversationId(), query.getExchangeId()));
    }

    @PostMapping("/exchange/retrieval/results")
    public ApiResponse<List<RetrievalResultView>> retrievalResults(@Valid @RequestBody RetrievalObserveQueryDto query) {
        return ApiResponse.ok(chatService.getRetrievalResults(query.getConversationId(), Long.parseLong(query.getExchangeId())));
    }

    @PostMapping("/exchange/channel/executions")
    public ApiResponse<List<ChannelExecutionView>> channelExecutions(@Valid @RequestBody RetrievalObserveQueryDto query) {
        return ApiResponse.ok(chatService.getChannelExecutions(query.getConversationId(), Long.parseLong(query.getExchangeId())));
    }

    @PostMapping("/stage/benchmarks")
    public ApiResponse<List<StageBenchmarkView>> stageBenchmarks() {
        return ApiResponse.ok(chatService.getStageBenchmarks());
    }
}
