package com.lamelo.agent.ai.auth.service;

import com.lamelo.agent.ai.auth.dto.AdminLoginRequest;
import com.lamelo.agent.ai.auth.dto.BindWechatRequest;
import com.lamelo.agent.ai.auth.dto.WechatCodeLoginRequest;
import com.lamelo.agent.ai.auth.vo.MiniProgramLoginVo;
import jakarta.servlet.http.HttpServletRequest;

public interface MiniProgramAuthService {
    MiniProgramLoginVo wechatLogin(WechatCodeLoginRequest request);
    MiniProgramLoginVo passwordLogin(AdminLoginRequest request);
    void bind(BindWechatRequest request, HttpServletRequest currentRequest);
    void unbind(HttpServletRequest currentRequest, String password);
}
