package com.growthkids.api.payment;

import com.growthkids.api.payment.entity.Payment;
import java.util.Map;

/**
 * webhook 事件处理器（TASK-017，§66/§67）：按 payment_type 分发 payment.succeeded，
 * membership/donation 各自实现（失败事件统一由 PaymentWebhookService 置 payment=failed）。
 */
public interface WebhookEventHandler {

    /** 处理器对应的 payments.payment_type（见 Payment.TYPE_*）。 */
    String paymentType();

    /** payment.succeeded：更新业务状态（幂等，重复事件已被防重层拦截）。 */
    void onSucceeded(Payment payment, Map<String, Object> event);
}
