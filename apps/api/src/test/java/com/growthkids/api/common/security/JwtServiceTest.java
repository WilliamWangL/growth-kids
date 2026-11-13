package com.growthkids.api.common.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import org.junit.jupiter.api.Test;

/** JwtService 单元测试（规范 §71：JWT access token）。 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-access-secret-0123456789-abcdef";

    private static AuthProperties props(long ttlSeconds) {
        AuthProperties properties = new AuthProperties();
        properties.setJwtAccessSecret(SECRET);
        properties.setIssuer("growthkids");
        properties.setAccessTokenTtlSeconds(ttlSeconds);
        return properties;
    }

    @Test
    void issueAndParseRoundTrip() {
        JwtService service = new JwtService(props(900));
        String token = service.issueAccessToken(42L, "admin");
        AuthPrincipal principal = service.parseAccessToken(token);
        assertEquals(42L, principal.userId());
        assertEquals("admin", principal.role());
        assertTrue(principal.isAdmin());
    }

    @Test
    void rejectsTamperedToken() {
        JwtService service = new JwtService(props(900));
        String token = service.issueAccessToken(1L, "user");
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1] + "x." + parts[2];
        ApiException ex =
                assertThrows(ApiException.class, () -> service.parseAccessToken(tampered));
        assertEquals(ApiErrorCode.AUTH_INVALID_TOKEN, ex.code());
    }

    @Test
    void rejectsExpiredToken() {
        JwtService service = new JwtService(props(-60));
        String token = service.issueAccessToken(1L, "user");
        ApiException ex =
                assertThrows(ApiException.class, () -> service.parseAccessToken(token));
        assertEquals(ApiErrorCode.AUTH_TOKEN_EXPIRED, ex.code());
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService issuer = new JwtService(props(900));
        String token = issuer.issueAccessToken(1L, "user");
        AuthProperties otherProps = props(900);
        otherProps.setJwtAccessSecret("another-secret-0123456789-0123456789-abcdef");
        JwtService verifier = new JwtService(otherProps);
        ApiException ex =
                assertThrows(ApiException.class, () -> verifier.parseAccessToken(token));
        assertEquals(ApiErrorCode.AUTH_INVALID_TOKEN, ex.code());
    }

    @Test
    void rejectsTooShortSecret() {
        AuthProperties properties = props(900);
        properties.setJwtAccessSecret("too-short");
        assertThrows(IllegalStateException.class, () -> new JwtService(properties));
    }
}
