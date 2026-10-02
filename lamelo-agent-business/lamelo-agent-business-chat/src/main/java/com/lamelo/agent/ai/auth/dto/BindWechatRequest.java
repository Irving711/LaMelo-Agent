package com.lamelo.agent.ai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BindWechatRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    @NotBlank
    private String code;

    public BindWechatRequest() {
    }

    public BindWechatRequest(String username, String password, String code) {
        this.username = username;
        this.password = password;
        this.code = code;
    }
}
