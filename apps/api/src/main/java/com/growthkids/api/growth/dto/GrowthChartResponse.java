package com.growthkids.api.growth.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 成长曲线响应（TASK-012，规范 §33/§58）：
 * 身高/体重/BMI/头围四组序列；某条记录未填写该测量时不产生对应点，
 * WHO percentile 缺失（如 gender=unknown）时字段省略（Jackson non_null）。
 */
public record GrowthChartResponse(
        MetricSeries height,
        MetricSeries weight,
        MetricSeries bmi,
        MetricSeries headCircumference) {

    public record MetricSeries(List<ChartPoint> records) {}

    /** ageMonths 为记录日期相对出生日期的整月龄（§37 totalMonths）。 */
    public record ChartPoint(LocalDate date, int ageMonths, BigDecimal value, BigDecimal percentile) {}
}

