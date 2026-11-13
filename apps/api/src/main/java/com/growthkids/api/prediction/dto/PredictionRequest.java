package com.growthkids.api.prediction.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 公开身高预测入参（TASK-021，规范 §75）：无需登录。
 *
 * <p>父母身高可选：缺失时引擎按比例重分配权重（§42），置信度相应降级（§43）。
 */
public record PredictionRequest(
        @NotBlank
                @Pattern(regexp = "male|female", message = "must be one of: male, female")
                String gender,
        @NotNull LocalDate birthDate,
        @NotNull @DecimalMin("20") @DecimalMax("250") BigDecimal heightCm,
        @DecimalMin("100") @DecimalMax("250") BigDecimal fatherHeightCm,
        @DecimalMin("100") @DecimalMax("250") BigDecimal motherHeightCm) {}
