package com.growthkids.api.common.security;

/** 认证后的当前用户主体（来自 JWT access token，规范 §70 CurrentUser）。 */
public record AuthPrincipal(long userId, String role) {

    public boolean isAdmin() {
        return "admin".equals(role);
    }
}
