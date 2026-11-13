package com.growthkids.api.membership.dto;

import com.growthkids.api.membership.entity.MembershipPlan;
import java.math.BigDecimal;

/** 套餐响应（TASK-016，§64）：价格与权益均来自 membership_plans 配置化数据。 */
public record PlanResponse(
        String code,
        String name,
        BigDecimal priceAmount,
        String currency,
        String billingPeriod,
        PlanPermissions permissions) {

    public static PlanResponse of(MembershipPlan plan, PlanPermissions permissions) {
        return new PlanResponse(
                plan.getCode(),
                plan.getName(),
                plan.getPriceAmount(),
                plan.getCurrency(),
                plan.getBillingPeriod(),
                permissions);
    }
}

