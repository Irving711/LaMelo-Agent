package com.lamelo.agent.ai.auth.service;

import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.dto.BindWechatRequest;
import com.lamelo.agent.ai.auth.dto.SetCredentialsRequest;
import com.lamelo.agent.ai.auth.dto.RegisterRequest;
import com.lamelo.agent.ai.auth.dto.SmsLoginRequest;
import com.lamelo.agent.ai.auth.dto.SmsPasswordResetRequest;
import com.lamelo.agent.ai.auth.dto.WechatCodeLoginRequest;
import com.lamelo.agent.ai.auth.vo.MiniProgramLoginVo;
import jakarta.servlet.http.HttpServletRequest;

public interface MiniProgramAuthService {
    MiniProgramLoginVo wechatLogin(WechatCodeLoginRequest request);
    MiniProgramLoginVo passwordLogin(AdminLoginRequest request);
    MiniProgramLoginVo register(RegisterRequest request);
    MiniProgramLoginVo smsLogin(SmsLoginRequest request);
    void resetPassword(SmsPasswordResetRequest request);
    MiniProgramLoginVo bind(BindWechatRequest request, HttpServletRequest currentRequest);
    MiniProgramLoginVo setCredentials(SetCredentialsRequest request, HttpServletRequest currentRequest);
    void unbind(HttpServletRequest currentRequest, String password);
}
