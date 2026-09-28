package com.lamelo.agent.ai.manage.service;

import com.lamelo.agent.ai.manage.data.LaMeloAgentDocument;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStrategyPlan;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStrategyStep;
import com.lamelo.agent.ai.manage.support.DocumentAnalysisResult;
import com.lamelo.agent.ai.manage.support.DocumentStrategyPlanDraft;
import com.lamelo.agent.ai.manage.support.ParentBlockCandidate;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务层
 * @author: 阿星不是程序员
 **/

public interface DocumentStrategyService {

    DocumentStrategyPlanDraft recommendStrategy(LaMeloAgentDocument document, DocumentAnalysisResult analysisResult);

    List<LaMeloAgentDocumentStrategyStep> normalizeSteps(LaMeloAgentDocumentStrategyPlan basePlan,
                                                        List<LaMeloAgentDocumentStrategyStep> baseSteps,
                                                        List<Integer> requestParentStrategyTypes,
                                                        List<Integer> requestChildStrategyTypes,
                                                        Long documentId);

    List<ParentBlockCandidate> buildParentBlocks(LaMeloAgentDocument document,
                                                 LaMeloAgentDocumentStrategyPlan plan,
                                                 List<LaMeloAgentDocumentStrategyStep> steps,
                                                 String parsedText);
}
