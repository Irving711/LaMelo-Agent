package com.lamelo.agent.ai.auth.data;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WechatIdentity {
    private Long id;
    private String openid;
    private String unionid;
    private Long userId;
    private LocalDateTime createTime;
    private LocalDateTime editTime;
    private Integer status;
}
