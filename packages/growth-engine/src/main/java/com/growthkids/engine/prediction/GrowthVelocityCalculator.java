package com.growthkids.engine.prediction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * 生长速度（规范 §40 growthVelocityCmPerYear）：历史平均速度 = 身高变化 / 时间跨度（年）。
 *
 * <p>口径：
 *
 * <ul>
 *   <li>过滤无效测量（日期/身高缺失、身高非正），同日重复以最后一条为准，按日期升序
 *   <li>需要至少 2 条有效测量且跨度 ≥ {@value #MIN_SPAN_DAYS} 天，否则返回 null
 *   <li>结果保留 2 位小数（cm/年）
 * </ul>
 */
public final class GrowthVelocityCalculator {

    /** 最小有效跨度（天）：低于此值无法稳定估算年化速度。 */
    public static final int MIN_SPAN_DAYS = 28;

    /** 一年天数（含闰年平均）。 */
    private static final BigDecimal DAYS_PER_YEAR = new BigDecimal("365.25");

    /** 结果小数位数。 */
    public static final int SCALE = 2;

    private GrowthVelocityCalculator() {
        // 工具类禁止实例化
    }

    /** 计算历史平均生长速度（cm/年）；不满足条件时返回 null。 */
    public static BigDecimal calculate(List<HeightMeasurement> measurements) {
        List<HeightMeasurement> valid = validSorted(measurements);
        if (valid.size() < 2) {
            return null;
        }
        HeightMeasurement first = valid.get(0);
        HeightMeasurement last = valid.get(valid.size() - 1);
        long days = ChronoUnit.DAYS.between(first.date(), last.date());
        if (days < MIN_SPAN_DAYS) {
            return null;
        }
        BigDecimal deltaCm = last.heightCm().subtract(first.heightCm());
        BigDecimal years =
                BigDecimal.valueOf(days).divide(DAYS_PER_YEAR, 6, RoundingMode.HALF_UP);
        return deltaCm.divide(years, SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 清洗测量序列：过滤无效项，同日重复以最后一条为准，返回按日期升序的列表。
     *
     * <p>预测引擎的置信度判定与年度预测共用此口径，保证一致。
     */
    public static List<HeightMeasurement> validSorted(List<HeightMeasurement> measurements) {
        if (measurements == null) {
            return List.of();
        }
        Map<LocalDate, BigDecimal> byDate = new TreeMap<>();
        for (HeightMeasurement measurement : measurements) {
            if (measurement == null
                    || measurement.date() == null
                    || measurement.heightCm() == null
                    || measurement.heightCm().signum() <= 0) {
                continue;
            }
            byDate.put(measurement.date(), measurement.heightCm());
        }
        return byDate.entrySet().stream()
                .map(entry -> new HeightMeasurement(entry.getKey(), entry.getValue()))
                .toList();
    }
}

