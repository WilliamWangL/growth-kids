package com.growthkids.engine.prediction;

import java.math.BigDecimal;
import java.util.List;

/**
 * 身高预测输出（规范 §40）。
 *
 * <p>可选字段（currentPercentile/growthVelocityCmPerYear/midParentalHeightCm）
 * 在对应输入缺失时为 null；confidence 是数据完整度（§43），不是医学意义准确率。
 */
public record HeightPredictionResult(
        int currentAgeMonths,
        BigDecimal currentHeightCm,
        BigDecimal currentPercentile,
        BigDecimal growthVelocityCmPerYear,
        BigDecimal midParentalHeightCm,
        BigDecimal predictedAdultHeightCm,
        BigDecimal lowerRangeCm,
        BigDecimal upperRangeCm,
        String confidence,
        List<YearlyPrediction> yearlyPredictions) {

    /** 单年龄段预测点（age 为周岁）。 */
    public record YearlyPrediction(
            int age, BigDecimal predictedHeightCm, BigDecimal lowerCm, BigDecimal upperCm) {}
}

