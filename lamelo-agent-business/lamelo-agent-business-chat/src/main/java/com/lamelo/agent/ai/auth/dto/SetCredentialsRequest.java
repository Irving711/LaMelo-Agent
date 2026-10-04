package com.lamelo.agent.ai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 为当前自动创建的账号设置用户名与密码。 */
@Data
public class SetCredentialsRequest {
    @NotBlank
    private String username;

    @NotBlank
    private String password;

    public SetCredentialsRequest() {
    }

    public SetCredentialsRequest(String username, String password) {
        this.username = username;
        this.password = password;
    }
}
