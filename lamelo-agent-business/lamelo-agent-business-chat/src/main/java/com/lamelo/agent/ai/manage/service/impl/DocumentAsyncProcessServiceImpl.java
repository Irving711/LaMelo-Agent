package com.lamelo.agent.ai.manage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baidu.fsg.uid.UidGenerator;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import jakarta.annotation.Resource;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocument;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentChunk;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentParentBlock;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStrategyPlan;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStrategyStep;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStructureNode;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentTask;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentChunkMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentParentBlockMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentStrategyPlanMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentStrategyStepMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentTaskMapper;
import com.lamelo.agent.ai.manage.service.DocumentAsyncProcessService;
import com.lamelo.agent.ai.manage.service.DocumentNavigationIndexService;
import com.lamelo.agent.ai.manage.service.DocumentParserService;
import com.lamelo.agent.ai.manage.service.DocumentProfileService;
import com.lamelo.agent.ai.manage.service.DocumentStorageService;
import com.lamelo.agent.ai.manage.service.DocumentStrategyService;
import com.lamelo.agent.ai.manage.service.DocumentStructureGraphProjectionService;
import com.lamelo.agent.ai.manage.service.DocumentStructureNodeService;
import com.lamelo.agent.ai.manage.service.DocumentTaskLogService;
import com.lamelo.agent.ai.manage.service.DocumentVectorGateway;
import com.lamelo.agent.ai.manage.service.keyword.DocumentKeywordSearchGateway;
import com.lamelo.agent.ai.manage.support.ChunkCandidate;
import com.lamelo.agent.ai.manage.support.DocumentAnalysisResult;
import com.lamelo.agent.ai.manage.support.DocumentStrategyPlanDraft;
import com.lamelo.agent.ai.manage.support.DocumentStrategyStepDraft;
import com.lamelo.agent.ai.manage.support.ParentBlockCandidate;
import com.lamelo.agent.enums.BusinessStatus;
import com.lamelo.agent.enums.DocumentChunkSourceTypeEnum;
import com.lamelo.agent.enums.DocumentFileTypeEnum;
import com.lamelo.agent.enums.DocumentIndexStatusEnum;
import com.lamelo.agent.enums.DocumentLogLevelEnum;
import com.lamelo.agent.enums.DocumentOperatorTypeEnum;
import com.lamelo.agent.enums.DocumentParseStatusEnum;
import com.lamelo.agent.enums.DocumentPlanSourceEnum;
import com.lamelo.agent.enums.DocumentPlanStatusEnum;
import com.lamelo.agent.enums.DocumentStrategyExecuteStatusEnum;
import com.lamelo.agent.enums.DocumentStrategyPipelineTypeEnum;
import com.lamelo.agent.enums.DocumentStrategyStatusEnum;
import com.lamelo.agent.enums.DocumentTaskEventTypeEnum;
import com.lamelo.agent.enums.DocumentTaskStageEnum;
import com.lamelo.agent.enums.DocumentTaskStatusEnum;
import com.lamelo.agent.enums.DocumentVectorStatusEnum;
import com.lamelo.agent.enums.DocumentVectorStoreTypeEnum;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务实现层
 * @author: 阿星不是程序员
 **/

@Slf4j
@AllArgsConstructor
@Service
public class DocumentAsyncProcessServiceImpl implements DocumentAsyncProcessService {

    private final LaMeloAgentDocumentMapper documentMapper;

    private final LaMeloAgentDocumentStrategyPlanMapper planMapper;

    private final LaMeloAgentDocumentStrategyStepMapper stepMapper;

    private final LaMeloAgentDocumentTaskMapper taskMapper;

    private final LaMeloAgentDocumentParentBlockMapper parentBlockMapper;

    private final LaMeloAgentDocumentChunkMapper chunkMapper;

    private final DocumentStorageService storageService;

    private final DocumentParserService parserService;

    private final DocumentStrategyService strategyService;

    private final DocumentStructureNodeService structureNodeService;

    private final DocumentTaskLogService taskLogService;

    private final DocumentVectorGateway vectorGateway;

    private final ObjectProvider<DocumentKeywordSearchGateway> keywordSearchGatewayProvider;

    private final ObjectProvider<DocumentNavigationIndexService> navigationIndexServiceProvider;

    private final ObjectProvider<DocumentStructureGraphProjectionService> graphProjectionServiceProvider;

    private final DocumentProfileService documentProfileService;

    @Resource
    private UidGenerator uidGenerator;

    @Override

    public void handleParseRoute(Long documentId, Long taskId) {
        handleParseRoute(documentId, taskId, null);
    }

    @Override
    public void handleParseRoute(Long documentId, Long taskId, String leaseOwner) {

        LaMeloAgentDocument document = documentMapper.selectById(documentId);
        LaMeloAgentDocumentTask task = taskMapper.selectById(taskId);
        if (document == null || task == null) {
            log.warn("解析任务对应的文档或任务不存在，documentId={}, taskId={}", documentId, taskId);
            return;
        }

        Date startTime = new Date();
        try {

            task.setTaskStatus(DocumentTaskStatusEnum.RUNNING.getCode());
            task.setCurrentStage(DocumentTaskStageEnum.CONTENT_PARSE.getCode());
            task.setStartTime(startTime);
            updateTask(task, leaseOwner);

            assertLease(taskId, leaseOwner);
            document.setParseStatus(DocumentParseStatusEnum.PARSING.getCode());
            documentMapper.updateById(document);

            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始解析文档内容。",
                Map.of("objectName", document.getObjectName()));

            byte[] fileBytes = storageService.downloadObject(document.getObjectName());
            assertLease(taskId, leaseOwner);
            DocumentAnalysisResult analysisResult = parserService.parse(fileBytes, document.getOriginalFileName(),
                document.getMimeType(), DocumentFileTypeEnum.getRc(document.getFileType()));

            assertLease(taskId, leaseOwner);
            String parseTextPath = storageService.uploadParsedText(documentId, analysisResult.getParsedText());

            assertLease(taskId, leaseOwner);
            List<LaMeloAgentDocumentStructureNode> structureNodes = structureNodeService.replaceDocumentNodes(
                documentId,
                taskId,
                analysisResult.getStructureNodes()
            );
            int structureNodeCount = structureNodes.size();
            syncNavigationArtifacts(documentId, taskId, structureNodes, leaseOwner);
            assertLease(taskId, leaseOwner);
            documentProfileService.generateProfile(documentId, analysisResult, structureNodes);

            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "文档解析完成。",
                Map.of(
                    "charCount", analysisResult.getCharCount(),
                    "tokenCount", analysisResult.getTokenCount(),
                    "structureLevel", analysisResult.getStructureLevel(),
                    "contentQualityLevel", analysisResult.getContentQualityLevel(),
                    "structureNodeCount", structureNodeCount
                ));

            task.setCurrentStage(DocumentTaskStageEnum.STRATEGY_ROUTE.getCode());
            updateTask(task, leaseOwner);

            assertLease(taskId, leaseOwner);
            DocumentStrategyPlanDraft planDraft = strategyService.recommendStrategy(document, analysisResult);
            Long planId = uidGenerator.getUid();
            int planVersion = getNextPlanVersion(documentId);

            LaMeloAgentDocumentStrategyPlan plan = new LaMeloAgentDocumentStrategyPlan();
            plan.setId(planId);
            plan.setDocumentId(documentId);
            plan.setPlanVersion(planVersion);
            plan.setPlanSource(DocumentPlanSourceEnum.SYSTEM_RECOMMEND.getCode());
            plan.setPlanStatus(DocumentPlanStatusEnum.WAIT_CONFIRM.getCode());
            plan.setStrategyCount(planDraft.getParentSteps().size() + planDraft.getChildSteps().size());
            plan.setStrategySnapshot(planDraft.getStrategySnapshot());
            plan.setRecommendReason(planDraft.getRecommendReason());
            plan.setStatus(BusinessStatus.YES.getCode());
            assertLease(taskId, leaseOwner);
            planMapper.insert(plan);

            for (int index = 0; index < planDraft.getParentSteps().size(); index++) {
                assertLease(taskId, leaseOwner);
                DocumentStrategyStepDraft draft = planDraft.getParentSteps().get(index);
                LaMeloAgentDocumentStrategyStep step = new LaMeloAgentDocumentStrategyStep();
                step.setId(uidGenerator.getUid());
                step.setPlanId(planId);
                step.setDocumentId(documentId);
                step.setPipelineType(draft.getPipelineType());
                step.setStepNo(index + 1);
                step.setStrategyType(draft.getStrategyType());
                step.setStrategyRole(draft.getStrategyRole());
                step.setSourceType(draft.getSourceType());
                step.setExecuteStatus(DocumentStrategyExecuteStatusEnum.WAIT_EXECUTE.getCode());
                step.setRecommendReason(draft.getRecommendReason());
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }
            for (int index = 0; index < planDraft.getChildSteps().size(); index++) {
                assertLease(taskId, leaseOwner);
                DocumentStrategyStepDraft draft = planDraft.getChildSteps().get(index);
                LaMeloAgentDocumentStrategyStep step = new LaMeloAgentDocumentStrategyStep();
                step.setId(uidGenerator.getUid());
                step.setPlanId(planId);
                step.setDocumentId(documentId);
                step.setPipelineType(draft.getPipelineType());
                step.setStepNo(index + 1);
                step.setStrategyType(draft.getStrategyType());
                step.setStrategyRole(draft.getStrategyRole());
                step.setSourceType(draft.getSourceType());
                step.setExecuteStatus(DocumentStrategyExecuteStatusEnum.WAIT_EXECUTE.getCode());
                step.setRecommendReason(draft.getRecommendReason());
                step.setStatus(BusinessStatus.YES.getCode());
                stepMapper.insert(step);
            }

            document.setParseStatus(DocumentParseStatusEnum.PARSE_SUCCESS.getCode());
            document.setStrategyStatus(DocumentStrategyStatusEnum.RECOMMENDED.getCode());
            document.setCharCount(analysisResult.getCharCount());
            document.setTokenCount(analysisResult.getTokenCount());
            document.setStructureLevel(analysisResult.getStructureLevel());
            document.setContentQualityLevel(analysisResult.getContentQualityLevel());
            document.setParseTextPath(parseTextPath);
            document.setParseErrorMsg(null);
            document.setCurrentPlanId(planId);
            document.setLastParseTaskId(taskId);
            document.setStructureNodeCount(structureNodeCount);
            assertLease(taskId, leaseOwner);
            documentMapper.updateById(document);

            finishTaskSuccess(task, DocumentTaskStageEnum.STRATEGY_ROUTE.getCode(), startTime, leaseOwner);
            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.STRATEGY_ROUTE.getCode(),
                DocumentTaskEventTypeEnum.RECOMMEND_STRATEGY.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "系统已生成推荐策略。",
                detail("planId", planId,
                    "strategySnapshot", planDraft.getStrategySnapshot(),
                    "parentStepCount", planDraft.getParentSteps().size(),
                    "childStepCount", planDraft.getChildSteps().size(),
                    "structureNodeCount", structureNodeCount,
                    "recommendReason", planDraft.getRecommendReason()));
        }
        catch (Exception exception) {
            if (exception instanceof LeaseLostException) throw (LeaseLostException) exception;
            assertLease(taskId, leaseOwner);
            log.error("异步解析文档失败，documentId={}, taskId={}", documentId, taskId, exception);

            document.setParseStatus(DocumentParseStatusEnum.PARSE_FAILED.getCode());
            document.setParseErrorMsg(exception.getMessage());
            documentMapper.updateById(document);

            failTask(task, startTime, exception, DocumentTaskStageEnum.CONTENT_PARSE.getCode(), leaseOwner);
            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CONTENT_PARSE.getCode(),
                DocumentTaskEventTypeEnum.FAILED.getCode(),
                DocumentLogLevelEnum.ERROR.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "文档解析失败。",
                detail("error", exception.getMessage()));
        }
    }

    @Override
    public void handleIndexBuild(Long documentId, Long taskId, Long planId) {
        handleIndexBuild(documentId, taskId, planId, null);
    }

    @Override
    public void handleIndexBuild(Long documentId, Long taskId, Long planId, String leaseOwner) {

        LaMeloAgentDocument document = documentMapper.selectById(documentId);
        LaMeloAgentDocumentTask task = taskMapper.selectById(taskId);
        LaMeloAgentDocumentStrategyPlan plan = planMapper.selectById(planId);
        if (document == null || task == null || plan == null) {
            log.warn("索引任务对应的数据不存在，documentId={}, taskId={}, planId={}", documentId, taskId, planId);
            return;
        }

        Date startTime = new Date();

        List<LaMeloAgentDocumentStrategyStep> stepList = listSteps(planId);
        try {

            task.setTaskStatus(DocumentTaskStatusEnum.RUNNING.getCode());
            task.setCurrentStage(DocumentTaskStageEnum.CHUNK_EXECUTE.getCode());
            task.setStartTime(startTime);
            updateTask(task, leaseOwner);

            assertLease(taskId, leaseOwner);
            document.setIndexStatus(DocumentIndexStatusEnum.BUILDING.getCode());
            documentMapper.updateById(document);

            assertLease(taskId, leaseOwner);
            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTING.getCode());

            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始执行切块流水线。",
                Map.of("strategySnapshot", plan.getStrategySnapshot()));

            assertLease(taskId, leaseOwner);
            String parsedText = storageService.downloadText(document.getParseTextPath());

            assertLease(taskId, leaseOwner);
            List<ParentBlockCandidate> parentBlockCandidateList = strategyService.buildParentBlocks(document, plan, stepList, parsedText);

            assertLease(taskId, leaseOwner);
            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTE_SUCCESS.getCode());

            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_EXECUTE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "切块执行完成。",
                Map.of(
                    "parentCount", parentBlockCandidateList.size(),
                    "childCount", countChildCandidates(parentBlockCandidateList)
                ));

            task.setCurrentStage(DocumentTaskStageEnum.CHUNK_POST_PROCESS.getCode());
            updateTask(task, leaseOwner);

            List<ParentBlockCandidate> finalParentBlockList = parentBlockCandidateList.stream()
                .filter(item -> item != null
                    && StrUtil.isNotBlank(item.getText())
                    && item.getChildChunks() != null
                    && item.getChildChunks().stream().anyMatch(child -> StrUtil.isNotBlank(child.getText())))
                .toList();

            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.CHUNK_POST_PROCESS.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "切块后处理完成。",
                Map.of(
                    "parentCount", finalParentBlockList.size(),
                    "childCount", countChildCandidates(finalParentBlockList)
                ));

            ParentChildEntityBundle entityBundle = buildParentChildEntities(documentId, taskId, planId, finalParentBlockList);
            List<LaMeloAgentDocumentParentBlock> parentBlockEntityList = entityBundle.parentBlocks();
            List<LaMeloAgentDocumentChunk> chunkEntityList = entityBundle.childChunks();

            for (LaMeloAgentDocumentParentBlock parentBlock : parentBlockEntityList) {
                assertLease(taskId, leaseOwner);
                parentBlockMapper.insert(parentBlock);
            }
            for (LaMeloAgentDocumentChunk chunk : chunkEntityList) {
                assertLease(taskId, leaseOwner);
                chunkMapper.insert(chunk);
            }

            task.setCurrentStage(DocumentTaskStageEnum.VECTORIZE.getCode());
            updateTask(task, leaseOwner);

            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.VECTORIZE.getCode(),
                DocumentTaskEventTypeEnum.START.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "开始执行向量化。",
                detail("chunkCount", chunkEntityList.size(),
                    "embeddingBatchSize", QdrantChunkGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "embeddingBatchCount",
                    (chunkEntityList.size() + QdrantChunkGateway.EMBEDDING_BATCH_SIZE_LIMIT - 1)
                        / QdrantChunkGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "vectorStoreType", DocumentVectorStoreTypeEnum.QDRANT.getMsg(),
                    "parentCount", parentBlockEntityList.size()));

            assertLease(taskId, leaseOwner);
            vectorGateway.vectorize(chunkEntityList);

            DocumentKeywordSearchGateway keywordSearchGateway = keywordSearchGatewayProvider.getIfAvailable();
            if (keywordSearchGateway != null) {
                assertLease(taskId, leaseOwner);
                keywordSearchGateway.indexChunks(chunkEntityList);
            }

            for (LaMeloAgentDocumentChunk chunk : chunkEntityList) {
                assertLease(taskId, leaseOwner);
                chunkMapper.updateById(chunk);
            }

            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.VECTORIZE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "向量化完成。",
                detail("chunkCount", chunkEntityList.size(),
                    "embeddingBatchSize", QdrantChunkGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "embeddingBatchCount",
                    (chunkEntityList.size() + QdrantChunkGateway.EMBEDDING_BATCH_SIZE_LIMIT - 1)
                        / QdrantChunkGateway.EMBEDDING_BATCH_SIZE_LIMIT,
                    "vectorStoreType", DocumentVectorStoreTypeEnum.QDRANT.getMsg(),
                    "parentCount", parentBlockEntityList.size()));

            task.setCurrentStage(DocumentTaskStageEnum.STORE_COMPLETE.getCode());
            updateTask(task, leaseOwner);

            assertLease(taskId, leaseOwner);
            plan.setPlanStatus(DocumentPlanStatusEnum.EXECUTED.getCode());
            planMapper.updateById(plan);

            document.setIndexStatus(DocumentIndexStatusEnum.BUILD_SUCCESS.getCode());
            document.setLastIndexTaskId(taskId);
            assertLease(taskId, leaseOwner);
            documentMapper.updateById(document);

            finishTaskSuccess(task, DocumentTaskStageEnum.STORE_COMPLETE.getCode(), startTime, leaseOwner);
            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                DocumentTaskStageEnum.STORE_COMPLETE.getCode(),
                DocumentTaskEventTypeEnum.COMPLETE.getCode(),
                DocumentLogLevelEnum.INFO.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "索引构建完成。",
                Map.of("taskId", taskId, "chunkCount", chunkEntityList.size(), "parentCount", parentBlockEntityList.size()));
        }
        catch (Exception exception) {
            if (exception instanceof LeaseLostException) throw (LeaseLostException) exception;
            assertLease(taskId, leaseOwner);
            log.error("异步构建索引失败，documentId={}, taskId={}, planId={}", documentId, taskId, planId, exception);

            document.setIndexStatus(DocumentIndexStatusEnum.BUILD_FAILED.getCode());
            documentMapper.updateById(document);

            assertLease(taskId, leaseOwner);
            chunkMapper.update(null, new LambdaUpdateWrapper<LaMeloAgentDocumentChunk>()
                .eq(LaMeloAgentDocumentChunk::getTaskId, taskId)
                .eq(LaMeloAgentDocumentChunk::getStatus, BusinessStatus.YES.getCode())
                .set(LaMeloAgentDocumentChunk::getVectorStatus, DocumentVectorStatusEnum.VECTOR_FAILED.getCode())
                .set(LaMeloAgentDocumentChunk::getVectorStoreType, DocumentVectorStoreTypeEnum.QDRANT.getCode()));

            assertLease(taskId, leaseOwner);
            updateStepExecuteStatus(planId, DocumentStrategyExecuteStatusEnum.EXECUTE_FAILED.getCode());
            failTask(task, startTime, exception, task.getCurrentStage(), leaseOwner);
            assertLease(taskId, leaseOwner);
            taskLogService.saveLog(taskId, documentId,
                task.getCurrentStage(),
                DocumentTaskEventTypeEnum.FAILED.getCode(),
                DocumentLogLevelEnum.ERROR.getCode(),
                DocumentOperatorTypeEnum.SYSTEM.getCode(),
                null,
                "索引构建失败。",
                detail("error", exception.getMessage()));
        }
    }

    private ParentChildEntityBundle buildParentChildEntities(Long documentId,
                                                             Long taskId,
                                                             Long planId,
                                                             List<ParentBlockCandidate> parentBlockCandidateList) {
        List<LaMeloAgentDocumentParentBlock> parentBlockEntityList = new ArrayList<>();
        List<LaMeloAgentDocumentChunk> chunkEntityList = new ArrayList<>();
        int globalChunkNo = 1;

        for (int parentIndex = 0; parentIndex < parentBlockCandidateList.size(); parentIndex++) {
            ParentBlockCandidate parentCandidate = parentBlockCandidateList.get(parentIndex);
            if (parentCandidate == null || StrUtil.isBlank(parentCandidate.getText())) {
                continue;
            }

            LaMeloAgentDocumentParentBlock parentBlock = new LaMeloAgentDocumentParentBlock();
            parentBlock.setId(uidGenerator.getUid());
            parentBlock.setDocumentId(documentId);
            parentBlock.setTaskId(taskId);
            parentBlock.setPlanId(planId);
            parentBlock.setParentNo(parentIndex + 1);
            parentBlock.setSourceType(parentCandidate.getSourceType() == null
                ? DocumentChunkSourceTypeEnum.ORIGINAL.getCode() : parentCandidate.getSourceType());
            parentBlock.setSectionPath(parentCandidate.getSectionPath());
            parentBlock.setStructureNodeId(parentCandidate.getStructureNodeId());
            parentBlock.setStructureNodeType(parentCandidate.getStructureNodeType());
            parentBlock.setCanonicalPath(parentCandidate.getCanonicalPath());
            parentBlock.setItemIndex(parentCandidate.getItemIndex());
            parentBlock.setParentText(parentCandidate.getText().trim());
            parentBlock.setCharCount(parentCandidate.getText().length());
            parentBlock.setTokenCount(estimateTokenCount(parentCandidate.getText()));
            parentBlock.setStatus(BusinessStatus.YES.getCode());

            int startChunkNo = globalChunkNo;
            int childCount = 0;
            for (ChunkCandidate childCandidate : parentCandidate.getChildChunks()) {
                if (childCandidate == null || StrUtil.isBlank(childCandidate.getText())) {
                    continue;
                }
                LaMeloAgentDocumentChunk chunk = new LaMeloAgentDocumentChunk();
                chunk.setId(uidGenerator.getUid());
                chunk.setDocumentId(documentId);
                chunk.setTaskId(taskId);
                chunk.setPlanId(planId);
                chunk.setParentBlockId(parentBlock.getId());
                chunk.setChunkNo(globalChunkNo++);
                chunk.setSourceType(childCandidate.getSourceType() == null
                    ? DocumentChunkSourceTypeEnum.ORIGINAL.getCode() : childCandidate.getSourceType());
                chunk.setSectionPath(StrUtil.blankToDefault(childCandidate.getSectionPath(), parentCandidate.getSectionPath()));
                chunk.setStructureNodeId(childCandidate.getStructureNodeId());
                chunk.setStructureNodeType(childCandidate.getStructureNodeType());
                chunk.setCanonicalPath(childCandidate.getCanonicalPath());
                chunk.setItemIndex(childCandidate.getItemIndex());
                chunk.setChunkText(childCandidate.getText().trim());
                chunk.setCharCount(childCandidate.getText().length());

                chunk.setTokenCount(estimateTokenCount(childCandidate.getText()));
                chunk.setVectorStatus(DocumentVectorStatusEnum.WAIT_VECTOR.getCode());
                chunk.setVectorStoreType(DocumentVectorStoreTypeEnum.QDRANT.getCode());
                chunk.setStatus(BusinessStatus.YES.getCode());
                chunkEntityList.add(chunk);
                childCount++;
            }

            parentBlock.setChildCount(childCount);
            parentBlock.setStartChunkNo(childCount == 0 ? null : startChunkNo);
            parentBlock.setEndChunkNo(childCount == 0 ? null : globalChunkNo - 1);
            parentBlockEntityList.add(parentBlock);
        }

        return new ParentChildEntityBundle(parentBlockEntityList, chunkEntityList);
    }

    private int countChildCandidates(List<ParentBlockCandidate> parentBlockCandidateList) {
        if (parentBlockCandidateList == null || parentBlockCandidateList.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (ParentBlockCandidate candidate : parentBlockCandidateList) {
            if (candidate == null || candidate.getChildChunks() == null) {
                continue;
            }
            count += (int) candidate.getChildChunks().stream()
                .filter(child -> child != null && StrUtil.isNotBlank(child.getText()))
                .count();
        }
        return count;
    }

    private void updateStepExecuteStatus(Long planId, Integer executeStatus) {

        stepMapper.update(null, new LambdaUpdateWrapper<LaMeloAgentDocumentStrategyStep>()
            .eq(LaMeloAgentDocumentStrategyStep::getPlanId, planId)
            .eq(LaMeloAgentDocumentStrategyStep::getStatus, BusinessStatus.YES.getCode())
            .set(LaMeloAgentDocumentStrategyStep::getExecuteStatus, executeStatus));
    }

    private List<LaMeloAgentDocumentStrategyStep> listSteps(Long planId) {
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

    private int pipelineOrder(String pipelineType) {
        return DocumentStrategyPipelineTypeEnum.PARENT.getCode().equalsIgnoreCase(
            StrUtil.blankToDefault(pipelineType, "")
        ) ? 0 : 1;
    }

    private int getNextPlanVersion(Long documentId) {

        List<LaMeloAgentDocumentStrategyPlan> planList = planMapper.selectList(new LambdaQueryWrapper<LaMeloAgentDocumentStrategyPlan>()
            .eq(LaMeloAgentDocumentStrategyPlan::getDocumentId, documentId)
            .eq(LaMeloAgentDocumentStrategyPlan::getStatus, BusinessStatus.YES.getCode())
            .orderByDesc(LaMeloAgentDocumentStrategyPlan::getPlanVersion)
            .last("limit 1"));
        return planList.isEmpty() ? 1 : planList.get(0).getPlanVersion() + 1;
    }

    private void finishTaskSuccess(LaMeloAgentDocumentTask task, Integer stage, Date startTime,
                                   String leaseOwner) {

        Date finishTime = new Date();
        task.setTaskStatus(DocumentTaskStatusEnum.SUCCESS.getCode());
        task.setCurrentStage(stage);
        task.setFinishTime(finishTime);
        task.setCostMillis(finishTime.getTime() - startTime.getTime());
        task.setErrorCode(null);
        task.setErrorMsg(null);
        updateTask(task, leaseOwner);
    }

    private void syncNavigationArtifacts(Long documentId,
                                         Long parseTaskId,
                                         List<LaMeloAgentDocumentStructureNode> structureNodes,
                                         String leaseOwner) {
        log.info("开始同步导航产物: documentId={}, parseTaskId={}, structureNodeCount={}",
            documentId,
            parseTaskId,
            structureNodes == null ? 0 : structureNodes.size());
        DocumentNavigationIndexService navigationIndexService = navigationIndexServiceProvider.getIfAvailable();
        if (navigationIndexService != null) {
            assertLease(parseTaskId, leaseOwner);
            log.info("同步导航 Qdrant 索引: documentId={}, parseTaskId={}", documentId, parseTaskId);
            navigationIndexService.reindexDocumentNodes(documentId, parseTaskId, structureNodes);
        }
        else {
            log.info("跳过导航 Qdrant 索引同步，因为服务未启用: documentId={}, parseTaskId={}", documentId, parseTaskId);
        }
        DocumentStructureGraphProjectionService graphProjectionService = graphProjectionServiceProvider.getIfAvailable();
        if (graphProjectionService != null && graphProjectionService.enabled()) {
            assertLease(parseTaskId, leaseOwner);
            log.info("同步结构图投影: documentId={}, parseTaskId={}", documentId, parseTaskId);
            graphProjectionService.projectToGraph(documentId, parseTaskId);
        }
        else {
            log.info("跳过结构图投影，因为图服务未启用: documentId={}, parseTaskId={}", documentId, parseTaskId);
        }
    }

    private void failTask(LaMeloAgentDocumentTask task, Date startTime, Exception exception,
                          Integer currentStage, String leaseOwner) {

        Date finishTime = new Date();
        task.setTaskStatus(DocumentTaskStatusEnum.FAILED.getCode());
        task.setCurrentStage(currentStage);
        task.setFinishTime(finishTime);
        task.setCostMillis(finishTime.getTime() - startTime.getTime());
        task.setErrorCode("TASK_FAILED");
        task.setErrorMsg(exception.getMessage());
        updateTask(task, leaseOwner);
    }

    private void assertLease(Long taskId, String leaseOwner) {
        if (leaseOwner != null && taskMapper.hasValidLease(taskId, leaseOwner) != 1) {
            throw new LeaseLostException(taskId);
        }
    }

    private void updateTask(LaMeloAgentDocumentTask task, String leaseOwner) {
        if (leaseOwner == null) {
            taskMapper.updateById(task);
            return;
        }
        int updated = taskMapper.update(task, new LambdaUpdateWrapper<LaMeloAgentDocumentTask>()
            .eq(LaMeloAgentDocumentTask::getId, task.getId())
            .eq(LaMeloAgentDocumentTask::getLeaseOwner, leaseOwner)
            .apply("lease_until > NOW()")
            .in(LaMeloAgentDocumentTask::getTaskStatus,
                DocumentTaskStatusEnum.NEW.getCode(), DocumentTaskStatusEnum.RUNNING.getCode(),
                DocumentTaskStatusEnum.FAILED.getCode()));
        if (updated != 1) throw new LeaseLostException(task.getId());
    }

    private static final class LeaseLostException extends IllegalStateException {
        private LeaseLostException(Long taskId) {
            super("Document task lease lost: " + taskId);
        }
    }

    private int estimateTokenCount(String text) {
        if (StrUtil.isBlank(text)) {
            return 0;
        }
        int chineseCount = 0;
        int englishCount = 0;

        for (char current : text.toCharArray()) {
            if (String.valueOf(current).matches("[\\u4e00-\\u9fa5]")) {
                chineseCount++;
            }
        }

        for (String word : text.split("\\s+")) {
            if (word.matches(".*[A-Za-z].*")) {
                englishCount++;
            }
        }

        return chineseCount + englishCount + Math.max(1, (text.length() - chineseCount) / 4);
    }

    private Map<String, Object> detail(Object... keyValues) {
        Map<String, Object> detailMap = new LinkedHashMap<>();

        for (int index = 0; index + 1 < keyValues.length; index += 2) {
            detailMap.put(String.valueOf(keyValues[index]), keyValues[index + 1]);
        }
        return detailMap;
    }

    private record ParentChildEntityBundle(
        List<LaMeloAgentDocumentParentBlock> parentBlocks,
        List<LaMeloAgentDocumentChunk> childChunks
    ) {
    }
}
