package com.growthkids.api.admin.dto;

import java.time.LocalDateTime;

/**
 * 日志列表行（§77 错误日志）：webhook（支付回调，含验签失败/处理错误）与
 * audit（管理操作审计）两类统一为行结构，按 kind 区分字段含义。
 */
public record AdminLogResponse(
        long id,
        String kind,
        LocalDateTime createdAt,
        /** webhook: provider/eventType；audit: action */
        String summary,
        /** webhook: transaction_id；audit: 请求 ID */
        String reference,
        /** audit: 操作者用户 ID */
        Long actorId,
        /** webhook: 已成功处理；audit: 无（null） */
        Boolean processed,
        String error,
        /** audit: details JSON 原文；webhook: null */
        String details) {}
