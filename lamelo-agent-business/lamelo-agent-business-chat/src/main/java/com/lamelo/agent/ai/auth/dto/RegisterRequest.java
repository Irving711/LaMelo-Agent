package com.lamelo.agent.ai.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class RegisterRequest {
    @NotBlank(message = "请输入账号")
    @Size(min = 3, max = 128, message = "账号长度必须为3到128位")
    private String username;

    @NotBlank(message = "请输入密码")
    @Size(min = 6, max = 128, message = "密码长度必须为6到128位")
    private String password;

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
}
