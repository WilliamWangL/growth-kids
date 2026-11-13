package com.growthkids.api.who.service;

import java.math.BigDecimal;

/**
 * 单条成长记录可得的 WHO 指标（z-score / percentile，规范 §34）。
 *
 * <p>每个指标独立匹配：无测量值、无对应锚点或性别无法匹配时该指标为 null；
 * whoVersion 仅在至少计算出一个指标时非空（规范 §124）。
 */
public record WhoMetrics(
        BigDecimal heightZ,
        BigDecimal heightPercentile,
        BigDecimal weightZ,
        BigDecimal weightPercentile,
        BigDecimal bmiZ,
        BigDecimal bmiPercentile,
        BigDecimal headZ,
        BigDecimal headPercentile,
        String whoVersion) {

    /** 无法计算（性别 unknown / WHO 数据缺失）时的空结果。 */
    public static WhoMetrics none() {
        return new WhoMetrics(null, null, null, null, null, null, null, null, null);
    }
}

