package com.growthkids.api.deal.dto;

import com.growthkids.api.deal.DealCategory;
import com.growthkids.api.deal.entity.Deal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 优惠商品响应（TASK-022）：公开列表/详情与后台列表共用。
 *
 * <p>discountPercent 为服务端计算值（原价缺失时为 null），
 * 避免各端重复实现折扣取整规则。
 */
public record DealResponse(
        long id,
        String title,
        String brand,
        String category,
        String categoryLabel,
        Integer minAgeMonths,
        Integer maxAgeMonths,
        BigDecimal originalPrice,
        BigDecimal dealPrice,
        Integer discountPercent,
        String currency,
        String merchant,
        String imageUrl,
        String purchaseUrl,
        String description,
        boolean featured,
        String status,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static DealResponse from(Deal deal) {
        return new DealResponse(
                deal.getId(),
                deal.getTitle(),
                deal.getBrand(),
                deal.getCategory(),
                labelOf(deal.getCategory()),
                deal.getMinAgeMonths(),
                deal.getMaxAgeMonths(),
                deal.getOriginalPrice(),
                deal.getDealPrice(),
                discountPercent(deal.getOriginalPrice(), deal.getDealPrice()),
                deal.getCurrency(),
                deal.getMerchant(),
                deal.getImageUrl(),
                deal.getPurchaseUrl(),
                deal.getDescription(),
                Boolean.TRUE.equals(deal.getFeatured()),
                deal.getStatus(),
                deal.getStartsAt(),
                deal.getEndsAt(),
                deal.getCreatedAt(),
                deal.getUpdatedAt());
    }

    /** 未知 code 不抛异常：历史数据分类下线时仍能正常展示。 */
    private static String labelOf(String code) {
        if (code == null) {
            return null;
        }
        try {
            return DealCategory.fromCode(code).label();
        } catch (IllegalArgumentException ex) {
            return code;
        }
    }

    private static Integer discountPercent(BigDecimal originalPrice, BigDecimal dealPrice) {
        if (originalPrice == null || dealPrice == null || originalPrice.signum() <= 0) {
            return null;
        }
        BigDecimal discount = originalPrice.subtract(dealPrice);
        if (discount.signum() <= 0) {
            return 0;
        }
        return discount.multiply(BigDecimal.valueOf(100))
                .divide(originalPrice, 0, RoundingMode.HALF_UP)
                .intValue();
    }
}
