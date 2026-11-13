package com.growthkids.api.prediction.dto;

import com.growthkids.engine.prediction.HeightPredictionResult;
import java.math.BigDecimal;
import java.util.List;

/**
 * 身高预测响应（TASK-021，规范 §40/§43/§44）。
 *
 * <p>可选字段（currentPercentile/growthVelocityCmPerYear/midParentalHeightCm）在对应
 * 输入缺失时省略（Jackson non_null）；confidence 是数据完整度，不是医学意义准确率。
 */
public record PredictionResponse(
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

    /** 单年龄段预测点（age 为周岁；§44 页面展示 6-18 岁）。 */
    public record YearlyPrediction(
            int age, BigDecimal predictedHeightCm, BigDecimal lowerCm, BigDecimal upperCm) {

        static YearlyPrediction from(HeightPredictionResult.YearlyPrediction point) {
            return new YearlyPrediction(
                    point.age(), point.predictedHeightCm(), point.lowerCm(), point.upperCm());
        }
    }

    public static PredictionResponse from(HeightPredictionResult result) {
        return new PredictionResponse(
                result.currentAgeMonths(),
                result.currentHeightCm(),
                result.currentPercentile(),
                result.growthVelocityCmPerYear(),
                result.midParentalHeightCm(),
                result.predictedAdultHeightCm(),
                result.lowerRangeCm(),
                result.upperRangeCm(),
                result.confidence(),
                result.yearlyPredictions().stream().map(YearlyPrediction::from).toList());
    }
}
