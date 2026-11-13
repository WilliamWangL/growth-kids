package com.growthkids.api.report.dto;

import com.growthkids.api.report.entity.GrowthReport;
import java.time.LocalDateTime;

/** 年度报告响应（TASK-018）：status pending/generating/ready/failed，ready 后可下载。 */
public record ReportResponse(
        long id,
        long childId,
        String reportType,
        int year,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static ReportResponse of(GrowthReport report) {
        return new ReportResponse(
                report.getId(),
                report.getChildId(),
                report.getReportType(),
                report.getYear(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt());
    }
}
