package com.growthkids.api.deal.dto;

import com.growthkids.api.deal.DealCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 新建优惠商品（TASK-022，admin）。
 *
 * <p>缺省值由服务层补齐：currency=USD、status=active、featured=false；
 * 投放窗口 startsAt/endsAt 均可为空（表示长期有效）。v1 仅支持 USD。
 */
public record DealRequest(
        @NotBlank @Size(max = 200) String title,
        @Size(max = 100) String brand,
        @NotBlank
                @Pattern(regexp = DealCategory.PATTERN, message = "must be one of: " + DealCategory.PATTERN)
                String category,
        @Min(0) @Max(216) Integer minAgeMonths,
        @Min(0) @Max(216) Integer maxAgeMonths,
        @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal originalPrice,
        @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0")
                @Digits(integer = 8, fraction = 2)
                BigDecimal dealPrice,
        @Pattern(regexp = "USD", message = "only USD is supported in v1") String currency,
        @Size(max = 100) String merchant,
        @Size(max = 500) String imageUrl,
        @NotBlank
                @Size(max = 1000)
                @Pattern(regexp = "https?://.+", message = "must be an http(s) URL")
                String purchaseUrl,
        @Size(max = 4000) String description,
        Boolean featured,
        @Pattern(regexp = "active|inactive", message = "must be active or inactive") String status,
        LocalDateTime startsAt,
        LocalDateTime endsAt) {}
