package com.growthkids.api.membership.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.growthkids.api.membership.MembershipProperties;
import com.growthkids.api.membership.dto.PlanPermissions;
import com.growthkids.api.membership.entity.Membership;
import com.growthkids.api.membership.entity.MembershipPlan;
import com.growthkids.api.membership.mapper.MembershipMapper;
import com.growthkids.api.membership.mapper.MembershipPlanMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 套餐解析器（TASK-016，§64/§70）：按用户当前订阅解析生效套餐与权益。
 * active 判定：status=active 且（expires_at 为空或晚于当前 UTC 时间）；否则回落 Free。
 * 权益数据全部来自 membership_plans.features（配置化 §78），缺失/异常回退 app.membership 默认值。
 */
@Service
public class PlanResolver {

    private final MembershipMapper membershipMapper;

    private final MembershipPlanMapper planMapper;

    private final MembershipProperties properties;

    public PlanResolver(
            MembershipMapper membershipMapper,
            MembershipPlanMapper planMapper,
            MembershipProperties properties) {
        this.membershipMapper = membershipMapper;
        this.planMapper = planMapper;
        this.properties = properties;
    }

    public ResolvedPlan resolve(long userId) {
        Membership membership = latestMembership(userId);
        if (isActive(membership)) {
            MembershipPlan plan = byCode(membership.getPlan());
            return new ResolvedPlan(
                    membership.getPlan(),
                    membership.getStatus(),
                    membership.getExpiresAt(),
                    membership.getProvider(),
                    PlanPermissions.fromFeatures(featuresOf(plan), properties));
        }
        MembershipPlan free = byCode(MembershipPlan.CODE_FREE);
        return new ResolvedPlan(
                MembershipPlan.CODE_FREE,
                null,
                null,
                null,
                PlanPermissions.fromFeatures(featuresOf(free), properties));
    }

    private Membership latestMembership(long userId) {
        List<Membership> rows =
                membershipMapper.selectList(
                        new LambdaQueryWrapper<Membership>()
                                .eq(Membership::getUserId, userId)
                                .orderByDesc(Membership::getId)
                                .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private boolean isActive(Membership membership) {
        return membership != null
                && Membership.STATUS_ACTIVE.equals(membership.getStatus())
                && (membership.getExpiresAt() == null
                        || membership.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)));
    }

    private MembershipPlan byCode(String code) {
        return planMapper.selectOne(
                new LambdaQueryWrapper<MembershipPlan>().eq(MembershipPlan::getCode, code));
    }

    private String featuresOf(MembershipPlan plan) {
        return plan != null ? plan.getFeatures() : null;
    }

    /** 解析结果：free 用户 status/provider/expiresAt 均为 null。 */
    public record ResolvedPlan(
            String plan,
            String status,
            LocalDateTime expiresAt,
            String provider,
            PlanPermissions permissions) {}
}

