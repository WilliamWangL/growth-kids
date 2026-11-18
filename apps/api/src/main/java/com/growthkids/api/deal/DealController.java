package com.growthkids.api.deal;

import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.deal.dto.DealCategoryOption;
import com.growthkids.api.deal.dto.DealResponse;
import com.growthkids.api.deal.service.DealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 优惠商品公开读取（TASK-022）。
 *
 * <p>无需登录（SecurityConfig 白名单），用于 SEO 与转化漏斗；
 * 只暴露上架且在投放窗口内的商品，写操作全部在 AdminDealController。
 */
@RestController
@RequestMapping("/api/v1/deals")
@Tag(name = "Deals", description = "优惠商品目录（公开浏览，购买跳转商家站外）")
public class DealController {

    private final DealService dealService;

    public DealController(DealService dealService) {
        this.dealService = dealService;
    }

    @GetMapping
    @Operation(
            summary = "优惠商品列表",
            description = "分页；可按 category 分类、ageMonths 适用月龄、q 标题/品牌关键词过滤")
    public PageResponse<DealResponse> list(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Integer ageMonths,
            @RequestParam(required = false) String q) {
        return dealService.listPublic(PageQuery.of(page, pageSize), category, ageMonths, q);
    }

    @GetMapping("/categories")
    @Operation(summary = "分类选项", description = "返回全量分类 code/label，前端筛选条直接渲染")
    public List<DealCategoryOption> categories() {
        return dealService.categories();
    }

    @GetMapping("/{dealId}")
    @Operation(summary = "优惠商品详情", description = "下架/过期商品返回 404 DEAL_NOT_FOUND")
    public DealResponse detail(@PathVariable long dealId) {
        return dealService.getPublic(dealId);
    }
}
