package com.growthkids.api.admin.dto;

import java.time.LocalDateTime;

/** 用户列表行（§77 users）：含活跃孩子数与当前订阅套餐。 */
public record AdminUserResponse(
        long id,
        String email,
        String displayName,
        String role,
        String status,
        String plan,
        long childCount,
        LocalDateTime createdAt) {}
