package com.lamelo.agent.ai.manage.service;

import com.lamelo.agent.ai.manage.dto.DocumentProfileBatchRegenerateDto;
import com.lamelo.agent.ai.manage.dto.DocumentProfileDetailQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentProfileRegenerateDto;
import com.lamelo.agent.ai.manage.dto.KnowledgeRouteTraceQueryDto;
import com.lamelo.agent.ai.manage.dto.KnowledgeScopeDeleteDto;
import com.lamelo.agent.ai.manage.dto.KnowledgeScopeSaveDto;
import com.lamelo.agent.ai.manage.dto.KnowledgeTopicDeleteDto;
import com.lamelo.agent.ai.manage.dto.KnowledgeTopicQueryDto;
import com.lamelo.agent.ai.manage.dto.KnowledgeTopicSaveDto;
import com.lamelo.agent.ai.manage.dto.TopicDocumentRelationListQueryDto;
import com.lamelo.agent.ai.manage.dto.TopicDocumentRelationRemoveDto;
import com.lamelo.agent.ai.manage.dto.TopicDocumentRelationSaveDto;
import com.lamelo.agent.ai.manage.vo.DocumentProfileVo;
import com.lamelo.agent.ai.manage.vo.KnowledgeRouteTracePageVo;
import com.lamelo.agent.ai.manage.vo.KnowledgeScopeItemVo;
import com.lamelo.agent.ai.manage.vo.KnowledgeTopicItemVo;
import com.lamelo.agent.ai.manage.vo.TopicDocumentRelationItemVo;

import java.util.List;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务层
 * @author: 阿星不是程序员
 **/
public interface KnowledgeManageService {

    KnowledgeScopeItemVo saveScope(KnowledgeScopeSaveDto dto);

    boolean deleteScope(KnowledgeScopeDeleteDto dto);

    List<KnowledgeScopeItemVo> listScopes();

    KnowledgeTopicItemVo saveTopic(KnowledgeTopicSaveDto dto);

    boolean deleteTopic(KnowledgeTopicDeleteDto dto);

    List<KnowledgeTopicItemVo> listTopics(KnowledgeTopicQueryDto dto);

    DocumentProfileVo queryProfile(DocumentProfileDetailQueryDto dto);

    DocumentProfileVo regenerateProfile(DocumentProfileRegenerateDto dto);

    List<DocumentProfileVo> batchRegenerateProfiles(DocumentProfileBatchRegenerateDto dto);

    List<TopicDocumentRelationItemVo> listTopicDocuments(TopicDocumentRelationListQueryDto dto);

    TopicDocumentRelationItemVo saveTopicDocumentRelation(TopicDocumentRelationSaveDto dto);

    boolean removeTopicDocumentRelation(TopicDocumentRelationRemoveDto dto);

    KnowledgeRouteTracePageVo queryRouteTracePage(KnowledgeRouteTraceQueryDto dto);
}
