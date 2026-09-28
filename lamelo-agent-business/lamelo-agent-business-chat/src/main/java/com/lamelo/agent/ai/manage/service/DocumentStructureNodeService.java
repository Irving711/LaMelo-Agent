package com.lamelo.agent.ai.manage.service;

import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStructureNode;
import com.lamelo.agent.ai.manage.support.DocumentStructureNodeCandidate;

import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务层
 * @author: 阿星不是程序员
 **/

public interface DocumentStructureNodeService {

    List<LaMeloAgentDocumentStructureNode> replaceDocumentNodes(Long documentId,
                                                               Long parseTaskId,
                                                               List<DocumentStructureNodeCandidate> candidates);

    List<LaMeloAgentDocumentStructureNode> listDocumentNodes(Long documentId, Long parseTaskId);

    Map<Long, LaMeloAgentDocumentStructureNode> nodeMap(Long documentId, Long parseTaskId);

    void deleteByDocumentId(Long documentId);
}
