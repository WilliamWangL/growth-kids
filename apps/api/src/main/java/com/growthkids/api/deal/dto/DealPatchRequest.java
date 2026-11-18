package com.growthkids.api.deal.dto;

import com.growthkids.api.deal.DealCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 局部更新优惠商品（TASK-022，admin，PATCH 语义）。
 *
 * <p>字段为 null 表示「保持不变」（与成长记录 PATCH 一致）；
 * 文本字段传空串表示「清空」。startsAt/endsAt 暂不支持通过 PATCH 清空，
 * 如需解除投放窗口请把 endsAt 调整为远期时间。
 */
public record DealPatchRequest(
        @Size(min = 1, max = 200) String title,
        @Size(max = 100) String brand,
        @Pattern(regexp = DealCategory.PATTERN, message = "must be one of: " + DealCategory.PATTERN)
                String category,
        @Min(0) @Max(216) Integer minAgeMonths,
        @Min(0) @Max(216) Integer maxAgeMonths,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal originalPrice,
        @DecimalMin(value = "0.01", message = "must be greater than 0")
                @Digits(integer = 8, fraction = 2)
                BigDecimal dealPrice,
        @Pattern(regexp = "USD", message = "only USD is supported in v1") String currency,
        @Size(max = 100) String merchant,
        @Size(max = 500) String imageUrl,
        @Size(max = 1000)
                @Pattern(regexp = "https?://.+", message = "must be an http(s) URL")
                String purchaseUrl,
        @Size(max = 4000) String description,
        Boolean featured,
        @Pattern(regexp = "active|inactive", message = "must be active or inactive") String status,
        LocalDateTime startsAt,
        LocalDateTime endsAt) {}
