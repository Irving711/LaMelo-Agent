package com.lamelo.agent.ai.manage.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 配置属性
 * @author: 阿星不是程序员
 **/

@Data
@ConfigurationProperties(prefix = "app.manage")
public class DocumentManageProperties {

    private Minio minio = new Minio();

    private Chunk chunk = new Chunk();

    private StructureParsing structureParsing = new StructureParsing();

    private Qdrant qdrant = new Qdrant();

    private Neo4j neo4j = new Neo4j();

    @Data
    public static class Minio {
        private String endpoint = "http://127.0.0.1:9000";
        private String accessKey = "minioadmin";
        private String secretKey = "minioadmin";
        private String bucketName = "lamelo-agent-document";
        private String objectPrefix = "rag/document";
        private String parsedTextPrefix = "rag/parsed-text";
    }

    @Data
    public static class Chunk {
        private Integer recursiveMaxChars = 800;
        private Integer recursiveOverlapChars = 120;
        private Integer semanticMaxChars = 700;
        private Integer semanticMinChars = 240;
        private Double semanticSimilarityThreshold = 0.18D;
        private Boolean llmEnabled = Boolean.FALSE;
        private Integer llmMaxChars = 3500;
        private Boolean recommendLlmWhenLowQuality = Boolean.TRUE;
    }

    @Data
    public static class StructureParsing {

        private Boolean llmDisambiguationEnabled = Boolean.TRUE;

        private Integer maxAmbiguousSignalsPerCall = 8;

        private Integer contextWindowLines = 2;

        private Integer maxPlainHeadingChars = 32;

        private Double ambiguityConfidenceFloor = 0.45D;

        private Double ambiguityConfidenceCeil = 0.80D;
    }

    @Data
    public static class Qdrant {

        private String endpoint = "http://127.0.0.1:6333";

        private String apiKey = "";

        private Integer dimension = 1024;

        private Integer timeoutSeconds = 10;

        private String chunkCollection = "lamelo-agent-document-chunks";

        private String navigationCollection = "lamelo-agent-document-navigation";

        private String routeCollection = "lamelo-agent-knowledge-route";
    }

    @Data
    public static class Neo4j {

        private Boolean enabled = Boolean.FALSE;

        private String uri = "bolt://127.0.0.1:7687";

        private String username = "neo4j";

        private String password = "12345678";

        private String database = "neo4j";

        private Integer queryTimeoutSeconds = 5;
    }
}
