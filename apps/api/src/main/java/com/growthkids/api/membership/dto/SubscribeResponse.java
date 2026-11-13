package com.growthkids.api.membership.dto;

/**
 * 订阅结算响应（TASK-016）：checkoutUrl 供前端跳转支付网关；
 * mock 环境下前端直接调 mock-complete 完成本地闭环（架构决策 9）。
 */
public record SubscribeResponse(String provider, String transactionId, String checkoutUrl) {}

