package com.growthkids.api.photo.storage;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 媒体 URL 签名（TASK-013，§120 规则 29 短期签名 URL）：
 * token = base64url(subject:expiry) + "." + base64url(HMAC-SHA256)；
 * subject 约定：upload:{storageKey} / view:{photoId}:{variant}。
 */
public final class StorageSigner {

    private StorageSigner() {}

    public static String sign(String secret, String subject, Instant expiresAt) {
        String payload = subject + ":" + expiresAt.getEpochSecond();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(payload.getBytes(StandardCharsets.UTF_8))
                + "."
                + hmac(secret, payload);
    }

    /** 验证签名与有效期；有效返回 subject，无效/篡改/过期返回 empty（时序安全比较）。 */
    public static Optional<String> verify(String secret, String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0) {
            return Optional.empty();
        }
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(token.substring(0, dot)), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(
                hmac(secret, payload).getBytes(StandardCharsets.UTF_8),
                token.substring(dot + 1).getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }
        int colon = payload.lastIndexOf(':');
        if (colon <= 0) {
            return Optional.empty();
        }
        long expiry;
        try {
            expiry = Long.parseLong(payload.substring(colon + 1));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
        if (expiry < Instant.now().getEpochSecond()) {
            return Optional.empty();
        }
        return Optional.of(payload.substring(0, colon));
    }

    private static String hmac(String secret, String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return Base64.getUrlEncoder()
                    .withoutPadding()
                    .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}

