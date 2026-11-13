package com.growthkids.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 刷新 token 请求（规范 §26）。 */
public record RefreshTokenRequest(@NotBlank @Size(max = 512) String refreshToken) {}
