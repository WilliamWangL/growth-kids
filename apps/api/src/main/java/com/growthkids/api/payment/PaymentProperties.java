package com.growthkids.api.payment;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 支付配置（app.payment.*，架构决策 9）：provider 选择实现（mock/paypal/stripe），
 * mock-signing-secret 用于 MockPaymentProvider 的 HMAC webhook 签名（生产必须替换）。
 */
@ConfigurationProperties(prefix = "app.payment")
public class PaymentProperties {

    private String provider = "mock";

    private String mockSigningSecret = "dev-payment-secret-please-change-me";

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getMockSigningSecret() {
        return mockSigningSecret;
    }

    public void setMockSigningSecret(String mockSigningSecret) {
        this.mockSigningSecret = mockSigningSecret;
    }
}

