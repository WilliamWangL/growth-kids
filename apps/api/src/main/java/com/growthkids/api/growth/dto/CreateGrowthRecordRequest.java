package com.growthkids.api.growth.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 创建成长记录（规范 §29）。
 *
 * <p>recordDate 必填；heightCm / weightKg / headCircumferenceCm 至少提供一个。
 * 业务区间校验在 Service 层（以返回规范 §69 对应的领域错误码）。
 */
public record CreateGrowthRecordRequest(
        @NotNull LocalDate recordDate,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal headCircumferenceCm,
        @Size(max = 2000) String note) {}
