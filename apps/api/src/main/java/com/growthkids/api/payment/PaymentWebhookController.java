package com.growthkids.api.payment;

import com.growthkids.api.common.error.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * 支付 webhook（TASK-016/017，§67）：无 JWT，靠 HMAC 签名自校验；
 * mock provider 的回调入口，按 payment_type 分发 membership/donation（真实 provider 各自的回调路由后续接入同一 service）。
 */
@RestController
@Tag(name = "Payments", description = "支付回调")
public class PaymentWebhookController {

    private final PaymentWebhookService paymentWebhookService;

    public PaymentWebhookController(PaymentWebhookService paymentWebhookService) {
        this.paymentWebhookService = paymentWebhookService;
    }

    @PostMapping("/api/v1/payments/webhook/mock")
    @Operation(summary = "mock 支付回调", description = "X-Signature = HMAC-SHA256(payload)，验签失败 400；同交易事件幂等")
    public Map<String, String> mockWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "X-Signature", required = false) String signature) {
        // 失败结果映射为 HTTP 错误；审计日志已在 service 事务内落库（§67）
        PaymentWebhookService.WebhookResult result =
                paymentWebhookService.processWebhook(payload, signature);
        if (!result.isSuccess()) {
            throw new ApiException(result.error());
        }
        return Map.of("received", "true");
    }
}

