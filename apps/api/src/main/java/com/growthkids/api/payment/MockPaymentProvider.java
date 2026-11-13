package com.growthkids.api.payment;

import com.growthkids.api.membership.entity.MembershipPlan;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Mock 支付提供商（架构决策 9）：开发可用，HMAC-SHA256 签名验证 webhook（§67），
 * 交易号本地生成；PayPal/Stripe 适配器待真实凭据后按同一接口接入。
 */
@Component
@ConditionalOnProperty(prefix = "app.payment", name = "provider", havingValue = "mock", matchIfMissing = true)
public class MockPaymentProvider implements PaymentProvider {

    private static final String HMAC_SHA256 = "HmacSHA256";

    private final PaymentProperties properties;

    public MockPaymentProvider(PaymentProperties properties) {
        this.properties = properties;
    }

    @Override
    public String name() {
        return "mock";
    }

    @Override
    public CheckoutResult createCheckout(MembershipPlan plan, long userId) {
        String transactionId = "mock-" + UUID.randomUUID();
        return new CheckoutResult(transactionId, "/membership/checkout/mock/" + transactionId);
    }

    @Override
    public CheckoutResult createDonationCheckout(BigDecimal amount, String currency, long userId) {
        String transactionId = "mock-" + UUID.randomUUID();
        return new CheckoutResult(transactionId, "/donate/checkout/mock/" + transactionId);
    }

    @Override
    public boolean verifyWebhook(String rawPayload, String signature) {
        if (rawPayload == null || signature == null) {
            return false;
        }
        String expected = signPayload(rawPayload);
        // 时序安全比较（与 StorageSigner 同策略，§71）
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8), signature.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public PaymentResult getPayment(String transactionId) {
        return new PaymentResult(transactionId, "UNKNOWN");
    }

    @Override
    public RefundResult refund(String transactionId, BigDecimal amount) {
        return new RefundResult(false, "mock provider does not support refunds");
    }

    @Override
    public String signPayload(String rawPayload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(
                    properties.getMockSigningSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return HexFormat.of().formatHex(
                    mac.doFinal(rawPayload.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to sign mock payload", e);
        }
    }
}

