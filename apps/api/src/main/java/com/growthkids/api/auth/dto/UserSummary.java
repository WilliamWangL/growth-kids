package com.growthkids.api.auth.dto;

import com.growthkids.api.user.entity.User;

/** 用户摘要（规范 §24 响应示例：id 为字符串；role 供前端区分 admin 入口，TASK-019）。 */
public record UserSummary(String id, String email, String displayName, String role) {

    public static UserSummary from(User user) {
        return new UserSummary(
                String.valueOf(user.getId()),
                user.getEmail(),
                user.getDisplayName(),
                user.getRole());
    }
}
