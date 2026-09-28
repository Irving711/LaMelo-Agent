package com.lamelo.agent.ai.manage.service.impl;

import cn.hutool.core.util.StrUtil;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import com.lamelo.agent.ai.manage.config.DocumentManageProperties;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocument;
import com.lamelo.agent.ai.manage.data.LaMeloAgentDocumentProfile;
import com.lamelo.agent.ai.manage.data.LaMeloAgentKnowledgeScopeNode;
import com.lamelo.agent.ai.manage.data.LaMeloAgentKnowledgeTopicNode;
import com.lamelo.agent.ai.manage.data.LaMeloAgentTopicDocumentRelation;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentDocumentProfileMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentKnowledgeScopeNodeMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentKnowledgeTopicNodeMapper;
import com.lamelo.agent.ai.manage.mapper.LaMeloAgentTopicDocumentRelationMapper;
import com.lamelo.agent.ai.manage.model.index.KnowledgeRouteIndexRecord;
import com.lamelo.agent.ai.manage.qdrant.QdrantStore;
import com.lamelo.agent.ai.manage.qdrant.SparseTextEncoder;
import com.lamelo.agent.ai.manage.service.KnowledgeRouteIndexService;
import com.lamelo.agent.enums.BusinessStatus;
import com.lamelo.agent.enums.DocumentIndexStatusEnum;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 服务实现层
 * @author: 阿星不是程序员
 **/

@Slf4j
@AllArgsConstructor
@Service
public class QdrantKnowledgeRouteIndexService implements KnowledgeRouteIndexService {

    private static final Duration REFRESH_INTERVAL = Duration.ofMinutes(5);
    private static final AtomicLong LAST_REFRESH_TIME = new AtomicLong(0L);

    private final QdrantStore store;
    private final SparseTextEncoder encoder;
    private final ObjectMapper mapper;
    private final DocumentManageProperties properties;
    private final LaMeloAgentKnowledgeScopeNodeMapper scopeNodeMapper;
    private final LaMeloAgentKnowledgeTopicNodeMapper topicNodeMapper;
    private final LaMeloAgentDocumentMapper documentMapper;
    private final LaMeloAgentDocumentProfileMapper documentProfileMapper;
    private final LaMeloAgentTopicDocumentRelationMapper topicDocumentRelationMapper;
    

    @Override
    public void refreshIfNeeded() {
        long now = System.currentTimeMillis();
        long last = LAST_REFRESH_TIME.get();
        if (now - last < REFRESH_INTERVAL.toMillis()) {
            return;
        }
        if (!LAST_REFRESH_TIME.compareAndSet(last, now)) {
            return;
        }
        try {
            refreshAll();
        }
        catch (Exception exception) {
            LAST_REFRESH_TIME.set(0L);
            log.warn("刷新知识路由索引失败，将在下次查询时重试。", exception);
        }
    }

    @Override
    public List<RouteLexicalHit> search(String routingText, String entityType, int size) {
        if (StrUtil.isBlank(routingText) || StrUtil.isBlank(entityType)) {
            return List.of();
        }
        refreshIfNeeded();
        try {
            List<JsonNode> response = store.query(properties.getQdrant().getRouteCollection(), "lexical",
                encoder.encode(routingText), QdrantStore.match("entityType", entityType),
                Math.max(1, Math.min(size, 10)));
            List<RouteLexicalHit> hits = new ArrayList<>();
            for (JsonNode hit : response) {
                JsonNode source = hit.path("payload");
                hits.add(new RouteLexicalHit(
                    source.path("routeId").asText(), source.path("entityCode").asText(),
                    source.path("entityType").asText(),
                    source.path("documentId").isNumber() ? source.path("documentId").asLong() : null,
                    source.path("scopeCode").asText(""), source.path("topicCode").asText(""),
                    source.path("documentName").asText(""), hit.path("score").asDouble()
                ));
            }
            return hits;
        }
        catch (RuntimeException exception) {
            log.warn("知识路由 Qdrant 检索失败，退回语义匹配: entityType={}", entityType, exception);
            return List.of();
        }
    }

    @Override
    public void deleteDocumentRoute(Long documentId) {
        if (documentId == null) {
            return;
        }
        store.deleteByDocumentId(properties.getQdrant().getRouteCollection(), documentId);
    }

    private void refreshAll() {
        List<KnowledgeRouteIndexRecord> records = buildIndexRecords();
        List<Map<String, Object>> batch = new ArrayList<>();
        Set<String> activeIds = new HashSet<>();
        for (KnowledgeRouteIndexRecord record : records) {
            Map<String, Double> text = new LinkedHashMap<>();
            addText(text, record.getDisplayName(), 10D);
            addText(text, record.getAliasesText(), 8D);
            addText(text, record.getExamplesText(), 6D);
            addText(text, record.getSummaryText(), 5D);
            addText(text, record.getRouteText(), 4D);
            addText(text, record.getDescriptionText(), 3D);
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = mapper.convertValue(record, Map.class);
            String id = UUID.nameUUIDFromBytes(record.getRouteId().getBytes(StandardCharsets.UTF_8)).toString();
            activeIds.add(id);
            batch.add(Map.of("id", id, "vector", Map.of("lexical", encoder.encode(text)), "payload", payload));
            if (batch.size() == 100) {
                store.upsert(properties.getQdrant().getRouteCollection(), batch);
                batch.clear();
            }
        }
        store.upsert(properties.getQdrant().getRouteCollection(), batch);
        List<String> obsolete = new ArrayList<>();
        for (JsonNode point : store.scroll(properties.getQdrant().getRouteCollection())) {
            String id = point.path("id").asText();
            if (!activeIds.contains(id)) {
                obsolete.add(id);
                if (obsolete.size() == 100) {
                    store.deletePoints(properties.getQdrant().getRouteCollection(), obsolete);
                    obsolete.clear();
                }
            }
        }
        store.deletePoints(properties.getQdrant().getRouteCollection(), obsolete);
        log.info("知识路由索引刷新完成: recordCount={}", records.size());
    }

    private void addText(Map<String, Double> text, String value, double weight) {
        if (StrUtil.isNotBlank(value)) {
            text.merge(value, weight, Double::sum);
        }
    }

    private List<KnowledgeRouteIndexRecord> buildIndexRecords() {
        List<KnowledgeRouteIndexRecord> records = new ArrayList<>();
        List<LaMeloAgentKnowledgeScopeNode> scopes = scopeNodeMapper.selectList(new LambdaQueryWrapper<LaMeloAgentKnowledgeScopeNode>()
            .eq(LaMeloAgentKnowledgeScopeNode::getStatus, BusinessStatus.YES.getCode()));
        List<LaMeloAgentKnowledgeTopicNode> topics = topicNodeMapper.selectList(new LambdaQueryWrapper<LaMeloAgentKnowledgeTopicNode>()
            .eq(LaMeloAgentKnowledgeTopicNode::getStatus, BusinessStatus.YES.getCode()));
        List<LaMeloAgentDocument> documents = documentMapper.selectList(new LambdaQueryWrapper<LaMeloAgentDocument>()
            .eq(LaMeloAgentDocument::getStatus, BusinessStatus.YES.getCode())
            .eq(LaMeloAgentDocument::getIndexStatus, DocumentIndexStatusEnum.BUILD_SUCCESS.getCode())
            .isNotNull(LaMeloAgentDocument::getLastIndexTaskId));
        Map<Long, LaMeloAgentDocumentProfile> profileMap = documentProfileMapper.selectList(new LambdaQueryWrapper<LaMeloAgentDocumentProfile>()
                .eq(LaMeloAgentDocumentProfile::getStatus, BusinessStatus.YES.getCode())
                .eq(LaMeloAgentDocumentProfile::getProfileStatus, 2))
            .stream()
            .collect(Collectors.toMap(LaMeloAgentDocumentProfile::getDocumentId, item -> item, (left, right) -> right));
        Map<String, List<LaMeloAgentKnowledgeTopicNode>> topicByScope = topics.stream()
            .collect(Collectors.groupingBy(LaMeloAgentKnowledgeTopicNode::getScopeCode));
        Map<String, List<LaMeloAgentTopicDocumentRelation>> relationByTopic = topicDocumentRelationMapper.selectList(
                new LambdaQueryWrapper<LaMeloAgentTopicDocumentRelation>()
                    .eq(LaMeloAgentTopicDocumentRelation::getStatus, BusinessStatus.YES.getCode()))
            .stream()
            .collect(Collectors.groupingBy(LaMeloAgentTopicDocumentRelation::getTopicCode));

        for (LaMeloAgentKnowledgeScopeNode scope : scopes) {
            List<String> scopeTags = new ArrayList<>();
            topicByScope.getOrDefault(scope.getScopeCode(), List.of()).forEach(topic -> {
                addUnique(scopeTags, topic.getTopicName());
                parseCommaText(topic.getAliases()).forEach(item -> addUnique(scopeTags, item));
            });
            records.add(KnowledgeRouteIndexRecord.builder()
                .routeId("scope:" + scope.getScopeCode())
                .entityType("scope")
                .entityCode(scope.getScopeCode())
                .scopeCode(scope.getScopeCode())
                .scopeName(scope.getScopeName())
                .displayName(safeText(scope.getScopeName()))
                .descriptionText(safeText(scope.getDescription()))
                .aliasesText(safeText(scope.getAliases()))
                .examplesText(safeText(scope.getExamples()))
                .summaryText(safeText(scope.getDescription()))
                .routeText(join(scope.getScopeName(), scope.getDescription(), scope.getAliases(), scope.getExamples()))
                .entityTerms(extractEntityTerms(join(scope.getScopeCode(), scope.getScopeName(), scope.getAliases())))
                .tags(scopeTags)
                .build());
        }

        for (LaMeloAgentKnowledgeTopicNode topic : topics) {
            List<String> tags = new ArrayList<>();
            parseJsonArray(topic.getExamples()).forEach(item -> addUnique(tags, item));
            parseCommaText(topic.getAliases()).forEach(item -> addUnique(tags, item));
            records.add(KnowledgeRouteIndexRecord.builder()
                .routeId("topic:" + topic.getTopicCode())
                .entityType("topic")
                .entityCode(topic.getTopicCode())
                .scopeCode(topic.getScopeCode())
                .topicCode(topic.getTopicCode())
                .topicName(topic.getTopicName())
                .displayName(safeText(topic.getTopicName()))
                .descriptionText(safeText(topic.getDescription()))
                .aliasesText(safeText(topic.getAliases()))
                .examplesText(safeText(topic.getExamples()))
                .summaryText(join(topic.getAnswerShape(), topic.getExecutionPreference()))
                .routeText(join(
                    topic.getTopicCode(),
                    topic.getTopicName(),
                    topic.getDescription(),
                    topic.getAliases(),
                    topic.getExamples(),
                    topic.getAnswerShape(),
                    topic.getExecutionPreference()))
                .entityTerms(extractEntityTerms(join(topic.getTopicCode(), topic.getTopicName(), topic.getAliases())))
                .tags(tags)
                .build());
        }

        Map<Long, LaMeloAgentKnowledgeTopicNode> topicDocumentMap = new LinkedHashMap<>();
        for (LaMeloAgentKnowledgeTopicNode topic : topics) {
            for (LaMeloAgentTopicDocumentRelation relation : relationByTopic.getOrDefault(topic.getTopicCode(), List.of())) {
                topicDocumentMap.put(relation.getDocumentId(), topic);
            }
        }

        for (LaMeloAgentDocument document : documents) {
            LaMeloAgentDocumentProfile profile = profileMap.get(document.getId());
            List<String> tags = new ArrayList<>();
            parseCommaText(document.getDocumentTags()).forEach(item -> addUnique(tags, item));
            if (profile != null) {
                parseJsonArray(profile.getCoreTopics()).forEach(item -> addUnique(tags, item));
                parseJsonArray(profile.getExampleQuestions()).forEach(item -> addUnique(tags, item));
            }
            relationByTopic.forEach((topicCode, relations) -> relations.stream()
                .filter(relation -> document.getId().equals(relation.getDocumentId()))
                .findFirst()
                .ifPresent(relation -> {
                    LaMeloAgentKnowledgeTopicNode topic = topics.stream()
                        .filter(item -> topicCode.equals(item.getTopicCode()))
                        .findFirst()
                        .orElse(null);
                    if (topic != null) {
                        addUnique(tags, topic.getTopicName());
                        parseCommaText(topic.getAliases()).forEach(item -> addUnique(tags, item));
                    }
                }));
            records.add(KnowledgeRouteIndexRecord.builder()
                .routeId("document:" + document.getId())
                .entityType("document")
                .entityCode(String.valueOf(document.getId()))
                .documentId(document.getId())
                .scopeCode(safeText(document.getKnowledgeScopeCode()))
                .scopeName(safeText(document.getKnowledgeScopeName()))
                .documentName(safeText(document.getDocumentName()))
                .businessCategory(safeText(document.getBusinessCategory()))
                .displayName(safeText(document.getDocumentName()))
                .descriptionText(profile == null ? "" : safeText(profile.getDocumentType()))
                .aliasesText("")
                .examplesText(profile == null ? "" : joinJsonLike(parseJsonArray(profile.getExampleQuestions())))
                .summaryText(profile == null ? "" : safeText(profile.getDocumentSummary()))
                .routeText(join(
                    document.getDocumentName(),
                    document.getKnowledgeScopeCode(),
                    document.getKnowledgeScopeName(),
                    document.getBusinessCategory(),
                    document.getDocumentTags(),
                    profile == null ? "" : profile.getDocumentSummary(),
                    profile == null ? "" : profile.getCoreTopics(),
                    profile == null ? "" : profile.getExampleQuestions(),
                    profile == null ? "" : profile.getDocumentType()
                ))
                .entityTerms(extractEntityTerms(join(document.getDocumentName(), document.getDocumentTags(), document.getKnowledgeScopeName())))
                .tags(tags)
                .build());
        }
        return records;
    }

    private List<String> extractEntityTerms(String text) {
        if (StrUtil.isBlank(text)) {
            return List.of();
        }
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        String normalized = text.trim();
        for (String part : normalized.split("[\\s、，,；;：:（）()]+")) {
            String trimmed = part.trim();
            if (trimmed.length() < 2) {
                continue;
            }
            if (trimmed.matches(".*[A-Za-z].*") || trimmed.matches(".*\\d.*")) {
                terms.add(trimmed);
                terms.add(trimmed.toUpperCase(Locale.ROOT));
                terms.add(trimmed.toLowerCase(Locale.ROOT));
            }
        }
        return new ArrayList<>(terms).stream().limit(20).toList();
    }

    private List<String> parseJsonArray(String raw) {
        String normalized = StrUtil.blankToDefault(raw, "").trim();
        if (normalized.isBlank() || "[]".equals(normalized)) {
            return List.of();
        }
        String body = normalized.replace("[", "").replace("]", "");
        if (body.isBlank()) {
            return List.of();
        }
        return List.of(body.split(",")).stream()
            .map(item -> item.replace("\"", "").trim())
            .filter(StrUtil::isNotBlank)
            .toList();
    }

    private List<String> parseCommaText(String raw) {
        String normalized = StrUtil.blankToDefault(raw, "").trim();
        if (normalized.isBlank()) {
            return List.of();
        }
        return List.of(normalized.split(",")).stream()
            .map(String::trim)
            .filter(StrUtil::isNotBlank)
            .toList();
    }

    private String joinJsonLike(List<String> values) {
        return values == null || values.isEmpty() ? "" : String.join(" ", values);
    }

    private String join(String... values) {
        return Arrays.stream(values)
            .filter(StrUtil::isNotBlank)
            .collect(Collectors.joining(" "));
    }

    private void addUnique(List<String> values, String value) {
        if (StrUtil.isBlank(value)) {
            return;
        }
        if (!values.contains(value.trim())) {
            values.add(value.trim());
        }
    }

    private String safeText(String text) {
        return text == null ? "" : text.trim();
    }
}
