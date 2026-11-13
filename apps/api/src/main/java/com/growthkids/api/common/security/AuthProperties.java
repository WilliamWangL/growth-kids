package com.growthkids.api.common.security;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Auth 相关配置（app.auth.*，规范 §78 配置化原则）。 */
@Validated
@ConfigurationProperties(prefix = "app.auth")
public class AuthProperties {

    /** JWT access token 签名密钥（HS256，至少 32 字节）。 */
    @NotBlank
    private String jwtAccessSecret;

    /** JWT refresh 相关密钥（保留配置；v1 refresh token 为随机不透明串，仅存摘要）。 */
    private String jwtRefreshSecret;

    /** access token 有效期（秒），默认 15 分钟。 */
    private long accessTokenTtlSeconds = 900;

    /** refresh token 有效期（秒），默认 30 天。 */
    private long refreshTokenTtlSeconds = 2_592_000;

    /** JWT issuer。 */
    private String issuer = "growthkids";

    /** 同一邮箱连续登录失败上限（超出后 429）。 */
    private int loginMaxAttemptsPerEmail = 10;

    /** 同一 IP 连续登录失败上限（超出后 429）。 */
    private int loginMaxAttemptsPerIp = 30;

    /** 登录失败计数窗口（秒），默认 15 分钟。 */
    private long loginAttemptWindowSeconds = 900;

    public String getJwtAccessSecret() {
        return jwtAccessSecret;
    }

    public void setJwtAccessSecret(String jwtAccessSecret) {
        this.jwtAccessSecret = jwtAccessSecret;
    }

    public String getJwtRefreshSecret() {
        return jwtRefreshSecret;
    }

    public void setJwtRefreshSecret(String jwtRefreshSecret) {
        this.jwtRefreshSecret = jwtRefreshSecret;
    }

    public long getAccessTokenTtlSeconds() {
        return accessTokenTtlSeconds;
    }

    public void setAccessTokenTtlSeconds(long accessTokenTtlSeconds) {
        this.accessTokenTtlSeconds = accessTokenTtlSeconds;
    }

    public long getRefreshTokenTtlSeconds() {
        return refreshTokenTtlSeconds;
    }

    public void setRefreshTokenTtlSeconds(long refreshTokenTtlSeconds) {
        this.refreshTokenTtlSeconds = refreshTokenTtlSeconds;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public int getLoginMaxAttemptsPerEmail() {
        return loginMaxAttemptsPerEmail;
    }

    public void setLoginMaxAttemptsPerEmail(int loginMaxAttemptsPerEmail) {
        this.loginMaxAttemptsPerEmail = loginMaxAttemptsPerEmail;
    }

    public int getLoginMaxAttemptsPerIp() {
        return loginMaxAttemptsPerIp;
    }

    public void setLoginMaxAttemptsPerIp(int loginMaxAttemptsPerIp) {
        this.loginMaxAttemptsPerIp = loginMaxAttemptsPerIp;
    }

    public long getLoginAttemptWindowSeconds() {
        return loginAttemptWindowSeconds;
    }

    public void setLoginAttemptWindowSeconds(long loginAttemptWindowSeconds) {
        this.loginAttemptWindowSeconds = loginAttemptWindowSeconds;
    }
}
