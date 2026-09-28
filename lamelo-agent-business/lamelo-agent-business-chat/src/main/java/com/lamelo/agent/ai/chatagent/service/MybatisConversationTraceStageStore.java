package com.lamelo.agent.ai.chatagent.service;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Resource;
import com.lamelo.agent.ai.chatagent.data.LaMeloAgentChatExchangeTraceStage;
import com.lamelo.agent.ai.chatagent.mapper.LaMeloAgentChatExchangeTraceStageMapper;
import com.lamelo.agent.ai.chatagent.model.trace.ConversationTraceStageCode;
import com.lamelo.agent.ai.chatagent.model.trace.ConversationTraceStageState;
import com.lamelo.agent.ai.chatagent.model.trace.ConversationTraceStageView;
import com.lamelo.agent.enums.BusinessStatus;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务层
 * @author: 阿星不是程序员
 **/

@Repository
public class MybatisConversationTraceStageStore implements ConversationTraceStageStore {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final LaMeloAgentChatExchangeTraceStageMapper traceStageMapper;
    private final ObjectMapper objectMapper;

    @Resource
    private UidGenerator uidGenerator;

    public MybatisConversationTraceStageStore(LaMeloAgentChatExchangeTraceStageMapper traceStageMapper,
                                              ObjectMapper objectMapper) {
        this.traceStageMapper = traceStageMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public long startStage(String conversationId,
                           long exchangeId,
                           String traceId,
                           ConversationTraceStageCode stageCode,
                           int stageLevel,
                           Long parentStageId,
                           String executionMode,
                           String summaryText,
                           Object snapshot) {
        long stageId = uidGenerator.getUid();
        LaMeloAgentChatExchangeTraceStage stage = new LaMeloAgentChatExchangeTraceStage();
        stage.setId(stageId);
        stage.setConversationId(conversationId);
        stage.setExchangeId(exchangeId);
        stage.setTraceId(traceId);
        stage.setStageCode(stageCode.getCode());
        stage.setStageName(stageCode.getLabel());
        stage.setStageOrder(stageCode.getOrder());
        stage.setStageLevel(stageLevel);
        stage.setParentStageId(parentStageId);
        stage.setExecutionMode(StrUtil.blankToDefault(executionMode, ""));
        stage.setStageState(ConversationTraceStageState.RUNNING.getCode());
        stage.setStartTime(new Date());
        stage.setSummaryText(StrUtil.blankToDefault(summaryText, ""));
        stage.setSnapshotJson(writeNullableJson(snapshot));
        stage.setStatus(BusinessStatus.YES.getCode());
        traceStageMapper.insert(stage);
        return stageId;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void finishStage(long stageId,
                            ConversationTraceStageState stageState,
                            String summaryText,
                            String errorMessage,
                            Object snapshot,
                            long durationMs) {
        traceStageMapper.update(
            null,
            new LambdaUpdateWrapper<LaMeloAgentChatExchangeTraceStage>()
                .eq(LaMeloAgentChatExchangeTraceStage::getId, stageId)
                .set(LaMeloAgentChatExchangeTraceStage::getStageState, stageState.getCode())
                .set(LaMeloAgentChatExchangeTraceStage::getEndTime, new Date())
                .set(LaMeloAgentChatExchangeTraceStage::getDurationMs, Math.max(durationMs, 0L))
                .set(LaMeloAgentChatExchangeTraceStage::getSummaryText, StrUtil.blankToDefault(summaryText, ""))
                .set(LaMeloAgentChatExchangeTraceStage::getErrorMessage, StrUtil.blankToDefault(errorMessage, ""))
                .set(LaMeloAgentChatExchangeTraceStage::getSnapshotJson, writeNullableJson(snapshot))
        );
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationTraceStageView> listStageViews(String conversationId, long exchangeId) {
        return traceStageMapper.selectList(
                new LambdaQueryWrapper<LaMeloAgentChatExchangeTraceStage>()
                    .eq(LaMeloAgentChatExchangeTraceStage::getConversationId, conversationId)
                    .eq(LaMeloAgentChatExchangeTraceStage::getExchangeId, exchangeId)
                    .orderByAsc(LaMeloAgentChatExchangeTraceStage::getStageOrder)
                    .orderByAsc(LaMeloAgentChatExchangeTraceStage::getStartTime)
                    .orderByAsc(LaMeloAgentChatExchangeTraceStage::getId)
            )
            .stream()
            .map(this::toView)
            .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteStages(String conversationId) {
        traceStageMapper.delete(
            new LambdaQueryWrapper<LaMeloAgentChatExchangeTraceStage>()
                .eq(LaMeloAgentChatExchangeTraceStage::getConversationId, conversationId)
        );
    }

    private ConversationTraceStageView toView(LaMeloAgentChatExchangeTraceStage stage) {
        return new ConversationTraceStageView(
            stage.getId(),
            StrUtil.blankToDefault(stage.getTraceId(), ""),
            StrUtil.blankToDefault(stage.getStageCode(), ""),
            StrUtil.blankToDefault(stage.getStageName(), ""),
            stage.getStageOrder(),
            stage.getStageLevel(),
            stage.getParentStageId(),
            StrUtil.blankToDefault(stage.getExecutionMode(), ""),
            ConversationTraceStageState.fromCode(stage.getStageState()).name(),
            toInstant(stage.getStartTime()),
            toInstant(stage.getEndTime()),
            stage.getDurationMs(),
            StrUtil.blankToDefault(stage.getSummaryText(), ""),
            StrUtil.blankToDefault(stage.getErrorMessage(), ""),
            readSnapshot(stage.getSnapshotJson())
        );
    }

    private Instant toInstant(Date value) {
        return value == null ? null : value.toInstant();
    }

    private String writeNullableJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        }
        catch (Exception exception) {
            throw new IllegalStateException("序列化阶段轨迹快照失败", exception);
        }
    }

    private Map<String, Object> readSnapshot(String value) {
        if (StrUtil.isBlank(value)) {
            return Map.of();
        }
        try {
            Map<String, Object> parsed = objectMapper.readValue(value, MAP_TYPE);
            return parsed == null ? Map.of() : new LinkedHashMap<>(parsed);
        }
        catch (Exception exception) {
            throw new IllegalStateException("解析阶段轨迹快照失败", exception);
        }
    }
}
