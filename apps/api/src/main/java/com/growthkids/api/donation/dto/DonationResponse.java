package com.growthkids.api.donation.dto;

import com.growthkids.api.payment.entity.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 捐赠记录响应（TASK-017，§65：user_id/amount/currency/provider/transaction_id/status）。 */
public record DonationResponse(
        long id,
        String transactionId,
        BigDecimal amount,
        String currency,
        String provider,
        String status,
        LocalDateTime paidAt,
        LocalDateTime createdAt) {

    public static DonationResponse of(Payment payment) {
        return new DonationResponse(
                payment.getId(),
                payment.getTransactionId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getProvider(),
                payment.getStatus(),
                payment.getPaidAt(),
                payment.getCreatedAt());
    }
}
