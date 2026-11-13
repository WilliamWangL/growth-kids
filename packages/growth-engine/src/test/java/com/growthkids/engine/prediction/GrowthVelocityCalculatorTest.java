package com.growthkids.engine.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 生长速度单测（TASK-010，规范 §40 growthVelocityCmPerYear）：
 *
 * <ul>
 *   <li>速度 = Δ身高 / 时间跨度（年），保留 2 位小数（cm/年）
 *   <li>需要至少 2 条有效测量且跨度 ≥ 28 天，否则 null
 *   <li>无效测量过滤、同日重复以最后一条为准、乱序输入按日期升序
 * </ul>
 */
class GrowthVelocityCalculatorTest {

    @Test
    void computesYearlyVelocityFromTwoMeasurements() {
        // 184 天增长 4cm → 4 / (184/365.25) ≈ 7.94 cm/年
        assertEquals(
                new BigDecimal("7.94"),
                GrowthVelocityCalculator.calculate(
                        List.of(
                                measurement("2025-07-01", "116"),
                                measurement("2026-01-01", "120"))));
    }

    @Test
    void sortsUnorderedMeasurements() {
        // 乱序输入结果一致（内部按日期升序）
        assertEquals(
                new BigDecimal("7.94"),
                GrowthVelocityCalculator.calculate(
                        List.of(
                                measurement("2026-01-01", "120"),
                                measurement("2025-07-01", "116"))));
    }

    @Test
    void keepsLastValueForDuplicateDates() {
        // 同日重复以最后一条为准：2026-01-01 取 120（而非 119）
        assertEquals(
                new BigDecimal("7.94"),
                GrowthVelocityCalculator.calculate(
                        List.of(
                                measurement("2026-01-01", "119"),
                                measurement("2025-07-01", "116"),
                                measurement("2026-01-01", "120"))));
    }

    @Test
    void filtersInvalidMeasurements() {
        // null 元素/日期缺失/身高缺失/非正身高均被过滤，有效序列同主用例
        assertEquals(
                new BigDecimal("7.94"),
                GrowthVelocityCalculator.calculate(
                        Arrays.asList(
                                measurement("2025-07-01", "116"),
                                null,
                                new HeightMeasurement(null, new BigDecimal("115")),
                                new HeightMeasurement(LocalDate.parse("2025-10-01"), null),
                                measurement("2025-11-01", "0"),
                                measurement("2025-12-01", "-5"),
                                measurement("2026-01-01", "120"))));
    }

    @Test
    void returnsNullWhenFewerThanTwoValidMeasurements() {
        assertNull(GrowthVelocityCalculator.calculate(null));
        assertNull(GrowthVelocityCalculator.calculate(List.of()));
        assertNull(
                GrowthVelocityCalculator.calculate(
                        List.of(measurement("2026-01-01", "120"))));
    }

    @Test
    void returnsNullWhenSpanShorterThanMinimum() {
        // 27 天 < 28 天下限
        assertNull(
                GrowthVelocityCalculator.calculate(
                        List.of(
                                measurement("2026-01-01", "120"),
                                measurement("2026-01-28", "121"))));
    }

    @Test
    void computesVelocityAtMinimumSpan() {
        // 恰好 28 天增长 1cm → 1 / (28/365.25) ≈ 13.04 cm/年
        assertEquals(
                new BigDecimal("13.04"),
                GrowthVelocityCalculator.calculate(
                        List.of(
                                measurement("2026-01-01", "120"),
                                measurement("2026-01-29", "121"))));
    }

    @Test
    void supportsNegativeVelocity() {
        // 184 天减少 2cm → -3.97 cm/年
        assertEquals(
                new BigDecimal("-3.97"),
                GrowthVelocityCalculator.calculate(
                        List.of(
                                measurement("2025-07-01", "116"),
                                measurement("2026-01-01", "114"))));
    }

    private static HeightMeasurement measurement(String date, String heightCm) {
        return new HeightMeasurement(LocalDate.parse(date), new BigDecimal(heightCm));
    }
}
