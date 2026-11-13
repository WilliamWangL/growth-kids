package com.growthkids.api.report.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** 创建年度报告请求（TASK-018，§98）：year 为报告年份。 */
public record CreateReportRequest(
        @NotNull(message = "year is required")
        @Min(value = 1900, message = "year must be after 1900")
        @Max(value = 2100, message = "year must be before 2100")
        Integer year) {}
