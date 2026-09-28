package com.lamelo.agent.ai.manage.service;

import com.lamelo.agent.ai.manage.model.route.KnowledgeRouteDecision;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
public interface KnowledgeRouteService {

    KnowledgeRouteDecision route(String question, String rewriteQuestion);

    void recordShadowRoute(String conversationId,
                           long exchangeId,
                           Long selectedDocumentId,
                           String question,
                           String rewriteQuestion);

    void recordAutoRoute(String conversationId,
                         long exchangeId,
                         String question,
                         String rewriteQuestion,
                         KnowledgeRouteDecision decision);
}
