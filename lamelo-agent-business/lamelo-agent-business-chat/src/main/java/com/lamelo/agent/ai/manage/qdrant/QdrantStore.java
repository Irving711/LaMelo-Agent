package com.lamelo.agent.ai.manage.qdrant;

import com.fasterxml.jackson.databind.JsonNode;
import com.lamelo.agent.ai.manage.config.DocumentManageProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class QdrantStore {

    private final RestClient client;
    private final DocumentManageProperties.Qdrant settings;

    public QdrantStore(DocumentManageProperties properties) {
        settings = properties.getQdrant();
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(settings.getTimeoutSeconds() * 1000);
        factory.setReadTimeout(settings.getTimeoutSeconds() * 1000);
        RestClient.Builder builder = RestClient.builder()
            .baseUrl(settings.getEndpoint().replaceAll("/+$", ""))
            .requestFactory(factory);
        if (settings.getApiKey() != null && !settings.getApiKey().isBlank()) {
            builder.defaultHeader("api-key", settings.getApiKey());
        }
        client = builder.build();
    }

    @PostConstruct
    public void ensureCollections() {
        ensureCollection(settings.getChunkCollection(), true, List.of("documentId", "taskId", "structureNodeId", "itemIndex"));
        ensureCollection(settings.getNavigationCollection(), false, List.of("documentId", "nodeType"));
        ensureCollection(settings.getRouteCollection(), false, List.of("documentId", "entityType"));
    }

    private void ensureCollection(String alias, boolean dense, List<String> indexedFields) {
        try {
            client.get().uri("/collections/{name}", alias).retrieve().toBodilessEntity();
            return;
        }
        catch (HttpClientErrorException.NotFound ignored) {
            // First boot creates an alias over a versioned physical collection.
        }
        String physical = alias + "-v1";
        try {
            client.get().uri("/collections/{name}", physical).retrieve().toBodilessEntity();
            createAlias(alias, physical);
            return;
        }
        catch (HttpClientErrorException.NotFound ignored) {
            // The first run creates the physical collection below.
        }
        Map<String, Object> vectors = dense
            ? Map.of("dense", Map.of("size", settings.getDimension(), "distance", "Cosine", "on_disk", true))
            : Map.of();
        Map<String, Object> sparse = Map.of("lexical", Map.of(
            "index", Map.of("on_disk", true), "modifier", "idf"));
        Map<String, Object> config = Map.of(
            "vectors", vectors,
            "sparse_vectors", sparse,
            "on_disk_payload", true,
            "hnsw_config", Map.of("on_disk", true));
        client.put().uri("/collections/{name}", physical).body(config).retrieve().toBodilessEntity();
        for (String field : indexedFields) {
            String type = field.equals("nodeType") || field.equals("entityType") ? "keyword" : "integer";
            client.put().uri("/collections/{name}/index?wait=true", physical)
                .body(Map.of("field_name", field, "field_schema", type)).retrieve().toBodilessEntity();
        }
        createAlias(alias, physical);
    }

    private void createAlias(String alias, String physical) {
        client.post().uri("/collections/aliases")
            .body(Map.of("actions", List.of(Map.of("create_alias", Map.of("collection_name", physical, "alias_name", alias)))))
            .retrieve().toBodilessEntity();
    }

    public void upsert(String collection, List<Map<String, Object>> points) {
        if (!points.isEmpty()) {
            client.put().uri("/collections/{name}/points?wait=true", collection)
                .body(Map.of("points", points)).retrieve().toBodilessEntity();
        }
    }

    public void deleteByDocumentId(String collection, long documentId) {
        client.post().uri("/collections/{name}/points/delete?wait=true", collection)
            .body(Map.of("filter", match("documentId", documentId)))
            .retrieve().toBodilessEntity();
    }

    public List<JsonNode> query(String collection, String vectorName, Object vector,
                                Map<String, Object> filter, int limit) {
        return query(collection, vectorName, vector, filter, limit, 0);
    }

    public List<JsonNode> query(String collection, String vectorName, Object vector,
                                Map<String, Object> filter, int limit, int offset) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", vector);
        body.put("using", vectorName);
        body.put("filter", filter);
        body.put("limit", limit);
        body.put("offset", offset);
        body.put("with_payload", true);
        JsonNode response = client.post().uri("/collections/{name}/points/query", collection)
            .body(body).retrieve().body(JsonNode.class);
        if (response == null || !response.path("status").asText().equals("ok")) {
            throw new IllegalStateException("Qdrant 查询失败");
        }
        JsonNode points = response.path("result").path("points");
        List<JsonNode> result = new ArrayList<>();
        if (points.isArray()) {
            points.forEach(result::add);
        }
        return result;
    }

    public List<JsonNode> scroll(String collection) {
        List<JsonNode> points = new ArrayList<>();
        JsonNode offset = null;
        do {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("limit", 100);
            body.put("with_payload", true);
            body.put("with_vector", false);
            if (offset != null) {
                body.put("offset", offset);
            }
            JsonNode response = client.post().uri("/collections/{name}/points/scroll", collection)
                .body(body).retrieve().body(JsonNode.class);
            if (response == null || !"ok".equals(response.path("status").asText())) {
                throw new IllegalStateException("Qdrant scroll 失败");
            }
            response.path("result").path("points").forEach(points::add);
            offset = response.path("result").path("next_page_offset");
        } while (offset != null && !offset.isNull() && !offset.isMissingNode());
        return points;
    }

    public void deletePoints(String collection, List<String> ids) {
        if (!ids.isEmpty()) {
            client.post().uri("/collections/{name}/points/delete?wait=true", collection)
                .body(Map.of("points", ids)).retrieve().toBodilessEntity();
        }
    }

    public static Map<String, Object> match(String field, Object value) {
        return Map.of("must", List.of(Map.of("key", field, "match", Map.of("value", value))));
    }

    public static Map<String, Object> terms(String field, List<?> values) {
        return Map.of("key", field, "match", Map.of("any", values));
    }

    public static Map<String, Object> filter(List<Map<String, Object>> must) {
        return Map.of("must", must);
    }
}
