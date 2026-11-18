package com.growthkids.api.deal;

import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.deal.dto.DealPatchRequest;
import com.growthkids.api.deal.dto.DealRequest;
import com.growthkids.api.deal.dto.DealResponse;
import com.growthkids.api.deal.service.DealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台优惠商品管理（TASK-022，规范 §77 admin 能力）。
 *
 * <p>路由级权限由 SecurityConfig（/api/v1/admin/** hasRole ADMIN）保证，
 * 方法级 @PreAuthorize 双保险；与 AdminController 保持同一约定。
 */
@RestController
@RequestMapping("/api/v1/admin/deals")
@Tag(name = "Admin Deals", description = "优惠商品管理（仅 admin）")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDealController {

    private final DealService dealService;

    public AdminDealController(DealService dealService) {
        this.dealService = dealService;
    }

    @GetMapping
    @Operation(summary = "商品列表（后台）", description = "含下架商品，可按 status=active|inactive 过滤")
    public PageResponse<DealResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String status) {
        return dealService.listAdmin(PageQuery.of(page, pageSize), status);
    }

    @PostMapping
    @Operation(summary = "新建商品", description = "currency 缺省 USD、status 缺省 active；原价不得低于优惠价")
    public DealResponse create(@Valid @RequestBody DealRequest request) {
        return dealService.create(request);
    }

    @PatchMapping("/{dealId}")
    @Operation(summary = "更新商品", description = "PATCH 语义：字段缺省不变，文本空串清空")
    public DealResponse update(
            @PathVariable long dealId, @Valid @RequestBody DealPatchRequest request) {
        return dealService.update(dealId, request);
    }

    @DeleteMapping("/{dealId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除商品", description = "物理删除；只想隐藏请 PATCH status=inactive")
    public void delete(@PathVariable long dealId) {
        dealService.delete(dealId);
    }
}
