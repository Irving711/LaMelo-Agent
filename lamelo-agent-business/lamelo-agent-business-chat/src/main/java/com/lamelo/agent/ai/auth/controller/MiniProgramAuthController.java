package com.lamelo.agent.ai.auth.controller;

import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.dto.BindWechatRequest;
import com.lamelo.agent.ai.auth.dto.WechatCodeLoginRequest;
import com.lamelo.agent.ai.auth.service.MiniProgramAuthService;
import com.lamelo.agent.ai.auth.vo.MiniProgramLoginVo;
import com.lamelo.agent.common.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/miniapp/auth")
public class MiniProgramAuthController {
    private final MiniProgramAuthService authService;

    public MiniProgramAuthController(MiniProgramAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/wechat-login")
    public ApiResponse<MiniProgramLoginVo> wechatLogin(@Valid @RequestBody WechatCodeLoginRequest request) {
        return ApiResponse.ok(authService.wechatLogin(request));
    }

    @PostMapping("/password-login")
    public ApiResponse<MiniProgramLoginVo> passwordLogin(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.ok(authService.passwordLogin(request));
    }

    @PostMapping("/bind")
    public ApiResponse<Void> bind(@Valid @RequestBody BindWechatRequest request, HttpServletRequest currentRequest) {
        authService.bind(request, currentRequest);
        return ApiResponse.ok();
    }

    @PostMapping("/unbind")
    public ApiResponse<Void> unbind(@RequestBody AdminLoginRequest request, HttpServletRequest currentRequest) {
        authService.unbind(currentRequest, request == null ? null : request.getPassword());
        return ApiResponse.ok();
    }
}
