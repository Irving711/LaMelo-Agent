package com.lamelo.agent.ai.manage.service.impl;

import lombok.AllArgsConstructor;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocument;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentChunk;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentParentBlock;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentProfile;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStrategyPlan;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStrategyStep;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentTask;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentTaskLog;
import com.lamelo.agent.ai.manage.data.LaMeloAgentTopicDocumentRelation;
import com.lamelo.agent.ai.manage.dto.DocumentChunkQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentChunkDetailQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentDeleteDto;
import com.lamelo.agent.ai.manage.dto.DocumentDetailQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentIndexBuildDto;
import com.lamelo.agent.ai.manage.dto.DocumentPageQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentStrategyConfirmDto;
import com.lamelo.agent.ai.manage.dto.DocumentStrategyPlanQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentStrategyStepItemDto;
import com.lamelo.agent.ai.manage.dto.DocumentTaskLogQueryDto;
import com.lamelo.agent.ai.manage.dto.DocumentUploadDto;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentChunkMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentParentBlockMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentProfileMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentStrategyPlanMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentStrategyStepMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskLogMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentTopicDocumentRelationMapper;
import com.lamelo.agent.ai.manage.mq.DocumentTaskPublisher;
import com.lamelo.agent.ai.manage.mq.message.DocumentIndexBuildMessage;
import com.lamelo.agent.ai.manage.mq.message.DocumentParseRouteMessage;
import com.lamelo.agent.ai.manage.service.DocumentManageService;
import com.lamelo.agent.ai.manage.service.DocumentNavigationIndexService;
import com.lamelo.agent.ai.manage.service.DocumentStorageService;
import com.lamelo.agent.ai.manage.service.DocumentStructureGraphProjectionService;
import com.lamelo.agent.ai.manage.service.DocumentStructureNodeService;
import com.lamelo.agent.ai.manage.service.DocumentStrategyService;
import com.lamelo.agent.ai.manage.service.DocumentTaskLogService;
import com.lamelo.agent.ai.manage.service.DocumentVectorGateway;
import com.lamelo.agent.ai.manage.service.KnowledgeRouteIndexService;
import com.lamelo.agent.ai.manage.service.keyword.DocumentKeywordSearchGateway;
import com.lamelo.agent.ai.manage.support.StoredObjectInfo;
import com.lamelo.agent.ai.manage.vo.DocumentChunkItemVo;
import com.lamelo.agent.ai.manage.vo.DocumentChunkQueryVo;
import com.lamelo.agent.ai.manage.vo.DocumentChunkDetailVo;
import com.lamelo.agent.ai.manage.vo.DocumentDeleteVo;
import com.lamelo.agent.ai.manage.vo.DocumentIndexBuildVo;
import com.lamelo.agent.ai.manage.vo.DocumentListItemVo;
import com.lamelo.agent.ai.manage.vo.DocumentParentBlockItemVo;
import com.lamelo.agent.ai.manage.vo.DocumentPageQueryVo;
import com.lamelo.agent.ai.manage.vo.DocumentStrategyConfirmVo;
import com.lamelo.agent.ai.manage.vo.DocumentStrategyPipelineVo;
import com.lamelo.agent.ai.manage.vo.DocumentStrategyPlanQueryVo;
import com.lamelo.agent.ai.manage.vo.DocumentStrategyPlanVo;
import com.lamelo.agent.ai.manage.vo.DocumentStrategyStepVo;
import com.lamelo.agent.ai.manage.vo.DocumentTaskLogQueryVo;
import com.lamelo.agent.ai.manage.vo.DocumentTaskLogVo;
import com.lamelo.agent.ai.manage.vo.DocumentUploadVo;
import com.lamelo.agent.enums.BaseCode;
import com.lamelo.agent.enums.BusinessStatus;
import com.lamelo.agent.enums.DocumentChunkSourceTypeEnum;
import com.lamelo.agent.enums.DocumentFileTypeEnum;
import com.lamelo.agent.enums.DocumentIndexStatusEnum;
import com.lamelo.agent.enums.DocumentLogLevelEnum;
import com.lamelo.agent.enums.DocumentManageCode;
import com.lamelo.agent.enums.DocumentOperatorTypeEnum;
import com.lamelo.agent.enums.DocumentParseStatusEnum;
import com.lamelo.agent.enums.DocumentPlanSourceEnum;
import com.lamelo.agent.enums.DocumentPlanStatusEnum;
import com.lamelo.agent.enums.DocumentStorageTypeEnum;
import com.lamelo.agent.enums.DocumentStrategyExecuteStatusEnum;
import com.lamelo.agent.enums.DocumentStrategyPipelineTypeEnum;
import com.lamelo.agent.enums.DocumentStrategyRoleEnum;
import com.lamelo.agent.enums.DocumentStrategySourceTypeEnum;
import com.lamelo.agent.enums.DocumentStrategyStatusEnum;
import com.lamelo.agent.enums.DocumentStrategyTypeEnum;
import com.lamelo.agent.enums.DocumentTaskEventTypeEnum;
import com.lamelo.agent.enums.DocumentTaskStageEnum;
import com.lamelo.agent.enums.DocumentTaskStatusEnum;
import com.lamelo.agent.enums.DocumentTaskTypeEnum;
import com.lamelo.agent.enums.DocumentTriggerSourceEnum;
import com.lamelo.agent.enums.DocumentVectorStatusEnum;
import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务实现层
 * @author: 阿星不是程序员
 **/

@Slf4j
@AllArgsConstructor
@Service
public class DocumentManageServiceImpl implements DocumentManageService {

    private final LaMeloAgentDocumentMapper documentMapper;

    private final LaMeloAgentDocumentStrategyPlanMapper planMapper;

    private final LaMeloAgentDocumentStrategyStepMapper stepMapper;

    private final LaMeloAgentDocumentTaskMapper taskMapper;

    private final LaMeloAgentDocumentTaskLogMapper taskLogMapper;

    private final LaMeloAgentDocumentChunkMapper chunkMapper;

    private final LaMeloAgentDocumentParentBlockMapper parentBlockMapper;

    private final LaMeloAgentDocumentProfileMapper documentProfileMapper;

    private final LaMeloAgentTopicDocumentRelationMapper topicDocumentRelationMapper;

    private final DocumentStorageService storageService;

    private final DocumentStructureNodeService structureNodeService;

    private final DocumentStrategyService strategyService;

    private final DocumentTaskLogService taskLogService;

    private final DocumentVectorGateway vectorGateway;

    private final ObjectProvider<DocumentKeywordSearchGateway> keywordSearchGatewayProvider;

    private final ObjectProvider<DocumentNavigationIndexService> navigationIndexServiceProvider;

    private final ObjectProvider<DocumentStructureGraphProjectionService> graphProjectionServiceProvider;

    private final ObjectProvider<KnowledgeRouteIndexService> knowledgeRouteIndexServiceProvider;

    private final DocumentTaskPublisher taskPublisher;

    private final TransactionTemplate transactionTemplate;
    
    private final UidGenerator uidGenerator;

    @Override
    public DocumentUploadVo upload(MultipartFile file, DocumentUploadDto dto) {

        if (file == null || file.isEmpty()) {
            throw new LaMeloAgentFrameException(DocumentManageCode.EMPTY_FILE_CONTENT.getCode(),
                DocumentManageCode.EMPTY_FILE_CONTENT.getMsg());
        }

        String originalFileName = file.getOriginalFilename();
        if (StrUtil.isBlank(originalFileName)) {
            throw new LaMeloAgentFrameException(DocumentManageCode.UNSUPPORTED_FILE_TYPE.getCode(),
                "上传文件缺少原始文件名，无法识别文件类型。");
        }

        DocumentFileTypeEnum fileType = DocumentFileTypeEnum.fromFileName(originalFileName);
        if (fileType == null) {
            throw new LaMeloAgentFrameException(DocumentManageCode.UNSUPPORTED_FILE_TYPE.getCode(),
                DocumentManageCode.UNSUPPORTED_FILE_TYPE.getMsg());
        }

        byte[] fileBytes = getFileBytes(file);
        Long documentId = uidGenerator.getUid();

        StoredObjectInfo storedObjectInfo = storageService.uploadOriginalFile(
                documentId, originalFileName, fileBytes, file.getContentType());

        LaMeloAgentDocument document = new LaMeloAgentDocument();
        document.setId(documentId);
        document.setDocumentName(StrUtil.isNotBlank(dto.getDocumentName()) ? dto.getDocumentName() : originalFileName);
        document.setOriginalFileName(originalFileName);
        document.setFileType(fileType.getCode());
        document.setMimeType(file.getContentType());
        document.setFileSize((long) fileBytes.length);
        document.setStorageType(DocumentStorageTypeEnum.MINIO.getCode());
        document.setBucketName(storedObjectInfo.getBucketName());
        document.setObjectName(storedObjectInfo.getObjectName());
        document.setObjectUrl(storedObjectInfo.getObjectUrl());
        document.setParseStatus(DocumentParseStatusEnum.PARSING.getCode());
        document.setStrategyStatus(DocumentStrategyStatusEnum.WAIT_RECOMMEND.getCode());
        document.setIndexStatus(DocumentIndexStatusEnum.WAIT_BUILD.getCode());
        document.setCharCount(0);
        document.setTokenCount(0);

        document.setKnowledgeScopeCode(StrUtil.trimToNull(dto.getKnowledgeScopeCode()));
        document.setKnowledgeScopeName(StrUtil.trimToNull(dto.getKnowledgeScopeName()));
        document.setBusinessCategory(StrUtil.trimToNull(dto.getBusinessCategory()));
        document.setDocumentTags(StrUtil.trimToNull(dto.getDocumentTags()));
        document.setStatus(BusinessStatus.YES.getCode());

        Long taskId = uidGenerator.getUid();
        LaMeloAgentDocumentTask task = new LaMeloAgentDocumentTask();
        task.setId(taskId);
        task.setDocumentId(documentId);
        task.setTaskType(DocumentTaskTypeEnum.PARSE_ROUTE.getCode());
        task.setTaskStatus(DocumentTaskStatusEnum.NEW.getCode());
        task.setCurrentStage(DocumentTaskStageEnum.FILE_UPLOAD.getCode());
        Long operatorId = parseOptionalLong(dto.getOperatorId());
        task.setTriggerSource(resolveTriggerSource(operatorId));
        task.setRetryCount(0);
        task.setStatus(BusinessStatus.YES.getCode());

        DocumentUploadVo uploadVo = transactionTemplate.execute(status -> {
            documentMapper.insert(document);
            taskMapper.insert(task);

            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.FILE_UPLOAD.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                resolveOperatorType(operatorId),
                operatorId,
                "文件上传完成，已进入解析与策略推荐队列。",
                Map.of("originalFileName", originalFileName, "fileSize", fileBytes.length));

            return new DocumentUploadVo(documentId, taskId, document.getDocumentName(),
                document.getParseStatus(), document.getStrategyStatus(), document.getIndexStatus());
        });

        taskPublisher.sendParseRoute(new DocumentParseRouteMessage(documentId, taskId));

        return uploadVo;
    }

    @Override
    public DocumentPageQueryVo queryDocumentPage(DocumentPageQueryDto dto) {

        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 10 : dto.getPageSize();
        String keyword = StrUtil.isNotBlank(dto.getKeyword()) ? dto.getKeyword().trim() : null;

        Page<LaMeloAgentDocument> page = new Page<>(pageNo, pageSize);
        LambdaQueryWrapper<LaMeloAgentDocument> wrapper = new LambdaQueryWrapper<LaMeloAgentDocument>()
            .eq(LaMeloAgentDocument::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(LaMeloAgentDocument::getEditTime, LaMeloAgentDocument::getId);

        if (keyword != null) {
            wrapper.and(query -> query.like(LaMeloAgentDocument::getDocumentName, keyword)
                .or()
                .like(LaMeloAgentDocument::getOriginalFileName, keyword));
        }

        IPage<LaMeloAgentDocument> resultPage = documentMapper.selectPage(page, wrapper);
        List<LaMeloAgentDocument> documentList = resultPage.getRecords();
        Map<Long, LaMeloAgentDocumentTask> latestTaskMap = getLatestTaskMap(documentList);

        List<DocumentListItemVo> records = documentList.stream()
            .map(document -> toDocumentListItemVo(document, latestTaskMap.get(document.getId())))
            .toList();

        return new DocumentPageQueryVo(pageNo, pageSize, resultPage.getTotal(), records);
    }

    @Override
    public DocumentListItemVo queryDocumentDetail(DocumentDetailQueryDto dto) {
        LaMeloAgentDocument document = getDocumentOrThrow(dto.getDocumentId());
        LaMeloAgentDocumentTask latestTask = getLatestTask(document.getId());
        return toDocumentListItemVo(document, latestTask);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentDeleteVo deleteDocument(DocumentDeleteDto dto) {
        Long documentId = parseRequiredLong(dto.getDocumentId(), "文档id");
        LaMeloAgentDocument document = getDocumentOrThrow(documentId);

        long activeTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<LaMeloAgentDocumentTask>()
            .eq(LaMeloAgentDocumentTask::getDocumentId, documentId)
            .eq(LaMeloAgentDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .in(LaMeloAgentDocumentTask::getTaskStatus, DocumentTaskStatusEnum.NEW.getCode(), DocumentTaskStatusEnum.RUNNING.getCode()));
        if (activeTaskCount > 0) {
            throw new LaMeloAgentFrameException(DocumentManageCode.DOCUMENT_STATUS_INVALID.getCode(),
                "当前文档存在进行中的任务，请等待任务结束后再删除。");
        }

        storageService.deleteObjects(List.of(document.getObjectName(), document.getParseTextPath()));
        vectorGateway.deleteByDocumentId(documentId);

        DocumentKeywordSearchGateway keywordSearchGateway = keywordSearchGatewayProvider.getIfAvailable();
        if (keywordSearchGateway != null) {
            log.info("删除文档关键词索引: documentId={}", documentId);
            keywordSearchGateway.deleteByDocumentId(documentId);
        }
        DocumentNavigationIndexService navigationIndexService = navigationIndexServiceProvider.getIfAvailable();
        if (navigationIndexService != null) {
            log.info("删除文档导航索引: documentId={}", documentId);
            navigationIndexService.deleteByDocumentId(documentId);
        }
        KnowledgeRouteIndexService knowledgeRouteIndexService = knowledgeRouteIndexServiceProvider.getIfAvailable();
        if (knowledgeRouteIndexService != null) {
            log.info("删除知识路由索引中的文档快照: documentId={}", documentId);
            knowledgeRouteIndexService.deleteDocumentRoute(documentId);
        }
        DocumentStructureGraphProjectionService graphProjectionService = graphProjectionServiceProvider.getIfAvailable();
        if (graphProjectionService != null && graphProjectionService.enabled()) {
            log.info("删除文档结构图投影: documentId={}", documentId);
            graphProjectionService.deleteByDocumentId(documentId);
        }

        documentProfileMapper.delete(new LambdaQueryWrapper<LaMeloAgentDocumentProfile>()
            .eq(LaMeloAgentDocumentProfile::getDocumentId, documentId));
        topicDocumentRelationMapper.delete(new LambdaQueryWrapper<LaMeloAgentTopicDocumentRelation>()
            .eq(LaMeloAgentTopicDocumentRelation::getDocumentId, documentId));
        parentBlockMapper.delete(new LambdaQueryWrapper<LaMeloAgentDocumentParentBlock>()
            .eq(LaMeloAgentDocumentParentBlock::getDocumentId, documentId));
        chunkMapper.delete(new LambdaQueryWrapper<LaMeloAgentDocumentChunk>()
            .eq(LaMeloAgentDocumentChunk::getDocumentId, documentId));
        structureNodeService.deleteByDocumentId(documentId);
        taskLogMapper.delete(new LambdaQueryWrapper<LaMeloAgentDocumentTaskLog>()
            .eq(LaMeloAgentDocumentTaskLog::getDocumentId, documentId));
        stepMapper.delete(new LambdaQueryWrapper<LaMeloAgentDocumentStrategyStep>()
            .eq(LaMeloAgentDocumentStrategyStep::getDocumentId, documentId));
        taskMapper.delete(new LambdaQueryWrapper<LaMeloAgentDocumentTask>()
            .eq(LaMeloAgentDocumentTask::getDocumentId, documentId));
        planMapper.delete(new LambdaQueryWrapper<LaMeloAgentDocumentStrategyPlan>()
            .eq(LaMeloAgentDocumentStrategyPlan::getDocumentId, documentId));
        documentMapper.deleteById(documentId);

        return new DocumentDeleteVo(documentId, document.getDocumentName());
    }

    @Override
    public DocumentStrategyPlanQueryVo queryStrategyPlan(DocumentStrategyPlanQueryDto dto) {

        LaMeloAgentDocument document = getDocumentOrThrow(dto.getDocumentId());
        DocumentStrategyPlanVo planVo = null;
        boolean planReady = false;

        if (document.getCurrentPlanId() != null) {
            LaMeloAgentDocumentStrategyPlan plan = planMapper.selectById(document.getCurrentPlanId());
            if (plan != null && Objects.equals(plan.getStatus(), BusinessStatus.YES.getCode())) {
                List<LaMeloAgentDocumentStrategyStep> stepList = listStepByPlanId(plan.getId());
                planVo = toPlanVo(plan, stepList);
                planReady = true;
            }
        }

        return new DocumentStrategyPlanQueryVo(
            document.getId(),
            document.getDocumentName(),
            document.getParseStatus(),
            enumMsg(DocumentParseStatusEnum.getRc(document.getParseStatus())),
            document.getStrategyStatus(),
            enumMsg(DocumentStrategyStatusEnum.getRc(document.getStrategyStatus())),
            document.getIndexStatus(),
            enumMsg(DocumentIndexStatusEnum.getRc(document.getIndexStatus())),
            document.getParseErrorMsg(),
            planReady,
            planVo
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentStrategyConfirmVo confirmStrategy(DocumentStrategyConfirmDto dto) {

        LaMeloAgentDocument document = getDocumentOrThrow(dto.getDocumentId());
        if (!Objects.equals(document.getParseStatus(), DocumentParseStatusEnum.PARSE_SUCCESS.getCode())) {
            throw new LaMeloAgentFrameException(DocumentManageCode.DOCUMENT_STATUS_INVALID.getCode(), "当前文档还未完成解析，不能确认策略。");
        }

        if (!Objects.equals(document.getCurrentPlanId(), dto.getBasePlanId())) {
            throw new LaMeloAgentFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(), "当前文档的基础方案不存在或已切换。");
        }

        LaMeloAgentDocumentStrategyPlan basePlan = planMapper.selectById(dto.getBasePlanId());
        if (basePlan == null || !Objects.equals(basePlan.getStatus(), BusinessStatus.YES.getCode())) {
            throw new LaMeloAgentFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(),
                DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getMsg());
        }

        List<LaMeloAgentDocumentStrategyStep> baseStepList = listStepByPlanId(basePlan.getId());
        List<Integer> requestParentTypeList = dto.getParentSteps().stream()
            .sorted(Comparator.comparing(item -> item.getStepNo() == null ? Integer.MAX_VALUE : item.getStepNo()))
            .map(DocumentStrategyStepItemDto::getStrategyType)
            .filter(Objects::nonNull)
            .toList();
        List<Integer> requestChildTypeList = dto.getChildSteps().stream()
            .sorted(Comparator.comparing(item -> item.getStepNo() == null ? Integer.MAX_VALUE : item.getStepNo()))
            .map(DocumentStrategyStepItemDto::getStrategyType)
            .filter(Objects::nonNull)
            .toList();

        List<LaMeloAgentDocumentStrategyStep> normalizedStepList = strategyService.normalizeSteps(
            basePlan, baseStepList, requestParentTypeList, requestChildTypeList, dto.getDocumentId());

        List<Integer> normalizedParentTypeList = extractPipelineTypes(normalizedStepList, DocumentStrategyPipelineTypeEnum.PARENT);
        List<Integer> normalizedChildTypeList = extractPipelineTypes(normalizedStepList, DocumentStrategyPipelineTypeEnum.CHILD);

        if (normalizedParentTypeList.isEmpty()) {
            throw new LaMeloAgentFrameException(DocumentManageCode.STRATEGY_STEP_EMPTY.getCode(), "父块流水线不能为空。");
        }
        if (normalizedChildTypeList.isEmpty()) {
            throw new LaMeloAgentFrameException(DocumentManageCode.STRATEGY_STEP_EMPTY.getCode(), "子块流水线不能为空。");
        }

        if (normalizedStepList.isEmpty()) {
            throw new LaMeloAgentFrameException(DocumentManageCode.STRATEGY_STEP_EMPTY.getCode(),
                DocumentManageCode.STRATEGY_STEP_EMPTY.getMsg());
        }

        List<Integer> baseParentTypeList = extractPipelineTypes(baseStepList, DocumentStrategyPipelineTypeEnum.PARENT);
        List<Integer> baseChildTypeList = extractPipelineTypes(baseStepList, DocumentStrategyPipelineTypeEnum.CHILD);
        List<Integer> requestDistinctParentTypeList = new LinkedHashSet<>(requestParentTypeList).stream().toList();
        List<Integer> requestDistinctChildTypeList = new LinkedHashSet<>(requestChildTypeList).stream().toList();

        boolean normalized = !requestDistinctParentTypeList.equals(normalizedParentTypeList)
            || !requestDistinctChildTypeList.equals(normalizedChildTypeList);

        boolean changed = !baseParentTypeList.equals(normalizedParentTypeList)
            || !baseChildTypeList.equals(normalizedChildTypeList);

        Long targetPlanId;
        Integer targetPlanVersion;
        List<LaMeloAgentDocumentStrategyStep> targetStepList;

        if (!changed) {

            basePlan.setPlanStatus(DocumentPlanStatusEnum.CONFIRMED.getCode());
            basePlan.setPlanSource(basePlan.getPlanSource() == null ? DocumentPlanSourceEnum.SYSTEM_RECOMMEND.getCode() : basePlan.getPlanSource());
            basePlan.setAdjustNote(dto.getAdjustNote());
            basePlan.setConfirmUserId(dto.getOperatorId());
            basePlan.setConfirmTime(new Date());
            planMapper.updateById(basePlan);
            targetPlanId = basePlan.getId();
            targetPlanVersion = basePlan.getPlanVersion();
            targetStepList = baseStepList;
        } else {

            basePlan.setPlanStatus(DocumentPlanStatusEnum.DISCARDED.getCode());
            planMapper.updateById(basePlan);

            Long newPlanId = uidGenerator.getUid();
            Integer newPlanVersion = getNextPlanVersion(document.getId());
            LaMeloAgentDocumentStrategyPlan newPlan = new LaMeloAgentDocumentStrategyPlan();
            newPlan.setId(newPlanId);
            newPlan.setDocumentId(document.getId());
            newPlan.setPlanVersion(newPlanVersion);

            newPlan.setPlanSource(DocumentPlanSourceEnum.USER_ADJUST.getCode());
            newPlan.setPlanStatus(DocumentPlanStatusEnum.CONFIRMED.getCode());
            newPlan.setStrategyCount(normalizedStepList.size());
            newPlan.setStrategySnapshot(buildStrategySnapshot(normalizedStepList));
            newPlan.setRecommendReason(basePlan.getRecommendReason());
            newPlan.setAdjustNote(dto.getAdjustNote());
            newPlan.setConfirmUserId(dto.getOperatorId());
            newPlan.setConfirmTime(new Date());
            newPlan.setStatus(BusinessStatus.YES.getCode());
            planMapper.insert(newPlan);

            for (LaMeloAgentDocumentStrategyStep step : normalizedStepList) {
                step.setId(uidGenerator.getUid());
                step.setPlanId(newPlanId);
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }

            targetPlanId = newPlanId;
            targetPlanVersion = newPlanVersion;
            targetStepList = normalizedStepList;
        }

        document.setCurrentPlanId(targetPlanId);
        document.setStrategyStatus(DocumentStrategyStatusEnum.CONFIRMED.getCode());
        documentMapper.updateById(document);

        LaMeloAgentDocumentTask latestParseTask = getLatestTask(document.getId(), DocumentTaskTypeEnum.PARSE_ROUTE.getCode());
        if (latestParseTask != null) {

            latestParseTask.setCurrentStage(DocumentTaskStageEnum.STRATEGY_CONFIRM.getCode());
            taskMapper.updateById(latestParseTask);

            if (changed) {

                taskLogService.saveLog(latestParseTask.getId(), document.getId(),
                    DocumentTaskStageEnum.STRATEGY_CONFIRM.getCode(),
                    DocumentTaskEventTypeEnum.USER_ADJUST.getCode(),
                    DocumentLogLevelEnum.INFO.getCode(),
                    resolveOperatorType(parseOptionalLong(dto.getOperatorId())),
                    parseOptionalLong(dto.getOperatorId()),
                    "用户调整了系统推荐策略。",
                    detail("parentStrategyTypes", normalizedParentTypeList,
                        "childStrategyTypes", normalizedChildTypeList,
                        "adjustNote", dto.getAdjustNote()));
            }

            taskLogService.saveLog(latestParseTask.getId(), document.getId(),
                DocumentTaskStageEnum.STRATEGY_CONFIRM.getCode(),
                DocumentTaskEventTypeEnum.USER_CONFIRM.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                    resolveOperatorType(parseOptionalLong(dto.getOperatorId())),
                    parseOptionalLong(dto.getOperatorId()),
                    "用户已确认最终策略方案。",
                Map.of("planId", targetPlanId,
                    "parentStrategyTypes", normalizedParentTypeList,
                    "childStrategyTypes", normalizedChildTypeList));
        }

        return new DocumentStrategyConfirmVo(
            document.getId(),
            targetPlanId,
            targetPlanVersion,
            document.getStrategyStatus(),
            enumMsg(DocumentStrategyStatusEnum.getRc(document.getStrategyStatus())),
            normalized,
            toPipelineVo(DocumentStrategyPipelineTypeEnum.PARENT, targetStepList),
            toPipelineVo(DocumentStrategyPipelineTypeEnum.CHILD, targetStepList)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentIndexBuildVo buildIndex(DocumentIndexBuildDto dto) {

        LaMeloAgentDocument document = getDocumentOrThrow(dto.getDocumentId());
        if (!Objects.equals(document.getParseStatus(), DocumentParseStatusEnum.PARSE_SUCCESS.getCode())
            || !Objects.equals(document.getStrategyStatus(), DocumentStrategyStatusEnum.CONFIRMED.getCode())) {
            throw new LaMeloAgentFrameException(DocumentManageCode.DOCUMENT_STATUS_INVALID.getCode(), "当前文档尚未完成“解析成功 + 策略确认”，不能构建索引。");
        }

        if (!Objects.equals(document.getCurrentPlanId(), dto.getPlanId())) {
            throw new LaMeloAgentFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(), "当前文档的生效方案与请求方案不一致。");
        }

        long runningTaskCount = taskMapper.selectCount(new LambdaQueryWrapper<LaMeloAgentDocumentTask>()
            .eq(LaMeloAgentDocumentTask::getDocumentId, dto.getDocumentId())
            .eq(LaMeloAgentDocumentTask::getTaskType, DocumentTaskTypeEnum.BUILD_INDEX.getCode())
            .in(LaMeloAgentDocumentTask::getTaskStatus, DocumentTaskStatusEnum.NEW.getCode(), DocumentTaskStatusEnum.RUNNING.getCode())
            .eq(LaMeloAgentDocumentTask::getStatus, BusinessStatus.YES.getCode()));
        if (runningTaskCount > 0) {
            throw new LaMeloAgentFrameException(DocumentManageCode.INDEX_TASK_RUNNING.getCode(),
                DocumentManageCode.INDEX_TASK_RUNNING.getMsg());
        }

        LaMeloAgentDocumentStrategyPlan plan = planMapper.selectById(dto.getPlanId());
        if (plan == null || !Objects.equals(plan.getStatus(), BusinessStatus.YES.getCode())) {
            throw new LaMeloAgentFrameException(DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getCode(),
                DocumentManageCode.STRATEGY_PLAN_NOT_FOUND.getMsg());
        }

        Long taskId = uidGenerator.getUid();
        LaMeloAgentDocumentTask task = new LaMeloAgentDocumentTask();
        task.setId(taskId);
        task.setDocumentId(document.getId());
        task.setPlanId(dto.getPlanId());
        task.setTaskType(DocumentTaskTypeEnum.BUILD_INDEX.getCode());
        task.setTaskStatus(DocumentTaskStatusEnum.NEW.getCode());
        task.setCurrentStage(DocumentTaskStageEnum.CHUNK_EXECUTE.getCode());
        Long operatorId = parseOptionalLong(dto.getOperatorId());
        task.setTriggerSource(resolveTriggerSource(operatorId));
        task.setStrategySnapshot(plan.getStrategySnapshot());
        task.setRetryCount(0);
        task.setStatus(BusinessStatus.YES.getCode());
        taskMapper.insert(task);

        document.setIndexStatus(DocumentIndexStatusEnum.BUILDING.getCode());
        documentMapper.updateById(document);

        taskLogService.saveLog(taskId, document.getId(),
            DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
            DocumentTaskEventTypeEnum.START.getCode(),
            DocumentLogLevelEnum.INFO.getCode(),
            resolveOperatorType(operatorId),
            operatorId,
            "索引构建任务已创建，等待异步执行。",
            Map.of("planId", dto.getPlanId(), "strategySnapshot", plan.getStrategySnapshot()));

        DocumentIndexBuildMessage message = new DocumentIndexBuildMessage(document.getId(), taskId, dto.getPlanId());
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                taskPublisher.sendIndexBuild(message);
            }
        });

        return new DocumentIndexBuildVo(
            document.getId(),
            taskId,
            task.getTaskType(),
            enumMsg(DocumentTaskTypeEnum.getRc(task.getTaskType())),
            task.getTaskStatus(),
            enumMsg(DocumentTaskStatusEnum.getRc(task.getTaskStatus())),
            document.getIndexStatus(),
            enumMsg(DocumentIndexStatusEnum.getRc(document.getIndexStatus()))
        );
    }

    @Override
    public DocumentTaskLogQueryVo queryTaskLogs(DocumentTaskLogQueryDto dto) {

        LaMeloAgentDocumentTask task = taskMapper.selectById(dto.getTaskId());
        if (task == null || !Objects.equals(task.getStatus(), BusinessStatus.YES.getCode())) {
            throw new LaMeloAgentFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "任务不存在。");
        }

        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 20 : dto.getPageSize();
        Page<LaMeloAgentDocumentTaskLog> page = new Page<>(pageNo, pageSize);

        IPage<LaMeloAgentDocumentTaskLog> resultPage = taskLogMapper.selectPage(page,
            new LambdaQueryWrapper<LaMeloAgentDocumentTaskLog>()
                .eq(LaMeloAgentDocumentTaskLog::getTaskId, dto.getTaskId())
                .eq(LaMeloAgentDocumentTaskLog::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(LaMeloAgentDocumentTaskLog::getCreateTime, LaMeloAgentDocumentTaskLog::getId));

        List<DocumentTaskLogVo> logVoList = resultPage.getRecords().stream()
            .map(this::toTaskLogVo)
            .toList();

        return new DocumentTaskLogQueryVo(
            task.getId(),
            task.getDocumentId(),
            task.getTaskType(),
            enumMsg(DocumentTaskTypeEnum.getRc(task.getTaskType())),
            task.getTaskStatus(),
            enumMsg(DocumentTaskStatusEnum.getRc(task.getTaskStatus())),
            task.getCurrentStage(),
            enumMsg(DocumentTaskStageEnum.getRc(task.getCurrentStage())),
            task.getStartTime(),
            task.getFinishTime(),
            task.getCostMillis(),
            task.getErrorCode(),
            task.getErrorMsg(),
            resultPage.getTotal(),
            logVoList
        );
    }

    @Override
    public DocumentChunkQueryVo queryDocumentChunks(DocumentChunkQueryDto dto) {
        LaMeloAgentDocument document = getDocumentOrThrow(dto.getDocumentId());
        int pageNo = dto.getPageNo() == null || dto.getPageNo() <= 0 ? 1 : dto.getPageNo();
        int pageSize = dto.getPageSize() == null || dto.getPageSize() <= 0 ? 20 : dto.getPageSize();

        Long effectiveTaskId = resolveChunkTaskId(document, dto.getTaskId());
        if (effectiveTaskId == null) {
            return new DocumentChunkQueryVo(document.getId(), null, document.getCurrentPlanId(), pageNo, pageSize, 0L, List.of());
        }

        LaMeloAgentDocumentTask task = taskMapper.selectById(effectiveTaskId);
        if (task == null
            || !Objects.equals(task.getStatus(), BusinessStatus.YES.getCode())
            || !Objects.equals(task.getDocumentId(), document.getId())) {
            throw new LaMeloAgentFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "切块任务不存在。");
        }

        Page<LaMeloAgentDocumentChunk> page = new Page<>(pageNo, pageSize);
        IPage<LaMeloAgentDocumentChunk> resultPage = chunkMapper.selectPage(page,
            new LambdaQueryWrapper<LaMeloAgentDocumentChunk>()
                .eq(LaMeloAgentDocumentChunk::getDocumentId, document.getId())
                .eq(LaMeloAgentDocumentChunk::getTaskId, effectiveTaskId)
                .eq(LaMeloAgentDocumentChunk::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(LaMeloAgentDocumentChunk::getChunkNo, LaMeloAgentDocumentChunk::getId));

        Map<Long, LaMeloAgentDocumentParentBlock> parentBlockMap = listParentBlockMap(
            resultPage.getRecords().stream()
                .map(LaMeloAgentDocumentChunk::getParentBlockId)
                .filter(Objects::nonNull)
                .toList()
        );

        List<DocumentChunkItemVo> records = resultPage.getRecords().stream()
            .map(chunk -> toDocumentChunkItemVo(chunk, parentBlockMap.get(chunk.getParentBlockId())))
            .toList();

        return new DocumentChunkQueryVo(
            document.getId(),
            effectiveTaskId,
            task.getPlanId(),
            pageNo,
            pageSize,
            resultPage.getTotal(),
            records
        );
    }

    @Override
    public DocumentChunkDetailVo queryDocumentChunkDetail(DocumentChunkDetailQueryDto dto) {
        LaMeloAgentDocument document = getDocumentOrThrow(dto.getDocumentId());
        Long effectiveTaskId = resolveChunkTaskId(document, dto.getTaskId());
        if (effectiveTaskId == null) {
            throw new LaMeloAgentFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "当前文档还没有可查看的 chunk 详情。");
        }

        LaMeloAgentDocumentTask task = taskMapper.selectById(effectiveTaskId);
        if (task == null
            || !Objects.equals(task.getStatus(), BusinessStatus.YES.getCode())
            || !Objects.equals(task.getDocumentId(), document.getId())) {
            throw new LaMeloAgentFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "切块任务不存在。");
        }

        LaMeloAgentDocumentChunk chunk = chunkMapper.selectOne(new LambdaQueryWrapper<LaMeloAgentDocumentChunk>()
            .eq(LaMeloAgentDocumentChunk::getId, dto.getChunkId())
            .eq(LaMeloAgentDocumentChunk::getDocumentId, document.getId())
            .eq(LaMeloAgentDocumentChunk::getTaskId, effectiveTaskId)
            .eq(LaMeloAgentDocumentChunk::getStatus, BusinessStatus.YES.getCode())
            .last("limit 1"));
        if (chunk == null) {
            throw new LaMeloAgentFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(), "chunk 详情不存在。");
        }

        LaMeloAgentDocumentParentBlock parentBlock = chunk.getParentBlockId() == null
            ? null
            : parentBlockMapper.selectOne(new LambdaQueryWrapper<LaMeloAgentDocumentParentBlock>()
                .eq(LaMeloAgentDocumentParentBlock::getId, chunk.getParentBlockId())
                .eq(LaMeloAgentDocumentParentBlock::getDocumentId, document.getId())
                .eq(LaMeloAgentDocumentParentBlock::getTaskId, effectiveTaskId)
                .eq(LaMeloAgentDocumentParentBlock::getStatus, BusinessStatus.YES.getCode())
                .last("limit 1"));

        List<LaMeloAgentDocumentChunk> siblingChunkList = chunk.getParentBlockId() == null
            ? List.of(chunk)
            : chunkMapper.selectList(new LambdaQueryWrapper<LaMeloAgentDocumentChunk>()
                .eq(LaMeloAgentDocumentChunk::getDocumentId, document.getId())
                .eq(LaMeloAgentDocumentChunk::getTaskId, effectiveTaskId)
                .eq(LaMeloAgentDocumentChunk::getParentBlockId, chunk.getParentBlockId())
                .eq(LaMeloAgentDocumentChunk::getStatus, BusinessStatus.YES.getCode())
                .orderByAsc(LaMeloAgentDocumentChunk::getChunkNo, LaMeloAgentDocumentChunk::getId));

        return new DocumentChunkDetailVo(
            document.getId(),
            effectiveTaskId,
            task.getPlanId(),
            toDocumentChunkItemVo(chunk, parentBlock),
            toDocumentParentBlockItemVo(parentBlock),
            siblingChunkList.stream()
                .map(item -> toDocumentChunkItemVo(item, parentBlock))
                .toList()
        );
    }

    private LaMeloAgentDocument getDocumentOrThrow(Long documentId) {

        LaMeloAgentDocument document = documentMapper.selectById(documentId);
        if (document == null || !Objects.equals(document.getStatus(), BusinessStatus.YES.getCode())) {
            throw new LaMeloAgentFrameException(DocumentManageCode.DOCUMENT_NOT_FOUND.getCode(),
                DocumentManageCode.DOCUMENT_NOT_FOUND.getMsg());
        }
        return document;
    }

    private List<LaMeloAgentDocumentStrategyStep> listStepByPlanId(Long planId) {
        List<LaMeloAgentDocumentStrategyStep> stepList = stepMapper.selectList(new LambdaQueryWrapper<LaMeloAgentDocumentStrategyStep>()
            .eq(LaMeloAgentDocumentStrategyStep::getPlanId, planId)
            .eq(LaMeloAgentDocumentStrategyStep::getStatus, BusinessStatus.YES.getCode()));
        return stepList.stream()
            .sorted(Comparator
                .comparingInt((LaMeloAgentDocumentStrategyStep step) -> pipelineOrder(step.getPipelineType()))
                .thenComparing(LaMeloAgentDocumentStrategyStep::getStepNo)
                .thenComparing(LaMeloAgentDocumentStrategyStep::getId))
            .toList();
    }

    private Integer getNextPlanVersion(Long documentId) {

        LaMeloAgentDocumentStrategyPlan latestPlan = planMapper.selectOne(new LambdaQueryWrapper<LaMeloAgentDocumentStrategyPlan>()
            .eq(LaMeloAgentDocumentStrategyPlan::getDocumentId, documentId)
            .eq(LaMeloAgentDocumentStrategyPlan::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(LaMeloAgentDocumentStrategyPlan::getPlanVersion)
            .last("limit 1"));
        return latestPlan == null ? 1 : latestPlan.getPlanVersion() + 1;
    }

    private LaMeloAgentDocumentTask getLatestTask(Long documentId, Integer taskType) {

        return taskMapper.selectOne(new LambdaQueryWrapper<LaMeloAgentDocumentTask>()
            .eq(LaMeloAgentDocumentTask::getDocumentId, documentId)
            .eq(LaMeloAgentDocumentTask::getTaskType, taskType)
            .eq(LaMeloAgentDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(LaMeloAgentDocumentTask::getId)
            .last("limit 1"));
    }

    private LaMeloAgentDocumentTask getLatestTask(Long documentId) {
        return taskMapper.selectOne(new LambdaQueryWrapper<LaMeloAgentDocumentTask>()
            .eq(LaMeloAgentDocumentTask::getDocumentId, documentId)
            .eq(LaMeloAgentDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(LaMeloAgentDocumentTask::getId)
            .last("limit 1"));
    }

    private Map<Long, LaMeloAgentDocumentTask> getLatestTaskMap(List<LaMeloAgentDocument> documentList) {
        if (documentList == null || documentList.isEmpty()) {
            return Map.of();
        }

        Set<Long> documentIdSet = documentList.stream()
            .map(LaMeloAgentDocument::getId)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
        if (documentIdSet.isEmpty()) {
            return Map.of();
        }

        List<LaMeloAgentDocumentTask> taskList = taskMapper.selectList(new LambdaQueryWrapper<LaMeloAgentDocumentTask>()
            .in(LaMeloAgentDocumentTask::getDocumentId, documentIdSet)
            .eq(LaMeloAgentDocumentTask::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(LaMeloAgentDocumentTask::getId));

        Map<Long, LaMeloAgentDocumentTask> latestTaskMap = new LinkedHashMap<>();
        for (LaMeloAgentDocumentTask task : taskList) {
            latestTaskMap.putIfAbsent(task.getDocumentId(), task);
        }
        return latestTaskMap;
    }

    private Long resolveChunkTaskId(LaMeloAgentDocument document, Long requestedTaskId) {
        if (requestedTaskId != null) {
            return requestedTaskId;
        }
        if (document.getLastIndexTaskId() != null) {
            return document.getLastIndexTaskId();
        }
        LaMeloAgentDocumentTask latestBuildTask = getLatestTask(document.getId(), DocumentTaskTypeEnum.BUILD_INDEX.getCode());
        return latestBuildTask == null ? null : latestBuildTask.getId();
    }

    private DocumentListItemVo toDocumentListItemVo(LaMeloAgentDocument document, LaMeloAgentDocumentTask latestTask) {
        return new DocumentListItemVo(
            document.getId(),
            document.getDocumentName(),
            document.getOriginalFileName(),
            document.getFileType(),
            enumMsg(DocumentFileTypeEnum.getRc(document.getFileType())),
            document.getFileSize(),
            document.getCharCount(),
            document.getTokenCount(),
            document.getParseStatus(),
            enumMsg(DocumentParseStatusEnum.getRc(document.getParseStatus())),
            document.getStrategyStatus(),
            enumMsg(DocumentStrategyStatusEnum.getRc(document.getStrategyStatus())),
            document.getIndexStatus(),
            enumMsg(DocumentIndexStatusEnum.getRc(document.getIndexStatus())),
            document.getParseErrorMsg(),
            document.getKnowledgeScopeCode(),
            document.getKnowledgeScopeName(),
            document.getBusinessCategory(),
            document.getDocumentTags(),
            document.getCurrentPlanId(),
            document.getLastIndexTaskId(),
            latestTask == null ? null : latestTask.getId(),
            latestTask == null ? null : latestTask.getTaskType(),
            latestTask == null ? "" : enumMsg(DocumentTaskTypeEnum.getRc(latestTask.getTaskType())),
            latestTask == null ? null : latestTask.getTaskStatus(),
            latestTask == null ? "" : enumMsg(DocumentTaskStatusEnum.getRc(latestTask.getTaskStatus())),
            document.getCreateTime(),
            document.getEditTime()
        );
    }

    private DocumentChunkItemVo toDocumentChunkItemVo(LaMeloAgentDocumentChunk chunk,
                                                     LaMeloAgentDocumentParentBlock parentBlock) {
        return new DocumentChunkItemVo(
            chunk.getId(),
            chunk.getParentBlockId(),
            parentBlock == null ? null : parentBlock.getParentNo(),
            parentBlock == null ? null : parentBlock.getChildCount(),
            parentBlock == null ? null : parentBlock.getStartChunkNo(),
            parentBlock == null ? null : parentBlock.getEndChunkNo(),
            chunk.getChunkNo(),
            chunk.getSectionPath(),
            chunk.getSourceType(),
            enumMsg(DocumentChunkSourceTypeEnum.getRc(chunk.getSourceType())),
            chunk.getCharCount(),
            chunk.getTokenCount(),
            chunk.getVectorStatus(),
            enumMsg(DocumentVectorStatusEnum.getRc(chunk.getVectorStatus())),
            chunk.getChunkText()
        );
    }

    private DocumentParentBlockItemVo toDocumentParentBlockItemVo(LaMeloAgentDocumentParentBlock parentBlock) {
        if (parentBlock == null) {
            return null;
        }
        return new DocumentParentBlockItemVo(
            parentBlock.getId(),
            parentBlock.getParentNo(),
            parentBlock.getSectionPath(),
            parentBlock.getSourceType(),
            enumMsg(DocumentChunkSourceTypeEnum.getRc(parentBlock.getSourceType())),
            parentBlock.getCharCount(),
            parentBlock.getTokenCount(),
            parentBlock.getChildCount(),
            parentBlock.getStartChunkNo(),
            parentBlock.getEndChunkNo(),
            parentBlock.getParentText()
        );
    }

    private Map<Long, LaMeloAgentDocumentParentBlock> listParentBlockMap(List<Long> parentBlockIds) {
        if (parentBlockIds == null || parentBlockIds.isEmpty()) {
            return Map.of();
        }
        return parentBlockMapper.selectList(new LambdaQueryWrapper<LaMeloAgentDocumentParentBlock>()
                .in(LaMeloAgentDocumentParentBlock::getId, parentBlockIds)
                .eq(LaMeloAgentDocumentParentBlock::getStatus, BusinessStatus.YES.getCode()))
            .stream()
            .collect(Collectors.toMap(
                LaMeloAgentDocumentParentBlock::getId,
                item -> item,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private DocumentStrategyPlanVo toPlanVo(LaMeloAgentDocumentStrategyPlan plan, List<LaMeloAgentDocumentStrategyStep> stepList) {
        return new DocumentStrategyPlanVo(
            plan.getId(),
            plan.getPlanVersion(),
            plan.getPlanSource(),
            enumMsg(DocumentPlanSourceEnum.getRc(plan.getPlanSource())),
            plan.getPlanStatus(),
            enumMsg(DocumentPlanStatusEnum.getRc(plan.getPlanStatus())),
            plan.getStrategySnapshot(),
            plan.getRecommendReason(),
            toPipelineVo(DocumentStrategyPipelineTypeEnum.PARENT, stepList),
            toPipelineVo(DocumentStrategyPipelineTypeEnum.CHILD, stepList)
        );
    }

    private List<DocumentStrategyStepVo> toStepVoList(List<LaMeloAgentDocumentStrategyStep> stepList) {

        return stepList.stream()
            .sorted(Comparator
                .comparingInt((LaMeloAgentDocumentStrategyStep step) -> pipelineOrder(step.getPipelineType()))
                .thenComparing(LaMeloAgentDocumentStrategyStep::getStepNo)
                .thenComparing(LaMeloAgentDocumentStrategyStep::getId))
            .map(step -> new DocumentStrategyStepVo(
                step.getStepNo(),
                step.getPipelineType(),
                enumMsg(DocumentStrategyPipelineTypeEnum.getRc(step.getPipelineType())),
                step.getStrategyType(),
                enumMsg(DocumentStrategyTypeEnum.getRc(step.getStrategyType())),
                step.getStrategyRole(),
                enumMsg(DocumentStrategyRoleEnum.getRc(step.getStrategyRole())),
                step.getSourceType(),
                enumMsg(DocumentStrategySourceTypeEnum.getRc(step.getSourceType())),
                step.getExecuteStatus(),
                enumMsg(DocumentStrategyExecuteStatusEnum.getRc(step.getExecuteStatus())),
                step.getRecommendReason()
            ))
            .toList();
    }

    private DocumentStrategyPipelineVo toPipelineVo(DocumentStrategyPipelineTypeEnum pipelineType,
                                                    List<LaMeloAgentDocumentStrategyStep> stepList) {
        List<LaMeloAgentDocumentStrategyStep> pipelineSteps = stepList.stream()
            .filter(step -> pipelineType.getCode().equalsIgnoreCase(
                StrUtil.blankToDefault(step.getPipelineType(), DocumentStrategyPipelineTypeEnum.CHILD.getCode())
            ))
            .sorted(Comparator.comparingInt(LaMeloAgentDocumentStrategyStep::getStepNo))
            .toList();
        return new DocumentStrategyPipelineVo(
            pipelineType.getCode(),
            pipelineType.getMsg(),
            pipelineSteps.stream().map(step -> String.valueOf(step.getStrategyType())).collect(Collectors.joining(",")),
            toStepVoList(pipelineSteps)
        );
    }

    private List<Integer> extractPipelineTypes(List<LaMeloAgentDocumentStrategyStep> stepList,
                                               DocumentStrategyPipelineTypeEnum pipelineType) {
        return stepList.stream()
            .filter(step -> pipelineType.getCode().equalsIgnoreCase(
                StrUtil.blankToDefault(step.getPipelineType(), DocumentStrategyPipelineTypeEnum.CHILD.getCode())
            ))
            .sorted(Comparator.comparingInt(LaMeloAgentDocumentStrategyStep::getStepNo))
            .map(LaMeloAgentDocumentStrategyStep::getStrategyType)
            .toList();
    }

    private String buildStrategySnapshot(List<LaMeloAgentDocumentStrategyStep> stepList) {
        return "PARENT:" + toPipelineVo(DocumentStrategyPipelineTypeEnum.PARENT, stepList).getStrategySnapshot()
            + ";CHILD:" + toPipelineVo(DocumentStrategyPipelineTypeEnum.CHILD, stepList).getStrategySnapshot();
    }

    private int pipelineOrder(String pipelineType) {
        return DocumentStrategyPipelineTypeEnum.PARENT.getCode().equalsIgnoreCase(
            StrUtil.blankToDefault(pipelineType, "")
        ) ? 0 : 1;
    }

    private DocumentTaskLogVo toTaskLogVo(LaMeloAgentDocumentTaskLog logRecord) {
        return new DocumentTaskLogVo(
            logRecord.getId(),
            logRecord.getStageType(),
            enumMsg(DocumentTaskStageEnum.getRc(logRecord.getStageType())),
            logRecord.getEventType(),
            enumMsg(DocumentTaskEventTypeEnum.getRc(logRecord.getEventType())),
            logRecord.getLogLevel(),
            enumMsg(DocumentLogLevelEnum.getRc(logRecord.getLogLevel())),
            logRecord.getContent(),
            logRecord.getDetailJson(),
            logRecord.getCreateTime()
        );
    }

    private Integer resolveOperatorType(Long operatorId) {

        return operatorId == null ? DocumentOperatorTypeEnum.SYSTEM.getCode() : DocumentOperatorTypeEnum.USER.getCode();
    }

    private Integer resolveTriggerSource(Long operatorId) {

        return operatorId == null ? DocumentTriggerSourceEnum.SYSTEM.getCode() : DocumentTriggerSourceEnum.USER.getCode();
    }

    private Long parseOptionalLong(String rawValue) {
        if (StrUtil.isBlank(rawValue)) {
            return null;
        }
        try {
            Long value = Long.valueOf(rawValue.trim());
            return value > 0 ? value : null;
        }
        catch (NumberFormatException exception) {
            return null;
        }
    }

    private Long parseOptionalLong(Long rawValue) {
        return rawValue == null || rawValue <= 0 ? null : rawValue;
    }

    private Long parseRequiredLong(String rawValue, String fieldName) {
        if (StrUtil.isBlank(rawValue)) {
            throw new LaMeloAgentFrameException(BaseCode.PARAMETER_ERROR.getCode(), fieldName + "不能为空。");
        }

        try {

            Long value = Long.valueOf(rawValue.trim());
            if (value <= 0) {
                throw new NumberFormatException("id must be positive");
            }
            return value;
        }
        catch (NumberFormatException exception) {
            throw new LaMeloAgentFrameException(BaseCode.PARAMETER_ERROR.getCode(), fieldName + "格式不正确。");
        }
    }

    private String enumMsg(Object enumObject) {
        if (enumObject == null) {
            return "";
        }
        if (enumObject instanceof DocumentParseStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentFileTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentIndexStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentPlanSourceEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentPlanStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyRoleEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategySourceTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentStrategyExecuteStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskStatusEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskStageEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentTaskEventTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentLogLevelEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentChunkSourceTypeEnum value) {
            return value.getMsg();
        }
        if (enumObject instanceof DocumentVectorStatusEnum value) {
            return value.getMsg();
        }
        return "";
    }

    private byte[] getFileBytes(MultipartFile file) {
        try {

            return file.getBytes();
        }
        catch (IOException exception) {
            throw new LaMeloAgentFrameException(DocumentManageCode.DOCUMENT_STORAGE_FAILED.getCode(),
                "读取上传文件内容失败: " + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> detailMap = new LinkedHashMap<>();

        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            detailMap.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return detailMap;
    }
}
