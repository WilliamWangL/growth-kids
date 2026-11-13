package com.growthkids.api.admin.dto;

import java.math.BigDecimal;

/**
 * 后台统计（TASK-019，规范 §77）：用户/孩子/记录/照片/会员/支付/捐赠/报告/WHO 版本。
 */
public record AdminDashboardResponse(
        long totalUsers,
        long activeUsers,
        ChildStats children,
        long growthRecords,
        long photos,
        long memories,
        long activeMemberships,
        PaymentStats payments,
        ReportStats reports,
        WhoVersionInfo who) {

    public record ChildStats(long total, long active, long deleted) {}

    /**
     * 支付统计：membership/donation 各自成功笔数与金额，另有跨类型的 pending/failed 计数。
     */
    public record PaymentStats(
            long membershipSucceeded,
            BigDecimal membershipRevenue,
            long donationSucceeded,
            BigDecimal donationRevenue,
            long pending,
            long failed) {}

    public record ReportStats(long total, long ready) {}

    /** WHO 数据集版本（§124）：source 为 growthkids-example-synthetic 时为合成示例数据。 */
    public record WhoVersionInfo(String source, String version, long rows, String importedAt) {}
}
