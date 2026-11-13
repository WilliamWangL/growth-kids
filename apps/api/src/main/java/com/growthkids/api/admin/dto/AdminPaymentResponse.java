package com.growthkids.api.admin.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 支付列表行（§77 payments）：membership/donation 共用一张 payments 表。 */
public record AdminPaymentResponse(
        long id,
        long userId,
        String userEmail,
        String provider,
        String transactionId,
        BigDecimal amount,
        String currency,
        String paymentType,
        String status,
        LocalDateTime paidAt,
        LocalDateTime createdAt) {}
