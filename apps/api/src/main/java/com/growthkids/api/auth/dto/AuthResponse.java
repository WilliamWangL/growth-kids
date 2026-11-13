package com.growthkids.api.auth.dto;

/** 认证响应（规范 §24）。 */
public record AuthResponse(UserSummary user, String accessToken, String refreshToken) {}
