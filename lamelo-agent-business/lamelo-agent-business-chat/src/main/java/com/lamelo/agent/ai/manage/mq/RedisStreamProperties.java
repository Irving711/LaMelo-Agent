package com.lamelo.agent.ai.manage.mq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.manage.mq.redis-stream")
public class RedisStreamProperties {

    private boolean enabled = true;
    private String parseStream = "lamelo-agent:document:parse";
    private String indexStream = "lamelo-agent:document:index";
    private String parseGroup = "lamelo-agent:document:parse-group";
    private String indexGroup = "lamelo-agent:document:index-group";
    private String consumerName = "local-document-worker";
    private int parseConsumerConcurrency = 1;
    private int indexConsumerConcurrency = 1;
    private long claimIdleSeconds = 120;
    private long retryDelaySeconds = 30;
    private int maxRetryCount = 3;
    private long taskLeaseSeconds = 900;
    private long deadLetterRetentionDays = 7;
    private long acknowledgedHistoryMaxLength = 1000;

    /** Compatibility accessors retained for the existing recovery and consumer code. */
    public int getMaximumRetries() {
        return maxRetryCount;
    }

    public void setMaximumRetries(int maximumRetries) {
        this.maxRetryCount = maximumRetries;
    }

    public long getLeaseSeconds() {
        return taskLeaseSeconds;
    }

    public void setLeaseSeconds(long leaseSeconds) {
        this.taskLeaseSeconds = leaseSeconds;
    }

    public long getDeadLetterRetentionSeconds() {
        return deadLetterRetentionDays * 86400L;
    }

    public void setDeadLetterRetentionSeconds(long seconds) {
        this.deadLetterRetentionDays = Math.max(1L, seconds / 86400L);
    }
}
