package com.lamelo.agent.ai.chatagent.controller;

import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import lombok.AllArgsConstructor;
import com.lamelo.agent.ai.chatagent.dto.ChatRequestDto;
import com.lamelo.agent.ai.chatagent.dto.ConversationExchangeDetailQueryDto;
import com.lamelo.agent.ai.chatagent.dto.ConversationIdentityDto;
import com.lamelo.agent.ai.chatagent.dto.ConversationSessionListQueryDto;
import com.lamelo.agent.ai.chatagent.dto.RetrievalObserveQueryDto;
import com.lamelo.agent.ai.chatagent.model.ChannelExecutionView;
import com.lamelo.agent.ai.chatagent.model.ConversationExchangeDetailView;
import com.lamelo.agent.ai.chatagent.model.ConversationMemorySummaryView;
import com.lamelo.agent.ai.chatagent.model.ConversationSessionView;
import com.lamelo.agent.ai.chatagent.model.KnowledgeDocumentOptionView;
import com.lamelo.agent.ai.chatagent.model.RetrievalResultView;
import com.lamelo.agent.ai.chatagent.model.StageBenchmarkView;
import com.lamelo.agent.ai.chatagent.service.BusinessChatService;
import com.lamelo.agent.ai.chatagent.service.ConversationOwnershipService;
import com.lamelo.agent.ai.chatagent.vo.ConversationResetVo;
import com.lamelo.agent.ai.chatagent.vo.ConversationSessionListVo;
import com.lamelo.agent.ai.chatagent.vo.ConversationStopVo;
import com.lamelo.agent.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 控制层
 * @author: 阿星不是程序员
 **/
@AllArgsConstructor
@RestController
@RequestMapping("/api/chat")
public class BusinessChatController {

    private final BusinessChatService businessChatService;
    private final ConversationOwnershipService ownershipService;

    @PostMapping(value = "/stream", produces = "text/event-stream;charset=UTF-8")
    public Flux<String> stream(@Valid @RequestBody ChatRequestDto dto, HttpServletRequest request) {
        Long accountId = accountId(request);
        if (ownershipService.isMiniapp(request)) {
            ownershipService.claimForStream(dto.getConversationId(), accountId);
        } else if (dto.getConversationId() != null) {
            ownershipService.check(dto.getConversationId(), accountId);
        }
        return businessChatService.openConversationStream(dto);
    }

    @PostMapping("/document/options")
    public ApiResponse<List<KnowledgeDocumentOptionView>> documentOptions() {
        return ApiResponse.ok(businessChatService.listKnowledgeDocumentOptions());
    }

    @PostMapping("/session/stop")
    public ApiResponse<ConversationStopVo> stop(@Valid @RequestBody ConversationIdentityDto dto, HttpServletRequest request) {
        check(dto.getConversationId(), request);
        return ApiResponse.ok(businessChatService.stopConversation(dto.getConversationId()));
    }

    @PostMapping("/session/detail")
    public ApiResponse<ConversationSessionView> session(@Valid @RequestBody ConversationIdentityDto dto, HttpServletRequest request) {
        check(dto.getConversationId(), request);
        return ApiResponse.ok(businessChatService.getSession(dto.getConversationId()));
    }

    @PostMapping("/exchange/detail")
    public ApiResponse<ConversationExchangeDetailView> exchange(@Valid @RequestBody ConversationExchangeDetailQueryDto dto, HttpServletRequest request) {
        check(dto.getConversationId(), request);
        return ApiResponse.ok(businessChatService.getExchangeDetail(dto.getConversationId(), dto.getExchangeId()));
    }

    @PostMapping("/session/list")
    public ApiResponse<ConversationSessionListVo> sessions(@RequestBody(required = false) ConversationSessionListQueryDto dto,
                                                           HttpServletRequest request) {
        Long accountId = accountId(request);
        return ApiResponse.ok(businessChatService.listSessionsForClient(dto,
            ownershipService.isMiniapp(request) ? accountId : null));
    }

    @PostMapping("/session/reset")
    public ApiResponse<ConversationResetVo> reset(@Valid @RequestBody ConversationIdentityDto dto, HttpServletRequest request) {
        check(dto.getConversationId(), request);
        return ApiResponse.ok(businessChatService.resetConversation(dto.getConversationId()));
    }

    @PostMapping("/session/summary/rebuild")
    public ApiResponse<ConversationMemorySummaryView> rebuildSummary(@Valid @RequestBody ConversationIdentityDto dto,
                                                                      HttpServletRequest request) {
        check(dto.getConversationId(), request);
        return ApiResponse.ok(businessChatService.rebuildConversationSummary(dto.getConversationId()));
    }

    @PostMapping("/exchange/retrieval/results")
    public ApiResponse<List<RetrievalResultView>> retrievalResults(@Valid @RequestBody RetrievalObserveQueryDto dto,
                                                                    HttpServletRequest request) {
        check(dto.getConversationId(), request);
        return ApiResponse.ok(businessChatService.getRetrievalResults(dto.getConversationId(), Long.parseLong(dto.getExchangeId())));
    }

    @PostMapping("/exchange/channel/executions")
    public ApiResponse<List<ChannelExecutionView>> channelExecutions(@Valid @RequestBody RetrievalObserveQueryDto dto,
                                                                      HttpServletRequest request) {
        check(dto.getConversationId(), request);
        return ApiResponse.ok(businessChatService.getChannelExecutions(dto.getConversationId(), Long.parseLong(dto.getExchangeId())));
    }

    @PostMapping("/stage/benchmarks")
    public ApiResponse<List<StageBenchmarkView>> stageBenchmarks() {
        return ApiResponse.ok(businessChatService.getStageBenchmarks());
    }

    private Long accountId(HttpServletRequest request) {
        return ownershipService.isMiniapp(request)
            ? ownershipService.requireMiniappAccount(request)
            : ownershipService.resolveAccountId(request);
    }

    private void check(String conversationId, HttpServletRequest request) {
        ownershipService.check(conversationId, accountId(request));
    }
}
