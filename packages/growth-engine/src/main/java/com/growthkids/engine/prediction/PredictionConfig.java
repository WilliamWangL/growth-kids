package com.growthkids.engine.prediction;

import java.math.BigDecimal;

/**
 * 预测模型配置（规范 §42：权重必须集中在配置，不得散落在业务代码）。
 *
 * <p>默认值：
 *
 * <ul>
 *   <li>权重：percentile 0.35 / parental 0.30 / velocity 0.20 / age_model 0.15
 *   <li>成年参照：男 176.0cm±7.0；女 163.0cm±6.5（公开中位身高的近似值）
 *   <li>速度比限幅 [0.8, 1.2]；z 限幅 ±3；预测区间半宽 = 成年 SD × 置信度系数
 *       （low 1.5 / medium 1.0 / high 0.75）
 * </ul>
 */
public record PredictionConfig(
        BigDecimal weightPercentile,
        BigDecimal weightParental,
        BigDecimal weightVelocity,
        BigDecimal weightAgeModel,
        BigDecimal adultMedianMale,
        BigDecimal adultMedianFemale,
        BigDecimal adultSdMale,
        BigDecimal adultSdFemale,
        BigDecimal minVelocityRatio,
        BigDecimal maxVelocityRatio,
        BigDecimal maxAbsZ,
        BigDecimal rangeFactorLow,
        BigDecimal rangeFactorMedium,
        BigDecimal rangeFactorHigh,
        int minMediumSpanDays,
        int minHighRecords,
        int minHighSpanDays) {

    /** 规范 §42 推荐权重与 V1 参照常量。 */
    public static PredictionConfig defaults() {
        return new PredictionConfig(
                new BigDecimal("0.35"),
                new BigDecimal("0.30"),
                new BigDecimal("0.20"),
                new BigDecimal("0.15"),
                new BigDecimal("176.0"),
                new BigDecimal("163.0"),
                new BigDecimal("7.0"),
                new BigDecimal("6.5"),
                new BigDecimal("0.8"),
                new BigDecimal("1.2"),
                new BigDecimal("3.0"),
                new BigDecimal("1.5"),
                new BigDecimal("1.0"),
                new BigDecimal("0.75"),
                90,
                3,
                180);
    }
}

