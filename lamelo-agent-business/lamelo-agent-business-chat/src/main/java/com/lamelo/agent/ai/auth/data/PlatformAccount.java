package com.lamelo.agent.ai.auth.data;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PlatformAccount {
    private Long id;
    private String username;
    private String passwordHash;
    private Boolean enabled;
    private LocalDateTime createTime;
    private LocalDateTime editTime;
    private Integer status;
}
