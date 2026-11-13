package com.growthkids.api.payment;

import com.growthkids.api.membership.entity.MembershipPlan;
import java.math.BigDecimal;

/**
 * 支付提供商抽象（规范 §66）：createCheckout/verifyWebhook/getPayment/refund 四能力，
 * 未来可替换 PayPal/Stripe 等真实实现；provider 名与 payments.provider 对应。
 */
public interface PaymentProvider {

    /** provider 标识（写入 payments.provider / memberships.provider）。 */
    String name();

    /** 发起订阅结算：返回交易号与跳转 URL（mock 为本地模拟地址）。 */
    CheckoutResult createCheckout(MembershipPlan plan, long userId);

    /** 发起捐赠结算（§65：金额用户自选，独立于会员套餐）。 */
    CheckoutResult createDonationCheckout(BigDecimal amount, String currency, long userId);

    /** §67：校验 webhook 原始 payload 的签名（时序安全比较）。 */
    boolean verifyWebhook(String rawPayload, String signature);

    /** §66：查询远程支付状态（mock 无远程状态，返回 UNKNOWN，以本地 DB 为准）。 */
    PaymentResult getPayment(String transactionId);

    /** §66：退款（mock 不支持）。 */
    RefundResult refund(String transactionId, BigDecimal amount);

    /**
     * 构造已签名事件（仅 mock 开发闭环使用）：mock-complete 端点模拟网关回调时，
     * 用与真实 webhook 完全相同的签名/处理路径。真实 provider 可返回空表示不支持。
     */
    String signPayload(String rawPayload);

    record CheckoutResult(String transactionId, String checkoutUrl) {}

    record PaymentResult(String transactionId, String status) {}

    record RefundResult(boolean succeeded, String message) {}
}

