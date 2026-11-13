package com.growthkids.api.children.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 更新孩子（PATCH 语义）：
 * 字段缺省（null）= 保持不变；空字符串 = 清空（avatarUrl / notes）。
 * name 为空字符串时返回 CHILD_INVALID_DATA；数值字段暂不支持显式清空。
 */
public record UpdateChildRequest(
        @Size(max = 100) String name,
        @Pattern(regexp = "male|female|unknown", message = "must be one of: male, female, unknown")
                String gender,
        LocalDate birthDate,
        @DecimalMin("20") @DecimalMax("70") BigDecimal birthHeightCm,
        @DecimalMin("0.3") @DecimalMax("12") BigDecimal birthWeightKg,
        @DecimalMin("100") @DecimalMax("250") BigDecimal fatherHeightCm,
        @DecimalMin("100") @DecimalMax("250") BigDecimal motherHeightCm,
        @Size(max = 500) String avatarUrl,
        @Size(max = 2000) String notes) {}
