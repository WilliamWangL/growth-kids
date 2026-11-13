package com.growthkids.api.membership.dto;

import java.time.LocalDateTime;

/**
 * 当前订阅状态（TASK-016）：plan 为当前生效套餐（free/pro）；
 * free 用户无订阅记录，status/provider/expiresAt 为 null（Jackson non_null 省略）。
 */
public record MembershipStatusResponse(
        String plan,
        String status,
        LocalDateTime expiresAt,
        String provider,
        PlanPermissions permissions) {
}

