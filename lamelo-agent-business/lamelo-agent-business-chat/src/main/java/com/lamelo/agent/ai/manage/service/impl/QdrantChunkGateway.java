package com.lamelo.agent.ai.manage.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.lamelo.agent.ai.manage.config.DocumentManageProperties;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentChunk;
import com.lamelo.agent.ai.manage.model.DocumentRetrieveFilters;
import com.lamelo.agent.ai.manage.model.DocumentRetrieveRequest;
import com.lamelo.agent.ai.manage.qdrant.QdrantStore;
import com.lamelo.agent.ai.manage.qdrant.SparseTextEncoder;
import com.lamelo.agent.ai.manage.service.DocumentVectorGateway;
import com.lamelo.agent.ai.manage.service.keyword.DocumentKeywordSearchGateway;
import com.lamelo.agent.ai.manage.support.DocumentKnowledgeMetadataKeys;
import com.lamelo.agent.enums.DocumentVectorStatusEnum;
import com.lamelo.agent.enums.DocumentVectorStoreTypeEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Slf4j
@Service
public class QdrantChunkGateway implements DocumentVectorGateway, DocumentKeywordSearchGateway {

    public static final int EMBEDDING_BATCH_SIZE_LIMIT = 10;

    private final QdrantStore store;
    private final SparseTextEncoder encoder;
    private final ObjectProvider<EmbeddingModel> embeddingModels;
    private final DocumentManageProperties.Qdrant settings;

    @Value("${spring.ai.openai.embedding.options.model:}")
    private String embeddingModelName;

    public QdrantChunkGateway(QdrantStore store, SparseTextEncoder encoder,
                              ObjectProvider<EmbeddingModel> embeddingModels, DocumentManageProperties properties) {
        this.store = store;
        this.encoder = encoder;
        this.embeddingModels = embeddingModels;
        this.settings = properties.getQdrant();
    }

    @Override
    public void vectorize(List<LaMeloAgentDocumentChunk> chunks) {
        if (CollUtil.isEmpty(chunks)) {
            return;
        }
        EmbeddingModel model = requireEmbeddingModel();
        List<LaMeloAgentDocumentChunk> valid = chunks.stream()
            .filter(chunk -> chunk != null && chunk.getId() != null && chunk.getChunkText() != null && !chunk.getChunkText().isBlank())
            .toList();
        for (int start = 0; start < valid.size(); start += EMBEDDING_BATCH_SIZE_LIMIT) {
            List<LaMeloAgentDocumentChunk> batch = valid.subList(start, Math.min(valid.size(), start + EMBEDDING_BATCH_SIZE_LIMIT));
            List<float[]> embeddings = model.embed(batch.stream().map(LaMeloAgentDocumentChunk::getChunkText).toList());
            if (embeddings.size() != batch.size()) {
                throw new IllegalStateException("Embedding 数量与文档块数量不一致");
            }
            List<Map<String, Object>> points = new ArrayList<>();
            for (int i = 0; i < batch.size(); i++) {
                LaMeloAgentDocumentChunk chunk = batch.get(i);
                float[] embedding = embeddings.get(i);
                if (embedding == null || embedding.length != settings.getDimension()) {
                    throw new IllegalStateException("Embedding 维度与 Qdrant collection 不一致");
                }
                Map<String, Double> fields = new LinkedHashMap<>();
                fields.put(chunk.getChunkText(), 1D);
                addField(fields, chunk.getSectionPath(), 3D);
                addField(fields, chunk.getCanonicalPath(), 2D);
                Map<String, Object> vectors = Map.of("dense", embedding, "lexical", encoder.encode(fields));
                points.add(Map.of("id", chunk.getId(), "vector", vectors, "payload", payload(chunk)));
            }
            store.upsert(settings.getChunkCollection(), points);
            for (LaMeloAgentDocumentChunk chunk : batch) {
                chunk.setVectorId(String.valueOf(chunk.getId()));
                chunk.setVectorStatus(DocumentVectorStatusEnum.VECTOR_SUCCESS.getCode());
                chunk.setVectorStoreType(DocumentVectorStoreTypeEnum.QDRANT.getCode());
            }
        }
    }

    @Override
    public void indexChunks(List<LaMeloAgentDocumentChunk> chunks) {
        // The dense write above stores both vectors in the same Qdrant point.
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        if (documentId != null) {
            store.deleteByDocumentId(settings.getChunkCollection(), documentId);
        }
    }

    public List<Document> searchDense(DocumentRetrieveRequest request) {
        if (!searchable(request)) {
            return List.of();
        }
        float[] vector = requireEmbeddingModel().embed(request.getRetrievalQuery().trim());
        if (vector.length != settings.getDimension()) {
            throw new IllegalStateException("查询向量维度与 Qdrant collection 不一致");
        }
        return search(request, "dense", vector, "vector");
    }

    @Override
    public List<Document> search(DocumentRetrieveRequest request) {
        if (!searchable(request)) {
            return List.of();
        }
        Map<String, Double> fields = new LinkedHashMap<>();
        addField(fields, request.getRetrievalQuery(), 1D);
        if (request.getQueryContextHints() != null) {
            request.getQueryContextHints().forEach(hint -> addField(fields, hint, 0.5D));
        }
        return search(request, "lexical", encoder.encode(fields), "keyword");
    }

    private List<Document> search(DocumentRetrieveRequest request, String vectorName, Object vector, String channel) {
        try {
            return searchQdrant(request, vectorName, vector, channel);
        }
        catch (RestClientException | IllegalStateException exception) {
            log.warn("Qdrant 文档检索失败: channel={}", channel, exception);
            return List.of();
        }
    }

    private List<Document> searchQdrant(DocumentRetrieveRequest request, String vectorName, Object vector, String channel) {
        List<Map<String, Object>> must = new ArrayList<>();
        must.add(QdrantStore.terms("documentId", request.resolvedDocumentIds()));
        must.add(QdrantStore.terms("taskId", request.resolvedTaskIds()));
        DocumentRetrieveFilters filters = request.getFilters();
        if (filters != null) {
            if (CollUtil.isNotEmpty(filters.getStructureNodeIdHints())) {
                must.add(QdrantStore.terms("structureNodeId", filters.getStructureNodeIdHints()));
            }
            if (CollUtil.isNotEmpty(filters.getItemIndexHints())) {
                must.add(QdrantStore.terms("itemIndex", filters.getItemIndexHints()));
            }
        }
        int topK = Math.max(1, Math.min(request.getTopK() <= 0 ? 8 : request.getTopK(), 100));
        Map<String, Object> scope = QdrantStore.filter(must);
        if (!hasPathFilters(filters)) {
            return store.query(settings.getChunkCollection(), vectorName, vector, scope, topK)
                .stream().map(hit -> toDocument(hit, channel)).toList();
        }
        List<Document> matched = new ArrayList<>();
        int offset = 0;
        while (matched.size() < topK) {
            List<JsonNode> page = store.query(settings.getChunkCollection(), vectorName, vector, scope, 100, offset);
            for (JsonNode hit : page) {
                if (matchesPaths(hit.path("payload"), filters)) {
                    matched.add(toDocument(hit, channel));
                    if (matched.size() == topK) {
                        break;
                    }
                }
            }
            if (page.size() < 100) {
                break;
            }
            offset += page.size();
        }
        return matched;
    }

    private boolean hasPathFilters(DocumentRetrieveFilters filters) {
        return filters != null && (CollUtil.isNotEmpty(filters.getSectionPathHints())
            || CollUtil.isNotEmpty(filters.getCanonicalPathHints()));
    }

    private boolean matchesPaths(JsonNode payload, DocumentRetrieveFilters filters) {
        if (filters == null) {
            return true;
        }
        if (CollUtil.isNotEmpty(filters.getSectionPathHints())) {
            String path = payload.path("sectionPath").asText("").toLowerCase(Locale.ROOT);
            if (filters.getSectionPathHints().stream().noneMatch(hint -> path.contains(hint.toLowerCase(Locale.ROOT)))) {
                return false;
            }
        }
        if (CollUtil.isNotEmpty(filters.getCanonicalPathHints())) {
            String path = payload.path("canonicalPath").asText("").toLowerCase(Locale.ROOT);
            return filters.getCanonicalPathHints().stream().anyMatch(hint -> path.startsWith(hint.toLowerCase(Locale.ROOT)));
        }
        return true;
    }

    private Document toDocument(JsonNode hit, String channel) {
        JsonNode payload = hit.path("payload");
        long id = hit.path("id").asLong();
        String text = payload.path("chunkText").asText("");
        double score = hit.path("score").asDouble();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put(DocumentKnowledgeMetadataKeys.SOURCE_TYPE, "DOCUMENT");
        metadata.put(DocumentKnowledgeMetadataKeys.CHANNEL, channel);
        metadata.put(DocumentKnowledgeMetadataKeys.SCORE, score);
        metadata.put(DocumentKnowledgeMetadataKeys.CHUNK_ID, id);
        copyLong(payload, metadata, "documentId", DocumentKnowledgeMetadataKeys.DOCUMENT_ID);
        copyLong(payload, metadata, "taskId", DocumentKnowledgeMetadataKeys.TASK_ID);
        copyLong(payload, metadata, "parentBlockId", DocumentKnowledgeMetadataKeys.PARENT_BLOCK_ID);
        copyLong(payload, metadata, "structureNodeId", DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_ID);
        copyInt(payload, metadata, "chunkNo", DocumentKnowledgeMetadataKeys.CHUNK_NO);
        copyInt(payload, metadata, "structureNodeType", DocumentKnowledgeMetadataKeys.STRUCTURE_NODE_TYPE);
        copyInt(payload, metadata, "itemIndex", DocumentKnowledgeMetadataKeys.ITEM_INDEX);
        metadata.put(DocumentKnowledgeMetadataKeys.SECTION_PATH, payload.path("sectionPath").asText(""));
        metadata.put(DocumentKnowledgeMetadataKeys.CANONICAL_PATH, payload.path("canonicalPath").asText(""));
        metadata.put(DocumentKnowledgeMetadataKeys.ORIGINAL_SNIPPET, text);
        return Document.builder().id(String.valueOf(id)).text(text).metadata(metadata).score(score).build();
    }

    private Map<String, Object> payload(LaMeloAgentDocumentChunk chunk) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("documentId", chunk.getDocumentId());
        result.put("taskId", chunk.getTaskId());
        result.put("planId", chunk.getPlanId());
        result.put("parentBlockId", chunk.getParentBlockId());
        result.put("chunkNo", chunk.getChunkNo());
        result.put("sourceType", chunk.getSourceType());
        result.put("sectionPath", chunk.getSectionPath());
        result.put("structureNodeId", chunk.getStructureNodeId());
        result.put("structureNodeType", chunk.getStructureNodeType());
        result.put("canonicalPath", chunk.getCanonicalPath());
        result.put("itemIndex", chunk.getItemIndex());
        result.put("chunkText", chunk.getChunkText());
        result.put("embeddingModel", embeddingModelName);
        return result;
    }

    private void addField(Map<String, Double> fields, String value, double boost) {
        if (value != null && !value.isBlank()) {
            fields.merge(value, boost, Double::sum);
        }
    }

    private void copyLong(JsonNode source, Map<String, Object> target, String field, String key) {
        if (source.path(field).isNumber()) {
            target.put(key, source.path(field).asLong());
        }
    }

    private void copyInt(JsonNode source, Map<String, Object> target, String field, String key) {
        if (source.path(field).isNumber()) {
            target.put(key, source.path(field).asInt());
        }
    }

    private boolean searchable(DocumentRetrieveRequest request) {
        return request != null && request.getRetrievalQuery() != null && !request.getRetrievalQuery().isBlank()
            && !request.resolvedDocumentIds().isEmpty() && !request.resolvedTaskIds().isEmpty();
    }

    private EmbeddingModel requireEmbeddingModel() {
        EmbeddingModel model = embeddingModels.getIfAvailable();
        if (model == null) {
            throw new IllegalStateException("未配置 EmbeddingModel");
        }
        return model;
    }
}
