package com.lamelo.agent.ai.manage.service;

import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentProfile;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStructureNode;
import com.lamelo.agent.ai.manage.support.DocumentAnalysisResult;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
public interface DocumentProfileService {

    LaMeloAgentDocumentProfile generateProfile(Long documentId,
                                              DocumentAnalysisResult analysisResult,
                                              List<LaMeloAgentDocumentStructureNode> structureNodes);

    LaMeloAgentDocumentProfile regenerateProfile(Long documentId);

    List<LaMeloAgentDocumentProfile> batchRegenerateProfiles(Collection<Long> documentIds);

    Optional<LaMeloAgentDocumentProfile> getByDocumentId(Long documentId);
}
