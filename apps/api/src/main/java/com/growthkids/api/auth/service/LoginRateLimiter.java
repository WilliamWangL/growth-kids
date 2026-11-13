package com.growthkids.api.auth.service;

import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.security.AuthProperties;
import java.time.Duration;
import java.util.Locale;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 登录暴力破解防护（规范 §71 Login brute force protection）。
 * Redis 计数：同一邮箱 + 同一 IP 双维度，窗口内失败次数超限返回 429。
 */
@Component
public class LoginRateLimiter {

    private static final String EMAIL_KEY_PREFIX = "rl:login:email:";
    private static final String IP_KEY_PREFIX = "rl:login:ip:";

    private final StringRedisTemplate redis;
    private final AuthProperties properties;

    public LoginRateLimiter(StringRedisTemplate redis, AuthProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    /** 超出限制则抛出 AUTH_TOO_MANY_ATTEMPTS（429）。 */
    public void assertNotLimited(String ip, String email) {
        long emailAttempts = counter(EMAIL_KEY_PREFIX + normalizeEmail(email));
        long ipAttempts = counter(IP_KEY_PREFIX + normalizeIp(ip));
        if (emailAttempts >= properties.getLoginMaxAttemptsPerEmail()
                || ipAttempts >= properties.getLoginMaxAttemptsPerIp()) {
            throw new ApiException(ApiErrorCode.AUTH_TOO_MANY_ATTEMPTS);
        }
    }

    /** 记录一次登录失败（邮箱 + IP 双计数，带窗口 TTL）。 */
    public void recordFailure(String ip, String email) {
        increment(EMAIL_KEY_PREFIX + normalizeEmail(email));
        increment(IP_KEY_PREFIX + normalizeIp(ip));
    }

    /** 登录成功后清除该邮箱的失败计数。 */
    public void clearFailures(String email) {
        redis.delete(EMAIL_KEY_PREFIX + normalizeEmail(email));
    }

    private long counter(String key) {
        String value = redis.opsForValue().get(key);
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    private void increment(String key) {
        Long count = redis.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redis.expire(key, Duration.ofSeconds(properties.getLoginAttemptWindowSeconds()));
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeIp(String ip) {
        return ip == null || ip.isBlank() ? "unknown" : ip.trim();
    }
}
