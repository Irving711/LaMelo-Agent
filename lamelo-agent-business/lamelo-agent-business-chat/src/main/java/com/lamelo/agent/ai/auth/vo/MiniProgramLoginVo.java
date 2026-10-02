package com.lamelo.agent.ai.auth.vo;

import lombok.Data;

import java.util.List;

@Data
public class MiniProgramLoginVo {
    private String token;
    private String username;
    private List<String> roles;
    private boolean needsBinding;
    private Long expireMinutes;

    public MiniProgramLoginVo() {
    }

    public MiniProgramLoginVo(String token, String username, List<String> roles,
                              boolean needsBinding, Long expireMinutes) {
        this.token = token;
        this.username = username;
        this.roles = roles;
        this.needsBinding = needsBinding;
        this.expireMinutes = expireMinutes;
    }
}
