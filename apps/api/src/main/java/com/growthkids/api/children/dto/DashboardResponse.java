package com.growthkids.api.children.dto;

import com.growthkids.api.growth.dto.GrowthRecordResponse;

/**
 * 孩子主页摘要（TASK-011，规范 §54/§112）：孩子信息 + 当前年龄（今天基准）+ 最近一次成长记录。
 *
 * <p>latestRecord 无记录时为 null（JSON 省略）；成长曲线/照片/故事板块由后续任务扩展。
 */
public record DashboardResponse(ChildResponse child, Age age, GrowthRecordResponse latestRecord) {

    /** 当前年龄（基于今天，规范 §37 实际日期口径）。 */
    public record Age(int years, int months, int days, int totalMonths) {}
}
