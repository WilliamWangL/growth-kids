package com.growthkids.engine.bmi;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * BMI 计算（规范 §36）：BMI = weightKg / (heightM × heightM)。
 *
 * <p>结果保留 3 位小数（HALF_UP），与 growth_records.bmi DECIMAL(6,3) 对齐。
 */
public final class BmiCalculator {

    /** 结果小数位数（对齐数据库列精度）。 */
    public static final int SCALE = 3;

    private BmiCalculator() {
        // 工具类禁止实例化
    }

    /**
     * 计算 BMI。
     *
     * @param heightCm 身高（cm，必须为正）
     * @param weightKg 体重（kg，必须为正）
     * @throws IllegalArgumentException 参数为空或非正
     */
    public static BigDecimal calculate(BigDecimal heightCm, BigDecimal weightKg) {
        if (heightCm == null || weightKg == null) {
            throw new IllegalArgumentException("heightCm and weightKg must not be null");
        }
        if (heightCm.signum() <= 0 || weightKg.signum() <= 0) {
            throw new IllegalArgumentException("heightCm and weightKg must be positive");
        }
        BigDecimal heightM = heightCm.movePointLeft(2);
        BigDecimal heightSquared = heightM.multiply(heightM);
        return weightKg.divide(heightSquared, SCALE, RoundingMode.HALF_UP);
    }
}
