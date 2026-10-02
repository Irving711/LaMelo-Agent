package com.lamelo.agent.ai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class WechatCodeLoginRequest {
    @NotBlank
    private String code;

    public WechatCodeLoginRequest() {
    }

    public WechatCodeLoginRequest(String code) {
        this.code = code;
    }
}
