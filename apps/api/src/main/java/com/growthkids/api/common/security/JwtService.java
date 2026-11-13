package com.growthkids.api.common.security;

import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/** JWT 签发与校验（规范 §71：JWT access token）。 */
@Service
public class JwtService {

    private static final String CLAIM_TYPE = "type";
    private static final String CLAIM_ROLE = "role";
    private static final String TYPE_ACCESS = "access";
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final AuthProperties properties;

    public JwtService(AuthProperties properties) {
        byte[] secret = properties.getJwtAccessSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("app.auth.jwt-access-secret must be at least 32 bytes");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.properties = properties;
    }

    /** 签发 access token（subject = userId）。 */
    public String issueAccessToken(long userId, String role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuer(properties.getIssuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(properties.getAccessTokenTtlSeconds())))
                .claim(CLAIM_TYPE, TYPE_ACCESS)
                .claim(CLAIM_ROLE, role)
                .signWith(key)
                .compact();
    }

    /**
     * 校验并解析 access token。
     *
     * @return 认证主体（userId + role）
     * @throws ApiException AUTH_TOKEN_EXPIRED / AUTH_INVALID_TOKEN
     */
    public AuthPrincipal parseAccessToken(String token) {
        try {
            Claims claims =
                    Jwts.parser()
                            .verifyWith(key)
                            .requireIssuer(properties.getIssuer())
                            .build()
                            .parseSignedClaims(token)
                            .getPayload();
            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TYPE, String.class))) {
                throw new ApiException(ApiErrorCode.AUTH_INVALID_TOKEN);
            }
            long userId = Long.parseLong(claims.getSubject());
            String role = claims.get(CLAIM_ROLE, String.class);
            return new AuthPrincipal(userId, role == null ? "user" : role);
        } catch (ExpiredJwtException ex) {
            throw new ApiException(ApiErrorCode.AUTH_TOKEN_EXPIRED);
        } catch (JwtException | IllegalArgumentException ex) {
            throw new ApiException(ApiErrorCode.AUTH_INVALID_TOKEN);
        }
    }

    public long getAccessTokenTtlSeconds() {
        return properties.getAccessTokenTtlSeconds();
    }
}
