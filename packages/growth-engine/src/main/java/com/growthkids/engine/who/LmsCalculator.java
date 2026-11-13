package com.growthkids.engine.who;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * WHO LMS 生长标准计算（TASK-009，规范 §34/§35）：Z-score 与百分位。
 *
 * <p>Z-score（LMS 公式）：
 *
 * <ul>
 *   <li>L ≠ 0：Z = ((X / M)^L - 1) / (L × S)
 *   <li>L = 0：Z = ln(X / M) / S
 * </ul>
 *
 * <p>百分位：P = Φ(Z) × 100（0-100）。结果均保留 4 位小数（HALF_UP），
 * 与 growth_records.z DECIMAL(8,4) / percentile DECIMAL(7,4) 对齐。
 */
public final class LmsCalculator {

    /** Z-score 结果小数位数（对齐数据库列精度）。 */
    public static final int Z_SCALE = 4;

    /** 百分位结果小数位数（对齐数据库列精度）。 */
    public static final int PERCENTILE_SCALE = 4;

    private LmsCalculator() {
        // 工具类禁止实例化
    }

    /**
     * 计算 Z-score。
     *
     * @param value 测量值 X（必须为正）
     * @param l LMS 的 L（可为任意实数，含 0 与负数）
     * @param m LMS 的 M（必须为正）
     * @param s LMS 的 S（必须为正）
     * @throws IllegalArgumentException 参数为空或非法
     */
    public static BigDecimal calculateZScore(
            BigDecimal value, BigDecimal l, BigDecimal m, BigDecimal s) {
        if (l == null) {
            throw new IllegalArgumentException("l must not be null");
        }
        requirePositive(value, "value");
        requirePositive(m, "m");
        requirePositive(s, "s");

        double x = value.doubleValue();
        double lv = l.doubleValue();
        double mv = m.doubleValue();
        double sv = s.doubleValue();

        double z;
        if (lv != 0.0) {
            z = (Math.pow(x / mv, lv) - 1.0) / (lv * sv);
        } else {
            z = Math.log(x / mv) / sv;
        }
        return BigDecimal.valueOf(z).setScale(Z_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Z-score → 百分位（P = Φ(Z) × 100，0-100）。
     *
     * @throws IllegalArgumentException zScore 为空
     */
    public static BigDecimal calculatePercentile(BigDecimal zScore) {
        if (zScore == null) {
            throw new IllegalArgumentException("zScore must not be null");
        }
        double percentile = NormalDistribution.cdf(zScore.doubleValue()) * 100.0;
        return BigDecimal.valueOf(percentile).setScale(PERCENTILE_SCALE, RoundingMode.HALF_UP);
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.signum() <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}

