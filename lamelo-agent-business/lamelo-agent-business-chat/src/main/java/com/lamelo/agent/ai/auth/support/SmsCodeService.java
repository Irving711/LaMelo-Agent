package com.lamelo.agent.ai.auth.support;

import com.lamelo.agent.exception.LaMeloAgentFrameException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Duration;

@Service
public class SmsCodeService {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration CODE_TTL = Duration.ofMinutes(5);
    private static final Duration SEND_INTERVAL = Duration.ofSeconds(60);
    private final StringRedisTemplate redis;
    private final SmsSender sender;

    public SmsCodeService(StringRedisTemplate redis, SmsSender sender) {
        this.redis = redis;
        this.sender = sender;
    }

    public void send(String mobile, String purpose) {
        String normalized = mobile.trim();
        if (!"login".equals(purpose) && !"register".equals(purpose) && !"reset".equals(purpose)) {
            throw new LaMeloAgentFrameException(400, "验证码用途不正确");
        }
        String throttleKey = "lamelo:auth:sms:throttle:" + normalized + ":" + purpose;
        Boolean accepted = redis.opsForValue().setIfAbsent(throttleKey, "1", SEND_INTERVAL);
        if (Boolean.FALSE.equals(accepted)) {
            throw new LaMeloAgentFrameException(429, "验证码发送过于频繁，请稍后再试");
        }
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        try {
            sender.send(normalized, purpose, code);
            redis.opsForValue().set(codeKey(normalized, purpose), code, CODE_TTL);
        } catch (RuntimeException exception) {
            // A failed provider call must not consume the 60-second retry window.
            redis.delete(throttleKey);
            throw exception;
        }
    }

    public void verifyAndConsume(String mobile, String purpose, String code) {
        String key = codeKey(mobile.trim(), purpose);
        String expected = redis.opsForValue().get(key);
        if (expected == null || !expected.equals(code.trim())) {
            throw new LaMeloAgentFrameException(400, "验证码错误或已过期");
        }
        redis.delete(key);
    }

    private String codeKey(String mobile, String purpose) {
        return "lamelo:auth:sms:code:" + mobile + ":" + purpose;
    }
}
