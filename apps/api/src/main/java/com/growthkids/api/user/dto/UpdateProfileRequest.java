package com.growthkids.api.user.dto;

import jakarta.validation.constraints.Size;

/**
 * 更新用户资料（PATCH 语义）：
 * 字段缺省（null）= 保持不变；空字符串 = 清空该字段（仅 displayName / avatarUrl）。
 */
public record UpdateProfileRequest(
        @Size(max = 100) String displayName,
        @Size(max = 500) String avatarUrl,
        @Size(max = 64) String timezone,
        @Size(max = 20) String locale) {}
