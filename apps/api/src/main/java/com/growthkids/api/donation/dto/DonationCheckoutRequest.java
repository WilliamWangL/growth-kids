package com.growthkids.api.donation.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

/**
 * 捐赠请求（TASK-017，规范 §65）：金额用户自选（预设 $3/$5/$10/$20 或自定义），
 * 范围 $0.50–$10,000.00 且最多 2 位小数；currency 缺省 USD。
 */
public record DonationCheckoutRequest(
        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.50", message = "amount must be at least 0.50")
        @DecimalMax(value = "10000.00", message = "amount must be at most 10000.00")
        @Digits(integer = 6, fraction = 2, message = "amount supports at most 2 decimal places")
        BigDecimal amount,
        String currency) {}
