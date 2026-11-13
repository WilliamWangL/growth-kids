package com.growthkids.api.donation;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.donation.dto.DonationCheckoutRequest;
import com.growthkids.api.donation.dto.DonationCheckoutResponse;
import com.growthkids.api.donation.dto.DonationResponse;
import com.growthkids.api.donation.service.DonationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 捐赠 API（TASK-017，规范 §65/§110 /donate 页面后端；独立于会员）。 */
@RestController
@RequestMapping("/api/v1/donations")
@Tag(name = "Donations", description = "捐赠（独立于会员）")
public class DonationController {

    private final DonationService donationService;

    public DonationController(DonationService donationService) {
        this.donationService = donationService;
    }

    @PostMapping("/checkout")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "发起捐赠", description = "金额自选（$0.50–$10,000，≤2 位小数，USD）；创建 pending 支付与结算会话（§66）；mock 环境接 mock-complete 闭环")
    public DonationCheckoutResponse checkout(@Valid @RequestBody DonationCheckoutRequest request) {
        return donationService.checkout(CurrentUser.id(), request);
    }

    @PostMapping("/mock-complete/{transactionId}")
    @Operation(
            summary = "mock 完成支付（开发环境）",
            description = "构造已签名事件走真实 webhook 处理路径（§67）；幂等")
    public DonationResponse mockComplete(@PathVariable String transactionId) {
        return donationService.mockComplete(CurrentUser.id(), transactionId);
    }

    @GetMapping
    @Operation(summary = "我的捐赠历史", description = "全部状态，按时间倒序分页（§65）")
    public PageResponse<DonationResponse> history(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return donationService.history(CurrentUser.id(), PageQuery.of(page, pageSize));
    }
}
