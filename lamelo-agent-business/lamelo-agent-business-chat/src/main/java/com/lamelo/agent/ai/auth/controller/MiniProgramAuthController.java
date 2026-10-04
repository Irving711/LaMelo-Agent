package com.lamelo.agent.ai.auth.controller;

import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.dto.BindWechatRequest;
import com.lamelo.agent.ai.auth.dto.SetCredentialsRequest;
import com.lamelo.agent.ai.auth.dto.RegisterRequest;
import com.lamelo.agent.ai.auth.dto.SmsLoginRequest;
import com.lamelo.agent.ai.auth.dto.SmsSendRequest;
import com.lamelo.agent.ai.auth.dto.SmsPasswordResetRequest;
import com.lamelo.agent.ai.auth.support.SmsCodeService;
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
    private final SmsCodeService smsCodeService;

    public MiniProgramAuthController(MiniProgramAuthService authService, SmsCodeService smsCodeService) {
        this.authService = authService;
        this.smsCodeService = smsCodeService;
    }

    @PostMapping("/wechat-login")
    public ApiResponse<MiniProgramLoginVo> wechatLogin(@Valid @RequestBody WechatCodeLoginRequest request) {
        return ApiResponse.ok(authService.wechatLogin(request));
    }

    @PostMapping("/password-login")
    public ApiResponse<MiniProgramLoginVo> passwordLogin(@Valid @RequestBody AdminLoginRequest request) {
        return ApiResponse.ok(authService.passwordLogin(request));
    }

    @PostMapping("/register")
    public ApiResponse<MiniProgramLoginVo> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    @PostMapping("/sms/send")
    public ApiResponse<Void> sendSms(@Valid @RequestBody SmsSendRequest request) {
        smsCodeService.send(request.getMobile(), request.getPurpose());
        return ApiResponse.ok();
    }

    @PostMapping("/sms-login")
    public ApiResponse<MiniProgramLoginVo> smsLogin(@Valid @RequestBody SmsLoginRequest request) {
        return ApiResponse.ok(authService.smsLogin(request));
    }

    @PostMapping("/password-reset")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody SmsPasswordResetRequest request) {
        authService.resetPassword(request);
        return ApiResponse.ok();
    }

    @PostMapping("/bind")
    public ApiResponse<MiniProgramLoginVo> bind(@Valid @RequestBody BindWechatRequest request,
                                                HttpServletRequest currentRequest) {
        return ApiResponse.ok(authService.bind(request, currentRequest));
    }

    @PostMapping("/credentials")
    public ApiResponse<MiniProgramLoginVo> setCredentials(@Valid @RequestBody SetCredentialsRequest request,
                                                          HttpServletRequest currentRequest) {
        return ApiResponse.ok(authService.setCredentials(request, currentRequest));
    }

    @PostMapping("/unbind")
    public ApiResponse<Void> unbind(@RequestBody AdminLoginRequest request, HttpServletRequest currentRequest) {
        authService.unbind(currentRequest, request == null ? null : request.getPassword());
        return ApiResponse.ok();
    }
}
