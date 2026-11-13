package com.growthkids.api.photo.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** 媒体签名 token 单元测试（TASK-013，§120 规则 29 短期签名 URL）。 */
class StorageSignerTest {

    private static final String SECRET = "unit-test-signing-secret";
    private static final Instant EXPIRY = Instant.now().plusSeconds(600);

    @Test
    void signAndVerifyRoundTrip() {
        String token = StorageSigner.sign(SECRET, "upload:children/1/2026/10/a.jpg", EXPIRY);
        assertEquals(
                Optional.of("upload:children/1/2026/10/a.jpg"),
                StorageSigner.verify(SECRET, token));
    }

    @Test
    void tamperedSignatureRejected() {
        String token = StorageSigner.sign(SECRET, "view:1:original", EXPIRY);
        String tampered = token.substring(0, token.length() - 2) + "xx";
        assertTrue(StorageSigner.verify(SECRET, tampered).isEmpty());
    }

    @Test
    void tamperedPayloadRejected() {
        // 用另一 subject 的 payload 替换签名部分 → 签名不匹配
        String tokenA = StorageSigner.sign(SECRET, "view:1:original", EXPIRY);
        String tokenB = StorageSigner.sign(SECRET, "view:2:original", EXPIRY);
        int dotA = tokenA.indexOf('.');
        String mixed = tokenB.substring(0, tokenB.indexOf('.')) + tokenA.substring(dotA);
        assertTrue(StorageSigner.verify(SECRET, mixed).isEmpty());
    }

    @Test
    void expiredTokenRejected() {
        String token =
                StorageSigner.sign(SECRET, "upload:children/1/a.jpg", Instant.now().minusSeconds(1));
        assertTrue(StorageSigner.verify(SECRET, token).isEmpty());
    }

    @Test
    void wrongSecretRejected() {
        String token = StorageSigner.sign(SECRET, "view:1:original", EXPIRY);
        assertTrue(StorageSigner.verify("another-secret", token).isEmpty());
    }

    @Test
    void malformedTokensRejected() {
        assertTrue(StorageSigner.verify(SECRET, null).isEmpty());
        assertTrue(StorageSigner.verify(SECRET, "").isEmpty());
        assertTrue(StorageSigner.verify(SECRET, "   ").isEmpty());
        assertTrue(StorageSigner.verify(SECRET, "not-a-token").isEmpty());
        // 非法 base64url payload
        assertTrue(StorageSigner.verify(SECRET, "!!!.???").isEmpty());
        // 合法签名但 payload 非预期格式：手工构造“签名与 payload 不对应”的场景
        String garbagePayload =
                Base64.getUrlEncoder()
                        .withoutPadding()
                        .encodeToString("x".getBytes(StandardCharsets.UTF_8));
        assertTrue(StorageSigner.verify(SECRET, garbagePayload + "." + "garbage").isEmpty());
    }
}
