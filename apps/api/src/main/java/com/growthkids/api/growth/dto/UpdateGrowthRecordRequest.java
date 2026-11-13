package com.growthkids.api.growth.dto;

import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 修改成长记录（规范 §31）：修改后必须重新计算 age / BMI / Z-score / percentile。
 *
 * <p>PATCH 语义：字段缺省（null）= 保持不变；note 传空字符串 = 清空。
 * 业务区间校验在 Service 层（以返回规范 §69 对应的领域错误码）。
 */
public record UpdateGrowthRecordRequest(
        LocalDate recordDate,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal headCircumferenceCm,
        @Size(max = 2000) String note) {}
