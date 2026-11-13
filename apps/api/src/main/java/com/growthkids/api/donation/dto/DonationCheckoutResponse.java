package com.growthkids.api.donation.dto;

import java.math.BigDecimal;

/**
 * 捐赠结算响应（TASK-017）：checkoutUrl 供前端跳转支付网关；
 * mock 环境下前端直接调 mock-complete 完成本地闭环（架构决策 9）。
 */
public record DonationCheckoutResponse(
        String provider, String transactionId, String checkoutUrl, BigDecimal amount, String currency) {}
