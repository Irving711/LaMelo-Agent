package com.lamelo.agent.ai.manage.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocument;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentParentBlock;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentParentBlockMapper;
import com.lamelo.agent.ai.manage.model.DocumentRetrieveFilters;
import com.lamelo.agent.ai.manage.model.DocumentRetrieveRequest;
import com.lamelo.agent.ai.manage.model.KnowledgeDocumentDescriptor;
import com.lamelo.agent.ai.manage.service.DocumentKnowledgeService;
import com.lamelo.agent.ai.manage.support.DocumentKnowledgeMetadataKeys;
import com.lamelo.agent.enums.BusinessStatus;
import com.lamelo.agent.enums.DocumentIndexStatusEnum;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务实现层
 * @author: 阿星不是程序员
 **/

@AllArgsConstructor
@Service
public class DocumentKnowledgeServiceImpl implements DocumentKnowledgeService {

    private final LaMeloAgentDocumentMapper documentMapper;
    
    private final LaMeloAgentDocumentParentBlockMapper parentBlockMapper;
    
    private final QdrantChunkGateway qdrantChunkGateway;
    
    @Override
    public List<KnowledgeDocumentDescriptor> listRetrievableDocuments() {

        List<LaMeloAgentDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<LaMeloAgentDocument>()
            .eq(LaMeloAgentDocument::getStatus, BusinessStatus.YES.getCode())
            .eq(LaMeloAgentDocument::getIndexStatus, DocumentIndexStatusEnum.BUILD_SUCCESS.getCode())
            .isNotNull(LaMeloAgentDocument::getLastIndexTaskId)
            .orderByDesc(LaMeloAgentDocument::getEditTime)
            .orderByDesc(LaMeloAgentDocument::getId));
        if (CollUtil.isEmpty(documents)) {
            return List.of();
        }

        return documents.stream()
            .map(document -> new KnowledgeDocumentDescriptor(
                document.getId(),
                document.getDocumentName(),
                document.getLastIndexTaskId(),
                document.getKnowledgeScopeCode(),
                document.getKnowledgeScopeName(),
                document.getBusinessCategory(),
                document.getDocumentTags()
            ))
            .toList();
    }

    @Override
    public List<Document> vectorSearch(DocumentRetrieveRequest request) {
        if (!isSearchableRequest(request)) {
            return List.of();
        }
        ResolvedMetadataScope resolvedScope = resolveMetadataScope(request);
        if (resolvedScope.documentIds().isEmpty() || resolvedScope.taskIds().isEmpty()) {
            return List.of();
        }
        return decorateResults(qdrantChunkGateway.searchDense(request), listDescriptorMap(request.resolvedDocumentIds()));
    }

    @Override
    public List<Document> keywordSearch(DocumentRetrieveRequest request) {
        if (!isSearchableRequest(request)) {
            return List.of();
        }
        ResolvedMetadataScope resolvedScope = resolveMetadataScope(request);
        if (resolvedScope.documentIds().isEmpty() || resolvedScope.taskIds().isEmpty()) {
            return List.of();
        }
        return decorateResults(qdrantChunkGateway.search(request), listDescriptorMap(request.resolvedDocumentIds()));
    }

    private List<Document> decorateResults(List<Document> hits, Map<Long, KnowledgeDocumentDescriptor> descriptors) {
        return hits.stream().map(hit -> {
            Long documentId = asLong(hit.getMetadata().get(DocumentKnowledgeMetadataKeys.DOCUMENT_ID));
            KnowledgeDocumentDescriptor descriptor = descriptors.get(documentId);
            if (descriptor == null) {
                return hit;
            }
            Map<String, Object> metadata = new LinkedHashMap<>(hit.getMetadata());
            metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_NAME, safeText(descriptor.getDocumentName()));
            metadata.put(DocumentKnowledgeMetadataKeys.KNOWLEDGE_SCOPE_CODE, safeText(descriptor.getKnowledgeScopeCode()));
            metadata.put(DocumentKnowledgeMetadataKeys.KNOWLEDGE_SCOPE_NAME, safeText(descriptor.getKnowledgeScopeName()));
            metadata.put(DocumentKnowledgeMetadataKeys.BUSINESS_CATEGORY, safeText(descriptor.getBusinessCategory()));
            metadata.put(DocumentKnowledgeMetadataKeys.DOCUMENT_TAGS, safeText(descriptor.getDocumentTags()));
            return Document.builder().id(hit.getId()).text(hit.getText()).metadata(metadata).score(hit.getScore()).build();
        }).toList();
    }

    @Override
    public List<Document> elevateToParentBlocks(List<Document> childDocuments, int maxChars) {
        if (CollUtil.isEmpty(childDocuments)) {
            return List.of();
        }

        Map<Long, List<Document>> childGroupsByParent = new LinkedHashMap<>();
        List<Document> fallbackDocuments = new ArrayList<>();
        for (Document childDocument : childDocuments) {
            if (childDocument == null) {
                continue;
            }
            Long parentBlockId = asLong(childDocument.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID));
            if (parentBlockId == null) {
                fallbackDocuments.add(childDocument);
                continue;
            }
            childGroupsByParent.computeIfAbsent(parentBlockId, ignored -> new ArrayList<>()).add(childDocument);
        }

        if (childGroupsByParent.isEmpty()) {
            return fallbackDocuments;
        }

        List<Long> parentBlockIds = new ArrayList<>(childGroupsByParent.keySet());
        Map<Long, LaMeloAgentDocumentParentBlock> parentBlockMap = parentBlockMapper.selectList(
                new LambdaQueryWrapper<LaMeloAgentDocumentParentBlock>()
                    .in(LaMeloAgentDocumentParentBlock::getId, parentBlockIds)
                    .eq(LaMeloAgentDocumentParentBlock::getStatus, BusinessStatus.YES.getCode())
                    .orderByAsc(LaMeloAgentDocumentParentBlock::getParentNo)
            ).stream()
            .collect(Collectors.toMap(
                LaMeloAgentDocumentParentBlock::getId,
                parent -> parent,
                (left, right) -> left,
                LinkedHashMap::new
            ));

        List<Document> elevatedDocuments = new ArrayList<>(childGroupsByParent.size() + fallbackDocuments.size());
        for (Map.Entry<Long, List<Document>> entry : childGroupsByParent.entrySet()) {
            LaMeloAgentDocumentParentBlock parentBlock = parentBlockMap.get(entry.getKey());
            if (parentBlock == null) {
                elevatedDocuments.addAll(entry.getValue());
                continue;
            }
            elevatedDocuments.add(buildParentEvidenceDocument(parentBlock, entry.getValue(), maxChars));
        }
        elevatedDocuments.addAll(fallbackDocuments);
        elevatedDocuments.sort(this::compareEvidenceDocument);
        return elevatedDocuments;
    }

    private boolean isSearchableRequest(DocumentRetrieveRequest request) {

        if (request == null || StrUtil.isBlank(request.getQuestion()) || StrUtil.isBlank(request.getRetrievalQuery())) {
            return false;
        }
        return !request.resolvedDocumentIds().isEmpty() && !request.resolvedTaskIds().isEmpty();
    }

    private Map<Long, KnowledgeDocumentDescriptor> listDescriptorMap(List<Long> requestedDocumentIds) {
        List<KnowledgeDocumentDescriptor> descriptors = listRetrievableDocuments();
        if (descriptors.isEmpty()) {
            return Map.of();
        }

        return descriptors.stream()
            .filter(descriptor -> requestedDocumentIds.contains(descriptor.getDocumentId()))
            .collect(Collectors.toMap(
                KnowledgeDocumentDescriptor::getDocumentId,
                descriptor -> descriptor,
                (left, right) -> left,
                LinkedHashMap::new
            ));
    }

    private ResolvedMetadataScope resolveMetadataScope(DocumentRetrieveRequest request) {
        List<Long> baseDocumentIds = request.resolvedDocumentIds();
        List<Long> baseTaskIds = request.resolvedTaskIds();
        return new ResolvedMetadataScope(baseDocumentIds, baseTaskIds, request.getFilters());
    }

    private Document buildParentEvidenceDocument(LaMeloAgentDocumentParentBlock parentBlock,
                                                 List<Document> childDocuments,
                                                 int maxChars) {
        Document bestChild = childDocuments.stream()
            .max(Comparator.comparingDouble(document -> {
                Double score = resolveScore(document);
                return score == null ? 0D : score;
            }))
            .orElseThrow();

        double parentScore = aggregateParentScore(childDocuments);
        Map<String, Object> metadata = new LinkedHashMap<>(bestChild.getMetadata());
        metadata.put(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID, parentBlock.getId());
        metadata.put(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO, parentBlock.getParentNo());
        metadata.put(DocumentKnowledgeMetadataKeys.SECTION_PATH, safeText(parentBlock.getSectionPath()));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_ID, parentBlock.getStructureNodeId());
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_TYPE, parentBlock.getStructureNodeType());
        metadata.put(DocumentKnowledgeMetadataKeys.CANONICAL_PATH, safeText(parentBlock.getCanonicalPath()));
        putIfNotNull(metadata, DocumentKnowledgeMetadataKeys.ITEM_INDEX, parentBlock.getItemIndex());
        metadata.put(DocumentKnowledgeMetadataKeys.SCORE, parentScore);
        metadata.put(DocumentKnowledgeMetadataKeys.ORIGINAL_SNIPPET, safeText(parentBlock.getParentText()));

        LinkedHashSet<String> channels = childDocuments.stream()
            .map(document -> asText(document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHANNEL)))
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL,
            channels.size() > 1 ? "hybrid" : channels.stream().findFirst().orElse("vector"));

        return Document.builder()
            .id("parent-" + parentBlock.getId())
            .text(renderParentEvidenceText(parentBlock, childDocuments, maxChars))
            .metadata(metadata)
            .score(parentScore)
            .build();
    }

    private double aggregateParentScore(List<Document> childDocuments) {
        double bestChildScore = childDocuments.stream()
            .map(this::resolveScore)
            .filter(Objects::nonNull)
            .max(Double::compareTo)
            .orElse(0D);
        int supportCount = Math.max(0, childDocuments.size() - 1);
        LinkedHashSet<String> channels = childDocuments.stream()
            .map(document -> asText(document.getMetadata().get(DocumentKnowledgeMetadataKeys.CHANNEL)))
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.toCollection(LinkedHashSet::new));

        double supportWeight = Math.min(0.36D, supportCount * 0.12D);
        double multiChannelWeight = channels.size() > 1 ? 0.10D : 0D;
        return bestChildScore * (1D + supportWeight + multiChannelWeight);
    }

    private void putIfNotNull(Map<String, Object> metadata, String key, Object value) {
        if (value != null) {
            metadata.put(key, value);
        }
    }

    private int compareEvidenceDocument(Document left, Document right) {
        int scoreCompare = Double.compare(resolveScoreOrZero(right), resolveScoreOrZero(left));
        if (scoreCompare != 0) {
            return scoreCompare;
        }
        Integer leftParentNo = asInteger(left == null ? null : left.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO));
        Integer rightParentNo = asInteger(right == null ? null : right.getMetadata().get(DocumentKnowledgeMetadataKeys.PARENT_BLOCK_NO));
        int parentNoCompare = compareNullableInteger(leftParentNo, rightParentNo);
        if (parentNoCompare != 0) {
            return parentNoCompare;
        }
        Integer leftChunkNo = asInteger(left == null ? null : left.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_NO));
        Integer rightChunkNo = asInteger(right == null ? null : right.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_NO));
        return compareNullableInteger(leftChunkNo, rightChunkNo);
    }

    private double resolveScoreOrZero(Document document) {
        Double score = resolveScore(document);
        return score == null ? 0D : score;
    }

    private int compareNullableInteger(Integer left, Integer right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return Integer.compare(left, right);
    }

    private String renderParentEvidenceText(LaMeloAgentDocumentParentBlock parentBlock,
                                            List<Document> childDocuments,
                                            int maxChars) {
        String parentText = safeText(parentBlock.getParentText());
        if (StrUtil.isBlank(parentText)) {
            return childDocuments.isEmpty() ? "" : StrUtil.blankToDefault(childDocuments.get(0).getText(), "");
        }

        StringBuilder hitSummaryBuilder = new StringBuilder();
        for (Document childDocument : childDocuments) {
            if (childDocument == null) {
                continue;
            }
            if (!hitSummaryBuilder.isEmpty()) {
                hitSummaryBuilder.append('\n');
            }
            hitSummaryBuilder.append("- child#")
                .append(asInteger(childDocument.getMetadata().get(DocumentKnowledgeMetadataKeys.CHUNK_NO)))
                .append("：")
                .append(trimText(safeText(childDocument.getText()), 140));
        }

        String composed = joinSections(
            "[父块内容]\n" + parentText,
            hitSummaryBuilder.isEmpty() ? "" : "[命中子片段]\n" + hitSummaryBuilder
        );
        return trimText(composed, Math.max(1, maxChars));
    }

    private Double resolveScore(Document document) {
        if (document == null) {
            return null;
        }
        Object metadataScore = document.getMetadata().get(DocumentKnowledgeMetadataKeys.SCORE);
        if (metadataScore instanceof Number number) {
            return number.doubleValue();
        }
        return document.getScore();
    }

    private String joinSections(String... sections) {
        List<String> parts = new ArrayList<>();
        for (String section : sections) {
            if (StrUtil.isNotBlank(section)) {
                parts.add(section.trim());
            }
        }
        return String.join("\n\n", parts);
    }

    private String trimText(String text, int maxChars) {
        if (StrUtil.isBlank(text) || text.length() <= maxChars) {
            return StrUtil.blankToDefault(text, "");
        }
        return text.substring(0, Math.max(0, maxChars - 1)) + "…";
    }

    private String safeText(String text) {
        return text == null ? "" : text;
    }

    private Long asLong(Object value) {
        return value instanceof Number number ? number.longValue() : null;
    }

    private Integer asInteger(Object value) {
        return value instanceof Number number ? number.intValue() : null;
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private record ResolvedMetadataScope(
        List<Long> documentIds,
        List<Long> taskIds,
        DocumentRetrieveFilters filters
    ) {
    }
}
