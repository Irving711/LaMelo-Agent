package com.lamelo.agent.ai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 绑定已有账号请求：把当前微信身份改挂到目标平台账号。 */
@Data
public class BindWechatRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    public BindWechatRequest() {
    }

    public BindWechatRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
