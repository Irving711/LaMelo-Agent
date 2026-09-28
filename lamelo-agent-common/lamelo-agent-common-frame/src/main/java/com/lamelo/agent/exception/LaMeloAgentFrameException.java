package com.lamelo.agent.exception;

import com.lamelo.agent.common.ApiResponse;
import com.lamelo.agent.enums.BaseCode;
import lombok.Data;

/**
 * @program: 企业级别深度设计 AI Agent。添加 阿星不是程序员 微信，添加时备注 super 来获取项目的完整资料
 * @description: 异常类
 * @author: 阿星不是程序员
 **/

@Data
public class LaMeloAgentFrameException extends BaseException {

	private Integer code;

	private String message;

	public LaMeloAgentFrameException() {
		super();
	}

	public LaMeloAgentFrameException(String message) {
		super(message);
	}

	public LaMeloAgentFrameException(String code, String message) {
		super(message);
		this.code = Integer.parseInt(code);
		this.message = message;
	}

	public LaMeloAgentFrameException(Integer code, String message) {
		super(message);
		this.code = code;
		this.message = message;
	}

	public LaMeloAgentFrameException(BaseCode baseCode) {
		super(baseCode.getMsg());
		this.code = baseCode.getCode();
		this.message = baseCode.getMsg();
	}

	public LaMeloAgentFrameException(ApiResponse apiResponse) {
		super(apiResponse.getMessage());
		this.code = apiResponse.getCode();
		this.message = apiResponse.getMessage();
	}

	public LaMeloAgentFrameException(Throwable cause) {
		super(cause);
	}

	public LaMeloAgentFrameException(String message, Throwable cause) {
		super(message, cause);
		this.message = message;
	}

	public LaMeloAgentFrameException(Integer code, String message, Throwable cause) {
		super(message, cause);
		this.code = code;
		this.message = message;
	}
}
