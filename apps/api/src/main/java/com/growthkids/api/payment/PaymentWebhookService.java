package com.growthkids.api.payment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.payment.entity.Payment;
import com.growthkids.api.payment.mapper.PaymentMapper;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 统一 webhook 处理（TASK-017，从 MembershipService 抽出，§67）：
 * 验签 → transactionId → 防重 → 查单 → 按 payment_type 分发（membership/donation）→ 写审计日志。
 * 返回结果而非抛错：失败分支的审计日志必须随事务提交，不因异常回滚丢失。
 * handler 用 ObjectProvider 延迟解析，打破 membership/donation service 的构造循环。
 */
@Service
public class PaymentWebhookService {

    private static final String EVENT_SUCCEEDED = "payment.succeeded";

    private static final String EVENT_FAILED = "payment.failed";

    private final PaymentProvider paymentProvider;

    private final PaymentMapper paymentMapper;

    private final JdbcTemplate jdbcTemplate;

    private final ObjectProvider<WebhookEventHandler> handlers;

    private final ObjectMapper objectMapper;

    public PaymentWebhookService(
            PaymentProvider paymentProvider,
            PaymentMapper paymentMapper,
            JdbcTemplate jdbcTemplate,
            ObjectProvider<WebhookEventHandler> handlers,
            ObjectMapper objectMapper) {
        this.paymentProvider = paymentProvider;
        this.paymentMapper = paymentMapper;
        this.jdbcTemplate = jdbcTemplate;
        this.handlers = handlers;
        this.objectMapper = objectMapper;
    }

    /**
     * webhook 处理（§67）：验证签名 → 验证交易 → 防重复 → 数据库事务 →
     * 更新 payment → 按 payment_type 分发业务更新 → 写 webhook log。
     */
    @Transactional
    public WebhookResult processWebhook(String payload, String signature) {
        boolean signatureValid = paymentProvider.verifyWebhook(payload, signature);
        Map<String, Object> event = parseEvent(payload);
        String transactionId = event == null ? null : str(event, "transactionId");
        String eventType = event == null ? null : str(event, "eventType");
        if (!signatureValid) {
            logWebhook(eventType, transactionId, false, false, payload, "invalid signature");
            return WebhookResult.fail(ApiErrorCode.WEBHOOK_INVALID_SIGNATURE);
        }
        if (transactionId == null || transactionId.isBlank()) {
            logWebhook(eventType, null, true, false, payload, "missing transactionId");
            return WebhookResult.fail(ApiErrorCode.PAYMENT_FAILED);
        }
        // §67 防重复：同交易已成功处理过的事件幂等返回（不重复激活/扣款）
        Integer processed = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_logs"
                        + " WHERE provider = ? AND transaction_id = ? AND processed = 1",
                Integer.class,
                paymentProvider.name(),
                transactionId);
        if (processed != null && processed > 0) {
            return WebhookResult.ok();
        }
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getProvider, paymentProvider.name())
                        .eq(Payment::getTransactionId, transactionId));
        if (payment == null) {
            logWebhook(eventType, transactionId, true, false, payload, "unknown transactionId");
            return WebhookResult.fail(ApiErrorCode.PAYMENT_FAILED);
        }
        switch (eventType == null ? "" : eventType) {
            case EVENT_SUCCEEDED -> {
                WebhookEventHandler handler = resolveHandler(payment);
                if (handler == null) {
                    logWebhook(eventType, transactionId, true, false, payload,
                            "unsupported payment type " + payment.getPaymentType());
                    return WebhookResult.fail(ApiErrorCode.PAYMENT_FAILED);
                }
                handler.onSucceeded(payment, event);
                logWebhook(eventType, transactionId, true, true, payload, null);
            }
            case EVENT_FAILED -> {
                payment.setStatus(Payment.STATUS_FAILED);
                paymentMapper.updateById(payment);
                logWebhook(eventType, transactionId, true, true, payload, null);
            }
            default -> {
                logWebhook(eventType, transactionId, true, false, payload, "unsupported eventType");
                return WebhookResult.fail(ApiErrorCode.PAYMENT_FAILED);
            }
        }
        return WebhookResult.ok();
    }

    private WebhookEventHandler resolveHandler(Payment payment) {
        return handlers.orderedStream()
                .filter(handler -> handler.paymentType().equals(payment.getPaymentType()))
                .findFirst()
                .orElse(null);
    }

    /** webhook 处理结果：error 非 null 表示失败（由调用方映射为 HTTP 错误响应）。 */
    public record WebhookResult(ApiErrorCode error) {

        public static WebhookResult ok() {
            return new WebhookResult(null);
        }

        public static WebhookResult fail(ApiErrorCode error) {
            return new WebhookResult(error);
        }

        public boolean isSuccess() {
            return error == null;
        }
    }

    private void logWebhook(
            String eventType,
            String transactionId,
            boolean signatureValid,
            boolean processed,
            String payload,
            String error) {
        jdbcTemplate.update(
                "INSERT INTO webhook_logs"
                        + " (provider, event_type, transaction_id, signature_valid, processed, payload, error)"
                        + " VALUES (?, ?, ?, ?, ?, ?, ?)",
                paymentProvider.name(),
                eventType,
                transactionId,
                signatureValid,
                processed,
                safePayload(payload),
                error);
    }

    /** payload 列为 JSON 类型：非 JSON 原文包裹为 {"raw": ...}，垃圾回调也能落审计日志而非 500。 */
    private String safePayload(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            objectMapper.readTree(payload);
            return payload;
        } catch (Exception e) {
            try {
                return objectMapper.writeValueAsString(Map.of("raw", payload));
            } catch (Exception ignored) {
                return null;
            }
        }
    }

    private Map<String, Object> parseEvent(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(payload, new TypeReference<>() {});
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Map<String, Object> event, String key) {
        Object value = event.get(key);
        return value != null ? String.valueOf(value) : null;
    }
}
