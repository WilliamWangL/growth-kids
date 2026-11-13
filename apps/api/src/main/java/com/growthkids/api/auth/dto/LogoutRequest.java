package com.growthkids.api.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 登出请求（规范 §27）：提交 refresh token 以撤销。 */
public record LogoutRequest(@NotBlank @Size(max = 512) String refreshToken) {}
