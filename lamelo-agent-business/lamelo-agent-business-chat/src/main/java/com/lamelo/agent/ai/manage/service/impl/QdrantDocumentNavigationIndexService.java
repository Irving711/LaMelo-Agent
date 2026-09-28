package com.lamelo.agent.ai.manage.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lamelo.agent.ai.manage.config.DocumentManageProperties;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentStructureNode;
import com.lamelo.agent.ai.manage.model.index.DocumentNavigationIndexRecord;
import com.lamelo.agent.ai.manage.qdrant.QdrantStore;
import com.lamelo.agent.ai.manage.qdrant.SparseTextEncoder;
import com.lamelo.agent.ai.manage.service.DocumentNavigationIndexService;
import com.lamelo.agent.enums.DocumentStructureNodeTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class QdrantDocumentNavigationIndexService implements DocumentNavigationIndexService {

    private final QdrantStore store;
    private final SparseTextEncoder encoder;
    private final ObjectMapper mapper;
    private final String collection;

    public QdrantDocumentNavigationIndexService(QdrantStore store, SparseTextEncoder encoder,
                                                ObjectMapper mapper, DocumentManageProperties properties) {
        this.store = store;
        this.encoder = encoder;
        this.mapper = mapper;
        this.collection = properties.getQdrant().getNavigationCollection();
    }

    @Override
    public void reindexDocumentNodes(Long documentId, Long parseTaskId, List<LaMeloAgentDocumentStructureNode> nodes) {
        if (documentId == null) {
            return;
        }
        deleteByDocumentId(documentId);
        if (CollUtil.isEmpty(nodes)) {
            return;
        }
        List<Map<String, Object>> batch = new ArrayList<>();
        for (LaMeloAgentDocumentStructureNode node : nodes) {
            if (node == null || node.getId() == null) {
                continue;
            }
            DocumentNavigationIndexRecord record = toRecord(node, parseTaskId);
            Map<String, Double> text = new LinkedHashMap<>();
            add(text, record.getTitle(), 10D);
            add(text, record.getSectionPath(), 8D);
            add(text, record.getAnchorText(), 5D);
            add(text, record.getContentText(), 1D);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.convertValue(record, Map.class);
            batch.add(Map.of("id", record.getNodeId(), "vector", Map.of("lexical", encoder.encode(text)), "payload", payload));
            if (batch.size() == 100) {
                store.upsert(collection, batch);
                batch.clear();
            }
        }
        store.upsert(collection, batch);
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId != null) {
            store.deleteByDocumentId(collection, documentId);
        }
    }

    @Override
    public List<NavigationSectionHit> searchSections(Long documentId, String topic, String facet,
                                                    String informationNeed, String question, int size) {
        if (documentId == null) {
            return List.of();
        }
        Map<String, Double> text = new LinkedHashMap<>();
        add(text, topic, 1D);
        add(text, facet, 1D);
        add(text, informationNeed, 1D);
        add(text, question, 1D);
        if (text.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> must = List.of(
            Map.of("key", "documentId", "match", Map.of("value", documentId)),
            Map.of("key", "nodeType", "match", Map.of("value", DocumentStructureNodeTypeEnum.SECTION.name())));
        try {
            List<NavigationSectionHit> result = new ArrayList<>();
            for (JsonNode hit : store.query(collection, "lexical", encoder.encode(text),
                QdrantStore.filter(must), Math.max(1, Math.min(size <= 0 ? 8 : size, 20)))) {
                JsonNode payload = hit.path("payload");
                result.add(new NavigationSectionHit(payload.path("nodeId").asLong(),
                    payload.path("nodeCode").asText(""), payload.path("title").asText(""),
                    payload.path("sectionPath").asText(""), payload.path("canonicalPath").asText(""),
                    hit.path("score").asDouble()));
            }
            return result;
        }
        catch (RestClientException | IllegalStateException exception) {
            log.warn("Qdrant 导航检索失败: documentId={}", documentId, exception);
            return List.of();
        }
    }

    private DocumentNavigationIndexRecord toRecord(LaMeloAgentDocumentStructureNode node, Long parseTaskId) {
        DocumentStructureNodeTypeEnum type = DocumentStructureNodeTypeEnum.getRc(node.getNodeType());
        return DocumentNavigationIndexRecord.builder()
            .nodeId(node.getId()).documentId(node.getDocumentId())
            .parseTaskId(node.getParseTaskId() == null ? parseTaskId : node.getParseTaskId())
            .nodeType(type == null ? "" : type.name()).nodeCode(value(node.getNodeCode()))
            .nodeNo(node.getNodeNo()).depth(node.getDepth()).parentNodeId(node.getParentNodeId())
            .title(value(node.getTitle())).anchorText(value(node.getAnchorText()))
            .sectionPath(value(node.getSectionPath())).canonicalPath(value(node.getCanonicalPath()))
            .contentText(value(node.getContentText())).itemIndex(node.getItemIndex()).build();
    }

    private void add(Map<String, Double> text, String value, double boost) {
        if (value != null && !value.isBlank()) {
            text.merge(value, boost, Double::sum);
        }
    }

    private String value(String value) {
        return value == null ? "" : value.trim();
    }
}
