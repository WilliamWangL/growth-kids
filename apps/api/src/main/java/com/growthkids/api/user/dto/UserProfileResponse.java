package com.growthkids.api.user.dto;

import com.growthkids.api.user.entity.User;
import java.time.Instant;
import java.time.ZoneOffset;

/** 当前用户资料（id 为字符串，时间统一 UTC，规范 §33/§128；role TASK-019）。 */
public record UserProfileResponse(
        String id,
        String email,
        String displayName,
        String avatarUrl,
        String timezone,
        String locale,
        String role,
        Instant createdAt) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getDisplayName(),
                user.getAvatarUrl(),
                user.getTimezone(),
                user.getLocale(),
                user.getRole(),
                user.getCreatedAt() == null ? null : user.getCreatedAt().toInstant(ZoneOffset.UTC));
    }
}
