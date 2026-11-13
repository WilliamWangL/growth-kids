package com.growthkids.api.membership;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.membership.dto.MembershipStatusResponse;
import com.growthkids.api.membership.dto.PlanResponse;
import com.growthkids.api.membership.dto.SubscribeRequest;
import com.growthkids.api.membership.dto.SubscribeResponse;
import com.growthkids.api.membership.service.MembershipService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 会员 API（TASK-016，规范 §64/§110 /membership 页面后端）。 */
@RestController
@Tag(name = "Membership", description = "会员套餐与订阅")
public class MembershipController {

    private final MembershipService membershipService;

    public MembershipController(MembershipService membershipService) {
        this.membershipService = membershipService;
    }

    @GetMapping("/api/v1/membership/plans")
    @Operation(summary = "套餐列表", description = "公开；价格与权益来自 membership_plans 配置化数据（§64）")
    public List<PlanResponse> plans() {
        return membershipService.listPlans();
    }

    @GetMapping("/api/v1/membership")
    @Operation(summary = "当前订阅", description = "生效套餐 + 权益；free 用户 status 为空")
    public MembershipStatusResponse me() {
        return membershipService.getMyMembership(CurrentUser.id());
    }

    @PostMapping("/api/v1/membership/subscribe")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "发起订阅", description = "创建 pending 支付与结算会话（§66）；mock 环境接 mock-complete 闭环")
    public SubscribeResponse subscribe(@Valid @RequestBody SubscribeRequest request) {
        return membershipService.subscribe(CurrentUser.id(), request);
    }

    @PostMapping("/api/v1/membership/mock-complete/{transactionId}")
    @Operation(
            summary = "mock 完成支付（开发环境）",
            description = "构造已签名事件走真实 webhook 处理路径（§67）；幂等")
    public MembershipStatusResponse mockComplete(@PathVariable String transactionId) {
        return membershipService.mockComplete(CurrentUser.id(), transactionId);
    }

    @PostMapping("/api/v1/membership/cancel")
    @Operation(summary = "取消订阅", description = "置 cancelled，权益立即回落 Free（§64）")
    public MembershipStatusResponse cancel() {
        return membershipService.cancel(CurrentUser.id());
    }
}

