package com.growthkids.api.donation.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.common.config.FeaturesProperties;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.donation.dto.DonationCheckoutRequest;
import com.growthkids.api.donation.dto.DonationCheckoutResponse;
import com.growthkids.api.donation.dto.DonationResponse;
import com.growthkids.api.payment.PaymentProvider;
import com.growthkids.api.payment.PaymentWebhookService;
import com.growthkids.api.payment.WebhookEventHandler;
import com.growthkids.api.payment.entity.Payment;
import com.growthkids.api.payment.mapper.PaymentMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 捐赠服务（TASK-017，规范 §65：捐赠独立于会员）：
 * 发起捐赠（PaymentProvider 结算）/ webhook 事件处理（payment→succeeded，无订阅副作用）/
 * mock-complete（与真实 webhook 同一路径）/ 我的捐赠历史。
 */
@Service
public class DonationService implements WebhookEventHandler {

    private static final String EVENT_SUCCEEDED = "payment.succeeded";

    private static final String CURRENCY_USD = "USD";

    private final PaymentMapper paymentMapper;

    private final PaymentProvider paymentProvider;

    private final PaymentWebhookService paymentWebhookService;

    private final FeaturesProperties featuresProperties;

    private final ObjectMapper objectMapper;

    public DonationService(
            PaymentMapper paymentMapper,
            PaymentProvider paymentProvider,
            PaymentWebhookService paymentWebhookService,
            FeaturesProperties featuresProperties,
            ObjectMapper objectMapper) {
        this.paymentMapper = paymentMapper;
        this.paymentProvider = paymentProvider;
        this.paymentWebhookService = paymentWebhookService;
        this.featuresProperties = featuresProperties;
        this.objectMapper = objectMapper;
    }

    /** 发起捐赠：金额自选（范围/小数位由 DTO 校验），创建 pending payment + 结算会话。 */
    @Transactional
    public DonationCheckoutResponse checkout(long userId, DonationCheckoutRequest request) {
        if (!featuresProperties.isEnableDonation()) {
            throw new ApiException(ApiErrorCode.DONATION_DISABLED);
        }
        String currency = normalizeCurrency(request.currency());
        // 与套餐计价对齐，第一版仅支持 USD（§65）
        if (!CURRENCY_USD.equals(currency)) {
            throw new ApiException(ApiErrorCode.DONATION_INVALID_AMOUNT);
        }
        BigDecimal amount = request.amount();
        // DTO 已限 ≤2 位小数，此处兑底防御（scale 为负表示整数尾零，直接放行）
        if (amount.stripTrailingZeros().scale() > 2) {
            throw new ApiException(ApiErrorCode.DONATION_INVALID_AMOUNT);
        }
        PaymentProvider.CheckoutResult checkout =
                paymentProvider.createDonationCheckout(amount, currency, userId);
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setProvider(paymentProvider.name());
        payment.setTransactionId(checkout.transactionId());
        payment.setAmount(request.amount());
        payment.setCurrency(currency);
        payment.setPaymentType(Payment.TYPE_DONATION);
        payment.setStatus(Payment.STATUS_PENDING);
        paymentMapper.insert(payment);
        return new DonationCheckoutResponse(
                paymentProvider.name(), checkout.transactionId(), checkout.checkoutUrl(),
                request.amount(), currency);
    }

    /**
     * mock 完成支付（开发闭环，架构决策 9）：构造 payment.succeeded 事件，
     * 走与真实 webhook 完全相同的验签/处理路径（§67）。已支付则幂等返回当前状态。
     */
    @Transactional
    public DonationResponse mockComplete(long userId, String transactionId) {
        Payment payment = requireOwned(userId, transactionId);
        if (!Payment.STATUS_SUCCEEDED.equals(payment.getStatus())) {
            String payload = buildSucceededEvent(payment);
            PaymentWebhookService.WebhookResult result =
                    paymentWebhookService.processWebhook(payload, paymentProvider.signPayload(payload));
            if (!result.isSuccess()) {
                throw new ApiException(result.error());
            }
            payment = paymentMapper.selectById(payment.getId());
        }
        return DonationResponse.of(payment);
    }

    /** 我的捐赠历史（§65）：全部状态按时间倒序分页，只含本人 donation 单。 */
    public PageResponse<DonationResponse> history(long userId, PageQuery query) {
        Page<Payment> page = new Page<>(query.page(), query.pageSize());
        paymentMapper.selectPage(
                page,
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getUserId, userId)
                        .eq(Payment::getPaymentType, Payment.TYPE_DONATION)
                        .orderByDesc(Payment::getId));
        return PageResponse.of(page).mapRecords(DonationResponse::of);
    }

    @Override
    public String paymentType() {
        return Payment.TYPE_DONATION;
    }

    /** payment.succeeded：payment→succeeded + paidAt（无订阅副作用，幂等）。 */
    @Override
    public void onSucceeded(Payment payment, Map<String, Object> event) {
        if (!Payment.STATUS_SUCCEEDED.equals(payment.getStatus())) {
            payment.setStatus(Payment.STATUS_SUCCEEDED);
            payment.setPaidAt(LocalDateTime.now(ZoneOffset.UTC));
            paymentMapper.updateById(payment);
        }
    }

    private Payment requireOwned(long userId, String transactionId) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getProvider, paymentProvider.name())
                        .eq(Payment::getTransactionId, transactionId));
        if (payment == null || payment.getUserId() == null || payment.getUserId() != userId) {
            throw new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND);
        }
        return payment;
    }

    /** mock 事件构造：金额/货币/交易号取自本单（donation 无套餐上下文）。 */
    private String buildSucceededEvent(Payment payment) {
        Map<String, Object> event = new HashMap<>();
        event.put("provider", paymentProvider.name());
        event.put("eventType", EVENT_SUCCEEDED);
        event.put("transactionId", payment.getTransactionId());
        event.put("amount", payment.getAmount());
        event.put("currency", payment.getCurrency());
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }
    }

    private String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return CURRENCY_USD;
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }
}
