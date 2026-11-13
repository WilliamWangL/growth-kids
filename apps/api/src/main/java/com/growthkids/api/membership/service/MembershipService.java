package com.growthkids.api.membership.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.common.config.FeaturesProperties;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.membership.MembershipProperties;
import com.growthkids.api.membership.dto.MembershipStatusResponse;
import com.growthkids.api.membership.dto.PlanPermissions;
import com.growthkids.api.membership.dto.PlanResponse;
import com.growthkids.api.membership.dto.SubscribeRequest;
import com.growthkids.api.membership.dto.SubscribeResponse;
import com.growthkids.api.membership.entity.Membership;
import com.growthkids.api.membership.entity.MembershipPlan;
import com.growthkids.api.membership.mapper.MembershipMapper;
import com.growthkids.api.membership.mapper.MembershipPlanMapper;
import com.growthkids.api.payment.PaymentProvider;
import com.growthkids.api.payment.PaymentWebhookService;
import com.growthkids.api.payment.WebhookEventHandler;
import com.growthkids.api.payment.entity.Payment;
import com.growthkids.api.payment.mapper.PaymentMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会员服务（TASK-016，规范 §64/§66/§67）：
 * 套餐列表（配置化）/ 当前订阅 / 发起订阅（PaymentProvider 结算）/
 * webhook 事件处理（WebhookEventHandler，统一入口见 PaymentWebhookService）/
 * mock-complete（开发闭环，与真实 webhook 同一路径）/ 取消。
 */
@Service
public class MembershipService implements WebhookEventHandler {

    private static final String EVENT_SUCCEEDED = "payment.succeeded";

    private final MembershipPlanMapper planMapper;

    private final MembershipMapper membershipMapper;

    private final PaymentMapper paymentMapper;

    private final PlanResolver planResolver;

    private final PaymentProvider paymentProvider;

    private final PaymentWebhookService paymentWebhookService;

    private final MembershipProperties membershipProperties;

    private final FeaturesProperties featuresProperties;

    private final ObjectMapper objectMapper;

    public MembershipService(
            MembershipPlanMapper planMapper,
            MembershipMapper membershipMapper,
            PaymentMapper paymentMapper,
            PlanResolver planResolver,
            PaymentProvider paymentProvider,
            PaymentWebhookService paymentWebhookService,
            MembershipProperties membershipProperties,
            FeaturesProperties featuresProperties,
            ObjectMapper objectMapper) {
        this.planMapper = planMapper;
        this.membershipMapper = membershipMapper;
        this.paymentMapper = paymentMapper;
        this.planResolver = planResolver;
        this.paymentProvider = paymentProvider;
        this.paymentWebhookService = paymentWebhookService;
        this.membershipProperties = membershipProperties;
        this.featuresProperties = featuresProperties;
        this.objectMapper = objectMapper;
    }

    /** 套餐列表（公开，§64 价格配置化；§121 建议缓存，第一版直查）。 */
    public List<PlanResponse> listPlans() {
        return planMapper
                .selectList(
                        new LambdaQueryWrapper<MembershipPlan>()
                                .eq(MembershipPlan::getIsActive, true)
                                .orderByAsc(MembershipPlan::getSortOrder))
                .stream()
                .map(plan -> PlanResponse.of(
                        plan, PlanPermissions.fromFeatures(plan.getFeatures(), membershipProperties)))
                .toList();
    }

    public MembershipStatusResponse getMyMembership(long userId) {
        PlanResolver.ResolvedPlan resolved = planResolver.resolve(userId);
        return new MembershipStatusResponse(
                resolved.plan(),
                resolved.status(),
                resolved.expiresAt(),
                resolved.provider(),
                resolved.permissions());
    }

    /** 发起订阅：创建 pending payment + 结算会话（真实网关跳 checkoutUrl）。 */
    @Transactional
    public SubscribeResponse subscribe(long userId, SubscribeRequest request) {
        if (!featuresProperties.isEnableMembership()) {
            throw new ApiException(ApiErrorCode.MEMBERSHIP_REQUIRED);
        }
        MembershipPlan plan = planMapper.selectOne(
                new LambdaQueryWrapper<MembershipPlan>()
                        .eq(MembershipPlan::getCode, request.planCode())
                        .eq(MembershipPlan::getIsActive, true));
        // free 无需订阅；不存在/未启用套餐一律 404（不泄露细节）
        if (plan == null || MembershipPlan.CODE_FREE.equals(plan.getCode())) {
            throw new ApiException(ApiErrorCode.MEMBERSHIP_PLAN_NOT_FOUND);
        }
        String currentPlan = planResolver.resolve(userId).plan();
        if (!MembershipPlan.CODE_FREE.equals(currentPlan)) {
            throw new ApiException(ApiErrorCode.MEMBERSHIP_ALREADY_ACTIVE);
        }
        PaymentProvider.CheckoutResult checkout = paymentProvider.createCheckout(plan, userId);
        Payment payment = new Payment();
        payment.setUserId(userId);
        payment.setProvider(paymentProvider.name());
        payment.setTransactionId(checkout.transactionId());
        payment.setAmount(plan.getPriceAmount());
        payment.setCurrency(plan.getCurrency());
        payment.setPaymentType(Payment.TYPE_MEMBERSHIP);
        payment.setStatus(Payment.STATUS_PENDING);
        // checkout 上下文（planCode）随单保存：webhook 激活时从此恢复套餐，不信任回调字段
        payment.setRawPayload(json(Map.of("planCode", plan.getCode())));
        paymentMapper.insert(payment);
        return new SubscribeResponse(
                paymentProvider.name(), checkout.transactionId(), checkout.checkoutUrl());
    }

    /**
     * mock 完成支付（开发闭环，架构决策 9）：构造 payment.succeeded 事件，
     * 走与真实 webhook 完全相同的验签/处理路径（§67）。已支付则幂等返回当前状态。
     */
    @Transactional
    public MembershipStatusResponse mockComplete(long userId, String transactionId) {
        Payment payment = paymentMapper.selectOne(
                new LambdaQueryWrapper<Payment>()
                        .eq(Payment::getProvider, paymentProvider.name())
                        .eq(Payment::getTransactionId, transactionId));
        if (payment == null || payment.getUserId() == null || payment.getUserId() != userId) {
            throw new ApiException(ApiErrorCode.PAYMENT_NOT_FOUND);
        }
        if (!Payment.STATUS_SUCCEEDED.equals(payment.getStatus())) {
            String payload = buildSucceededEvent(payment);
            PaymentWebhookService.WebhookResult result =
                    paymentWebhookService.processWebhook(payload, paymentProvider.signPayload(payload));
            if (!result.isSuccess()) {
                throw new ApiException(result.error());
            }
        }
        return getMyMembership(userId);
    }

    @Override
    public String paymentType() {
        return Payment.TYPE_MEMBERSHIP;
    }

    /** payment.succeeded：激活订阅（payment→succeeded + membership upsert）。 */
    @Override
    public void onSucceeded(Payment payment, Map<String, Object> event) {
        activateMembership(payment, event);
    }

    /** 取消订阅（§64）：置 cancelled，权益立即回落 Free（到期时间保留供审计）。 */
    @Transactional
    public MembershipStatusResponse cancel(long userId) {
        Membership membership = latestMembership(userId);
        if (membership == null || !Membership.STATUS_ACTIVE.equals(membership.getStatus())) {
            throw new ApiException(ApiErrorCode.MEMBERSHIP_NOT_FOUND);
        }
        membership.setStatus(Membership.STATUS_CANCELLED);
        membershipMapper.updateById(membership);
        return getMyMembership(userId);
    }

    /** 激活订阅：payment → succeeded；membership 按 billing_period 计算到期时间并 upsert。 */
    private void activateMembership(Payment payment, Map<String, Object> event) {
        String planCode = str(event, "planCode");
        MembershipPlan plan = planCode == null ? null : planMapper.selectOne(
                new LambdaQueryWrapper<MembershipPlan>().eq(MembershipPlan::getCode, planCode));
        if (plan == null) {
            // checkout 时 planCode 随单保存（见 subscribe），缺失/未知视为不可信事件
            throw new ApiException(ApiErrorCode.PAYMENT_FAILED);
        }
        if (!Payment.STATUS_SUCCEEDED.equals(payment.getStatus())) {
            payment.setStatus(Payment.STATUS_SUCCEEDED);
            payment.setPaidAt(LocalDateTime.now(ZoneOffset.UTC));
            paymentMapper.updateById(payment);
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        Membership membership = latestMembership(payment.getUserId());
        if (membership == null) {
            membership = new Membership();
            membership.setUserId(payment.getUserId());
        }
        membership.setPlan(plan.getCode());
        membership.setStatus(Membership.STATUS_ACTIVE);
        membership.setProvider(payment.getProvider());
        membership.setProviderSubscriptionId(str(event, "providerSubscriptionId"));
        membership.setStartedAt(now);
        membership.setExpiresAt(expiresAt(plan.getBillingPeriod(), now));
        if (membership.getId() == null) {
            membershipMapper.insert(membership);
        } else {
            membershipMapper.updateById(membership);
        }
    }

    private LocalDateTime expiresAt(String billingPeriod, LocalDateTime now) {
        return switch (billingPeriod == null ? "none" : billingPeriod) {
            case "monthly" -> now.plusMonths(1);
            case "yearly" -> now.plusYears(1);
            default -> null;
        };
    }

    /** mock 事件构造：金额/货币/交易号取自本单，planCode 恢复自 checkout 上下文。 */
    private String buildSucceededEvent(Payment payment) {
        String planCode = MembershipPlan.CODE_FREE;
        if (payment.getRawPayload() != null) {
            Map<String, Object> raw = parseEvent(payment.getRawPayload());
            if (raw != null && raw.get("planCode") != null) {
                planCode = String.valueOf(raw.get("planCode"));
            }
        }
        Map<String, Object> event = new HashMap<>();
        event.put("provider", paymentProvider.name());
        event.put("eventType", EVENT_SUCCEEDED);
        event.put("transactionId", payment.getTransactionId());
        event.put("amount", payment.getAmount());
        event.put("currency", payment.getCurrency());
        event.put("planCode", planCode);
        event.put("providerSubscriptionId", "mock-sub-" + UUID.randomUUID());
        return json(event);
    }

    private Membership latestMembership(long userId) {
        List<Membership> rows = membershipMapper.selectList(
                new LambdaQueryWrapper<Membership>()
                        .eq(Membership::getUserId, userId)
                        .orderByDesc(Membership::getId)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.getFirst();
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

    private String json(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize webhook payload", e);
        }
    }

    private static String str(Map<String, Object> event, String key) {
        Object value = event.get(key);
        return value != null ? String.valueOf(value) : null;
    }
}

