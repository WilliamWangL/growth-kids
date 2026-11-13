package com.growthkids.api.auth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.growthkids.api.auth.dto.AuthResponse;
import com.growthkids.api.auth.dto.LoginRequest;
import com.growthkids.api.auth.dto.LogoutRequest;
import com.growthkids.api.auth.dto.RefreshTokenRequest;
import com.growthkids.api.auth.dto.RegisterRequest;
import com.growthkids.api.auth.dto.UserSummary;
import com.growthkids.api.auth.entity.RefreshToken;
import com.growthkids.api.auth.mapper.RefreshTokenMapper;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.security.AuthProperties;
import com.growthkids.api.common.security.JwtService;
import com.growthkids.api.common.security.PasswordHasher;
import com.growthkids.api.user.entity.User;
import com.growthkids.api.user.mapper.UserMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 认证服务（规范 §24-§27、§71）：
 * 注册 / 登录（暴力破解防护）/ refresh token rotation / 登出。
 * refresh token 为 64 字节随机不透明串，数据库仅存 SHA-256 摘要；
 * 已轮换 token 再次被使用判定为泄露，撤销该用户全部 refresh token。
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final int REFRESH_TOKEN_BYTES = 64;

    private final UserMapper userMapper;
    private final RefreshTokenMapper refreshTokenMapper;
    private final RefreshTokenRevoker refreshTokenRevoker;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final LoginRateLimiter rateLimiter;
    private final AuthProperties authProperties;
    private final SecureRandom secureRandom = new SecureRandom();

    /** 用于不存在用户时的等价耗时校验，抵御时序探测。 */
    private final String dummyHash;

    public AuthService(
            UserMapper userMapper,
            RefreshTokenMapper refreshTokenMapper,
            RefreshTokenRevoker refreshTokenRevoker,
            PasswordHasher passwordHasher,
            JwtService jwtService,
            LoginRateLimiter rateLimiter,
            AuthProperties authProperties) {
        this.userMapper = userMapper;
        this.refreshTokenMapper = refreshTokenMapper;
        this.refreshTokenRevoker = refreshTokenRevoker;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.rateLimiter = rateLimiter;
        this.authProperties = authProperties;
        this.dummyHash = passwordHasher.hash("growthkids-timing-defense");
    }

    /** 注册（规范 §24）。 */
    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        Long existing =
                userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (existing != null && existing > 0) {
            throw new ApiException(ApiErrorCode.AUTH_EMAIL_EXISTS);
        }
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordHasher.hash(request.password()));
        user.setDisplayName(normalizeDisplayName(request.displayName()));
        user.setTimezone("UTC");
        user.setLocale("en-US");
        user.setStatus(User.STATUS_ACTIVE);
        user.setRole(User.ROLE_USER);
        try {
            userMapper.insert(user);
        } catch (DuplicateKeyException ex) {
            // 并发注册同一邮箱：唯一索引兜底
            throw new ApiException(ApiErrorCode.AUTH_EMAIL_EXISTS);
        }
        return issueTokens(user);
    }

    /** 登录（规范 §25），含暴力破解防护。 */
    @Transactional
    public AuthResponse login(LoginRequest request, String ip) {
        String email = normalizeEmail(request.email());
        rateLimiter.assertNotLimited(ip, email);
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, email));
        if (user == null) {
            passwordHasher.matches(request.password(), dummyHash);
            rateLimiter.recordFailure(ip, email);
            throw new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (!passwordHasher.matches(request.password(), user.getPasswordHash())) {
            rateLimiter.recordFailure(ip, email);
            throw new ApiException(ApiErrorCode.AUTH_INVALID_CREDENTIALS);
        }
        if (!User.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new ApiException(ApiErrorCode.AUTH_ACCOUNT_DISABLED);
        }
        rateLimiter.clearFailures(email);
        return issueTokens(user);
    }

    /** 刷新并轮换 refresh token（规范 §26、§71 rotation）。 */
    @Transactional
    public AuthResponse refresh(RefreshTokenRequest request) {
        String tokenHash = sha256Hex(request.refreshToken());
        RefreshToken stored =
                refreshTokenMapper.selectOne(
                        new LambdaQueryWrapper<RefreshToken>().eq(RefreshToken::getTokenHash, tokenHash));
        if (stored == null) {
            throw new ApiException(ApiErrorCode.AUTH_INVALID_TOKEN);
        }
        if (stored.getRevokedAt() != null) {
            if (stored.getReplacedByTokenHash() != null) {
                // 已轮换 token 被复用：判定泄露，撤销该用户全部有效 token（独立事务提交）
                log.warn("Refresh token reuse detected, revoking all tokens for userId={}", stored.getUserId());
                refreshTokenRevoker.revokeAllForUser(stored.getUserId());
                throw new ApiException(ApiErrorCode.AUTH_INVALID_TOKEN);
            }
            // 已登出 token 再次使用
            throw new ApiException(ApiErrorCode.AUTH_INVALID_TOKEN);
        }
        if (stored.getExpiresAt().isBefore(LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ApiException(ApiErrorCode.AUTH_TOKEN_EXPIRED);
        }
        User user = userMapper.selectById(stored.getUserId());
        if (user == null || !User.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED);
        }

        String newRawToken = generateRefreshToken();
        String newHash = sha256Hex(newRawToken);
        int updated =
                refreshTokenMapper.update(
                        null,
                        new LambdaUpdateWrapper<RefreshToken>()
                                .eq(RefreshToken::getId, stored.getId())
                                .isNull(RefreshToken::getRevokedAt)
                                .set(RefreshToken::getRevokedAt, LocalDateTime.now(ZoneOffset.UTC))
                                .set(RefreshToken::getReplacedByTokenHash, newHash));
        if (updated == 0) {
            // CAS 失败：并发重复轮换，按泄露处理（独立事务提交撤销）
            log.warn("Concurrent refresh detected, revoking all tokens for userId={}", stored.getUserId());
            refreshTokenRevoker.revokeAllForUser(stored.getUserId());
            throw new ApiException(ApiErrorCode.AUTH_INVALID_TOKEN);
        }
        insertRefreshToken(user.getId(), newHash);
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        return new AuthResponse(UserSummary.from(user), accessToken, newRawToken);
    }

    /** 登出：撤销 refresh token（规范 §27，幂等）。 */
    @Transactional
    public void logout(LogoutRequest request) {
        String tokenHash = sha256Hex(request.refreshToken());
        refreshTokenMapper.update(
                null,
                new LambdaUpdateWrapper<RefreshToken>()
                        .eq(RefreshToken::getTokenHash, tokenHash)
                        .isNull(RefreshToken::getRevokedAt)
                        .set(RefreshToken::getRevokedAt, LocalDateTime.now(ZoneOffset.UTC)));
    }

    private AuthResponse issueTokens(User user) {
        String rawToken = generateRefreshToken();
        insertRefreshToken(user.getId(), sha256Hex(rawToken));
        String accessToken = jwtService.issueAccessToken(user.getId(), user.getRole());
        return new AuthResponse(UserSummary.from(user), accessToken, rawToken);
    }

    private void insertRefreshToken(Long userId, String tokenHash) {
        RefreshToken token = new RefreshToken();
        token.setUserId(userId);
        token.setTokenHash(tokenHash);
        token.setExpiresAt(
                LocalDateTime.now(ZoneOffset.UTC).plusSeconds(authProperties.getRefreshTokenTtlSeconds()));
        refreshTokenMapper.insert(token);
    }

    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }

    private static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String normalizeDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }
        String trimmed = displayName.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
