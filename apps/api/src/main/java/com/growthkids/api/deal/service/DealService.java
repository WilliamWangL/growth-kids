package com.growthkids.api.deal.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.deal.DealCategory;
import com.growthkids.api.deal.dto.DealCategoryOption;
import com.growthkids.api.deal.dto.DealPatchRequest;
import com.growthkids.api.deal.dto.DealRequest;
import com.growthkids.api.deal.dto.DealResponse;
import com.growthkids.api.deal.entity.Deal;
import com.growthkids.api.deal.mapper.DealMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 优惠商品服务（TASK-022）。
 *
 * <p>公开侧只返回「已上架 + 在投放窗口内」的商品；不可见与不存在统一抛
 * DEAL_NOT_FOUND（§70 隐藏存在性）。后台侧返回全量并可按状态过滤。
 * 商品不涉及库存与订单，金额、年龄区间与投放窗口校验仅为展示准确性。
 */
@Service
public class DealService {

    private static final String STATUS_ACTIVE = "active";

    private static final String STATUS_INACTIVE = "inactive";

    private static final String DEFAULT_CURRENCY = "USD";

    private static final int MAX_AGE_MONTHS = 216;

    private final DealMapper dealMapper;

    public DealService(DealMapper dealMapper) {
        this.dealMapper = dealMapper;
    }

    public PageResponse<DealResponse> listPublic(
            PageQuery query, String category, Integer ageMonths, String keyword) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        LambdaQueryWrapper<Deal> wrapper =
                new LambdaQueryWrapper<Deal>()
                        .eq(Deal::getStatus, STATUS_ACTIVE)
                        .and(w -> w.isNull(Deal::getStartsAt).or().le(Deal::getStartsAt, now))
                        .and(w -> w.isNull(Deal::getEndsAt).or().ge(Deal::getEndsAt, now));
        if (category != null && !category.isBlank()) {
            wrapper.eq(Deal::getCategory, normalizeCategory(category));
        }
        if (ageMonths != null) {
            if (ageMonths < 0 || ageMonths > MAX_AGE_MONTHS) {
                throw new ApiException(
                        ApiErrorCode.VALIDATION_ERROR,
                        "ageMonths must be between 0 and 216",
                        Map.of("ageMonths", "must be between 0 and 216"));
            }
            wrapper.and(w -> w.isNull(Deal::getMinAgeMonths).or().le(Deal::getMinAgeMonths, ageMonths))
                    .and(w -> w.isNull(Deal::getMaxAgeMonths).or().ge(Deal::getMaxAgeMonths, ageMonths));
        }
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Deal::getTitle, kw).or().like(Deal::getBrand, kw));
        }
        wrapper.orderByDesc(Deal::getFeatured).orderByDesc(Deal::getId);
        return page(query, wrapper);
    }

    public DealResponse getPublic(long dealId) {
        Deal deal = dealMapper.selectById(dealId);
        if (deal == null || !isVisible(deal, LocalDateTime.now(ZoneOffset.UTC))) {
            throw new ApiException(ApiErrorCode.DEAL_NOT_FOUND);
        }
        return DealResponse.from(deal);
    }

    public List<DealCategoryOption> categories() {
        return Arrays.stream(DealCategory.values())
                .map(category -> new DealCategoryOption(category.code(), category.label()))
                .toList();
    }

    public PageResponse<DealResponse> listAdmin(PageQuery query, String status) {
        LambdaQueryWrapper<Deal> wrapper = new LambdaQueryWrapper<>();
        if (status != null && !status.isBlank()) {
            wrapper.eq(Deal::getStatus, normalizeStatus(status));
        }
        wrapper.orderByDesc(Deal::getId);
        return page(query, wrapper);
    }

    @Transactional
    public DealResponse create(DealRequest request) {
        Deal deal = new Deal();
        deal.setTitle(request.title().trim());
        deal.setBrand(normalizeOptional(request.brand()));
        deal.setCategory(normalizeCategory(request.category()));
        deal.setMinAgeMonths(request.minAgeMonths());
        deal.setMaxAgeMonths(request.maxAgeMonths());
        deal.setOriginalPrice(request.originalPrice());
        deal.setDealPrice(request.dealPrice());
        deal.setCurrency(request.currency() == null ? DEFAULT_CURRENCY : request.currency());
        deal.setMerchant(normalizeOptional(request.merchant()));
        deal.setImageUrl(normalizeOptional(request.imageUrl()));
        deal.setPurchaseUrl(request.purchaseUrl().trim());
        deal.setDescription(normalizeOptional(request.description()));
        deal.setFeatured(Boolean.TRUE.equals(request.featured()));
        deal.setStatus(request.status() == null ? STATUS_ACTIVE : normalizeStatus(request.status()));
        deal.setStartsAt(request.startsAt());
        deal.setEndsAt(request.endsAt());
        validate(deal);

        dealMapper.insert(deal);
        // 回读一次以拿到 DB 生成的 created_at/updated_at
        return DealResponse.from(dealMapper.selectById(deal.getId()));
    }

    @Transactional
    public DealResponse update(long dealId, DealPatchRequest request) {
        Deal deal = requireDeal(dealId);
        if (request.title() != null) {
            deal.setTitle(request.title().trim());
        }
        if (request.brand() != null) {
            deal.setBrand(normalizeOptional(request.brand()));
        }
        if (request.category() != null) {
            deal.setCategory(normalizeCategory(request.category()));
        }
        if (request.minAgeMonths() != null) {
            deal.setMinAgeMonths(request.minAgeMonths());
        }
        if (request.maxAgeMonths() != null) {
            deal.setMaxAgeMonths(request.maxAgeMonths());
        }
        if (request.originalPrice() != null) {
            deal.setOriginalPrice(request.originalPrice());
        }
        if (request.dealPrice() != null) {
            deal.setDealPrice(request.dealPrice());
        }
        if (request.currency() != null) {
            deal.setCurrency(request.currency());
        }
        if (request.merchant() != null) {
            deal.setMerchant(normalizeOptional(request.merchant()));
        }
        if (request.imageUrl() != null) {
            deal.setImageUrl(normalizeOptional(request.imageUrl()));
        }
        if (request.purchaseUrl() != null) {
            deal.setPurchaseUrl(request.purchaseUrl().trim());
        }
        if (request.description() != null) {
            deal.setDescription(normalizeOptional(request.description()));
        }
        if (request.featured() != null) {
            deal.setFeatured(request.featured());
        }
        if (request.status() != null) {
            deal.setStatus(normalizeStatus(request.status()));
        }
        if (request.startsAt() != null) {
            deal.setStartsAt(request.startsAt());
        }
        if (request.endsAt() != null) {
            deal.setEndsAt(request.endsAt());
        }
        validate(deal);

        dealMapper.updateById(deal);
        return DealResponse.from(dealMapper.selectById(dealId));
    }

    @Transactional
    public void delete(long dealId) {
        requireDeal(dealId);
        dealMapper.deleteById(dealId);
    }

    private Deal requireDeal(long dealId) {
        Deal deal = dealMapper.selectById(dealId);
        if (deal == null) {
            throw new ApiException(ApiErrorCode.DEAL_NOT_FOUND);
        }
        return deal;
    }

    private boolean isVisible(Deal deal, LocalDateTime now) {
        if (!STATUS_ACTIVE.equals(deal.getStatus())) {
            return false;
        }
        if (deal.getStartsAt() != null && deal.getStartsAt().isAfter(now)) {
            return false;
        }
        return deal.getEndsAt() == null || !deal.getEndsAt().isBefore(now);
    }

    /** 合并后的整行校验：覆盖 PATCH 局部更新产生的组合非法（如原价 < 优惠价）。 */
    private void validate(Deal deal) {
        if (deal.getDealPrice() == null || deal.getDealPrice().signum() <= 0) {
            throw new ApiException(
                    ApiErrorCode.DEAL_INVALID_DATA, "Deal price must be greater than 0");
        }
        if (deal.getOriginalPrice() != null
                && deal.getOriginalPrice().compareTo(deal.getDealPrice()) < 0) {
            throw new ApiException(
                    ApiErrorCode.DEAL_INVALID_DATA,
                    "Original price must be greater than or equal to deal price",
                    Map.of("originalPrice", "must be greater than or equal to dealPrice"));
        }
        Integer minAge = deal.getMinAgeMonths();
        Integer maxAge = deal.getMaxAgeMonths();
        if ((minAge != null && (minAge < 0 || minAge > MAX_AGE_MONTHS))
                || (maxAge != null && (maxAge < 0 || maxAge > MAX_AGE_MONTHS))) {
            throw new ApiException(
                    ApiErrorCode.DEAL_INVALID_DATA, "Age range must be between 0 and 216 months");
        }
        if (minAge != null && maxAge != null && minAge > maxAge) {
            throw new ApiException(
                    ApiErrorCode.DEAL_INVALID_DATA,
                    "minAgeMonths must be less than or equal to maxAgeMonths",
                    Map.of("maxAgeMonths", "must be greater than or equal to minAgeMonths"));
        }
        if (deal.getStartsAt() != null
                && deal.getEndsAt() != null
                && !deal.getEndsAt().isAfter(deal.getStartsAt())) {
            throw new ApiException(
                    ApiErrorCode.DEAL_INVALID_DATA,
                    "endsAt must be after startsAt",
                    Map.of("endsAt", "must be after startsAt"));
        }
        if (!DEFAULT_CURRENCY.equals(deal.getCurrency())) {
            throw new ApiException(
                    ApiErrorCode.DEAL_INVALID_DATA,
                    "Only USD is supported in v1",
                    Map.of("currency", "only USD is supported in v1"));
        }
        if (deal.getPurchaseUrl() == null || !deal.getPurchaseUrl().matches("^https?://.+")) {
            throw new ApiException(
                    ApiErrorCode.DEAL_INVALID_DATA,
                    "purchaseUrl must be an http(s) URL",
                    Map.of("purchaseUrl", "must be an http(s) URL"));
        }
    }

    private String normalizeCategory(String category) {
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        try {
            return DealCategory.fromCode(normalized).code();
        } catch (IllegalArgumentException ex) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_ERROR,
                    "Unsupported deal category",
                    Map.of("category", "must be one of: " + DealCategory.PATTERN));
        }
    }

    private String normalizeStatus(String status) {
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        if (!STATUS_ACTIVE.equals(normalized) && !STATUS_INACTIVE.equals(normalized)) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_ERROR,
                    "Unsupported deal status",
                    Map.of("status", "must be active or inactive"));
        }
        return normalized;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private PageResponse<DealResponse> page(PageQuery query, LambdaQueryWrapper<Deal> wrapper) {
        Page<Deal> page = new Page<>(query.page(), query.pageSize());
        dealMapper.selectPage(page, wrapper);
        return new PageResponse<>(
                page.getRecords().stream().map(DealResponse::from).toList(),
                new PageResponse.Pagination(
                        page.getCurrent(), page.getSize(), page.getTotal(), page.getPages()));
    }
}
