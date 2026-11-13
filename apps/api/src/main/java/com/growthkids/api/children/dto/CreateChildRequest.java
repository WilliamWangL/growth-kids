package com.growthkids.api.children.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

/** 创建孩子（规范 §28）。 */
public record CreateChildRequest(
        @NotBlank @Size(max = 100) String name,
        @NotBlank
                @Pattern(regexp = "male|female|unknown", message = "must be one of: male, female, unknown")
                String gender,
        @NotNull LocalDate birthDate,
        @DecimalMin("20") @DecimalMax("70") BigDecimal birthHeightCm,
        @DecimalMin("0.3") @DecimalMax("12") BigDecimal birthWeightKg,
        @DecimalMin("100") @DecimalMax("250") BigDecimal fatherHeightCm,
        @DecimalMin("100") @DecimalMax("250") BigDecimal motherHeightCm,
        @Size(max = 500) String avatarUrl,
        @Size(max = 2000) String notes) {}
