package com.lamelo.agent.ai.auth.config;

import lombok.Data;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 后台管理登录配置。
 */
@Data
@ConfigurationProperties(prefix = "app.admin-auth")
public class AdminAuthProperties {

    /**
     * 后台登录用户名。
     */
    private String username = "admin";

    /**
     * 后台登录密码。
     */
    private String password = "admin123";

    /**
     * BCrypt 密码哈希；部署环境设置后优先于本地明文密码。
     */
    private String passwordHash;

    /**
     * JWT 签名密钥。
     */
    private String tokenSecret = "lamelo-agent-admin-token-secret-change-me";

    /**
     * token 有效期，单位分钟。
     */
    private Long tokenExpireMinutes = 720L;

    private String wechatAppId;

    @ToString.Exclude
    private String wechatAppSecret;

    private String wechatCode2SessionUrl = "https://api.weixin.qq.com/sns/jscode2session";

    private int wechatConnectTimeoutMs = 3000;

    private int wechatReadTimeoutMs = 5000;

    private String adminRoleCode = "ADMIN";
}
