package com.growthkids.engine.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 身高预测引擎单测（TASK-010，规范 §38-§45）。
 *
 * <p>期望值按 BigDecimal HALF_UP 语义手工推导并用 Python decimal 复核；cm 断言容差 ±0.05
 * （覆盖链式舍入），percentile 容差 ±0.001（erf 近似误差 &lt; 1.5e-7）。
 */
class HeightPredictionEngineTest {

    private final HeightPredictionEngine engine = new HeightPredictionEngine();

    @Test
    void predictsWithAllFactorsAvailable() {
        // 男，2020-01-01 → 2026-01-01（6 岁/72 月）：120cm、z=0.5、两条测量、父母齐全
        // 速度比 7.94/5.808=1.3671 超上限 1.2 → 速度因子限幅（不限幅预测将达 184.8）
        HeightPredictionResult result =
                engine.predict(
                        new HeightPredictionInput(
                                "male",
                                LocalDate.parse("2020-01-01"),
                                LocalDate.parse("2026-01-01"),
                                new BigDecimal("120"),
                                new BigDecimal("0.5"),
                                List.of(
                                        measurement("2025-07-01", "116"),
                                        measurement("2026-01-01", "120")),
                                new BigDecimal("180"),
                                new BigDecimal("165")));

        assertEquals(72, result.currentAgeMonths());
        assertEquals(new BigDecimal("120"), result.currentHeightCm());
        assertWithin(new BigDecimal("69.1462"), result.currentPercentile(), 0.001);
        assertEquals(new BigDecimal("7.94"), result.growthVelocityCmPerYear());
        assertEquals(new BigDecimal("179.0"), result.midParentalHeightCm());
        assertWithin(new BigDecimal("182.7"), result.predictedAdultHeightCm(), 0.05);
        assertWithin(new BigDecimal("175.7"), result.lowerRangeCm(), 0.05);
        assertWithin(new BigDecimal("189.7"), result.upperRangeCm(), 0.05);
        assertEquals("medium", result.confidence());

        // 年度预测 7..18 岁共 12 个点（§44 页面展示范围）
        List<HeightPredictionResult.YearlyPrediction> yearly = result.yearlyPredictions();
        assertEquals(12, yearly.size());
        assertEquals(7, yearly.get(0).age());
        assertWithin(new BigDecimal("126.1"), yearly.get(0).predictedHeightCm(), 0.05);
        assertWithin(new BigDecimal("119.1"), yearly.get(0).lowerCm(), 0.05);
        assertWithin(new BigDecimal("133.1"), yearly.get(0).upperCm(), 0.05);
        assertEquals(18, yearly.get(11).age());
        assertWithin(new BigDecimal("182.7"), yearly.get(11).predictedHeightCm(), 0.05);
    }

    @Test
    void predictsWithMinimalData() {
        // 男，2025-01-01 → 2026-01-01（1 岁/12 月）：76cm、无 z、单条测量、无父母
        // 仅年龄模型因子可用：76 / 0.43 = 176.7442 → 176.7（§43 数据不足）
        HeightPredictionResult result =
                engine.predict(
                        new HeightPredictionInput(
                                "male",
                                LocalDate.parse("2025-01-01"),
                                LocalDate.parse("2026-01-01"),
                                new BigDecimal("76"),
                                null,
                                List.of(measurement("2026-01-01", "76")),
                                null,
                                null));

        assertEquals(12, result.currentAgeMonths());
        assertNull(result.currentPercentile());
        assertNull(result.growthVelocityCmPerYear());
        assertNull(result.midParentalHeightCm());
        assertWithin(new BigDecimal("176.7"), result.predictedAdultHeightCm(), 0.05);
        // low：半宽 = 7.0 × 1.5 = 10.5
        assertWithin(new BigDecimal("166.2"), result.lowerRangeCm(), 0.05);
        assertWithin(new BigDecimal("187.2"), result.upperRangeCm(), 0.05);
        assertEquals("low", result.confidence());

        // 年度预测从 6 岁起（页面展示下限）
        List<HeightPredictionResult.YearlyPrediction> yearly = result.yearlyPredictions();
        assertEquals(13, yearly.size());
        assertEquals(6, yearly.get(0).age());
        assertWithin(new BigDecimal("116.5"), yearly.get(0).predictedHeightCm(), 0.05);
        assertWithin(new BigDecimal("106.0"), yearly.get(0).lowerCm(), 0.05);
        assertWithin(new BigDecimal("127.0"), yearly.get(0).upperCm(), 0.05);
    }

    @Test
    void predictsWithHighConfidenceWhenThreeOrMoreMeasurements() {
        // 女，2018-05-01 → 2026-01-01（7 岁/92 月）：127cm、z=1.0、四条测量、父母齐全
        // §43 high：≥3 条有效记录 + 跨度 579 天 ≥ 180 + 父母齐全
        HeightPredictionResult result =
                engine.predict(
                        new HeightPredictionInput(
                                "female",
                                LocalDate.parse("2018-05-01"),
                                LocalDate.parse("2026-01-01"),
                                new BigDecimal("127"),
                                new BigDecimal("1.0"),
                                List.of(
                                        measurement("2024-06-01", "118"),
                                        measurement("2025-03-01", "122"),
                                        measurement("2025-09-01", "125.5"),
                                        measurement("2026-01-01", "127")),
                                new BigDecimal("172"),
                                new BigDecimal("158")));

        assertEquals(92, result.currentAgeMonths());
        assertWithin(new BigDecimal("84.1345"), result.currentPercentile(), 0.001);
        assertEquals(new BigDecimal("5.68"), result.growthVelocityCmPerYear());
        assertEquals(new BigDecimal("158.5"), result.midParentalHeightCm());
        assertWithin(new BigDecimal("166.9"), result.predictedAdultHeightCm(), 0.05);
        assertWithin(new BigDecimal("162.0"), result.lowerRangeCm(), 0.05);
        assertWithin(new BigDecimal("171.8"), result.upperRangeCm(), 0.05);
        assertEquals("high", result.confidence());

        // high：半宽 = 6.5 × 0.75 = 4.875；年度从 8 岁起
        List<HeightPredictionResult.YearlyPrediction> yearly = result.yearlyPredictions();
        assertEquals(11, yearly.size());
        assertEquals(8, yearly.get(0).age());
        assertWithin(new BigDecimal("132.3"), yearly.get(0).predictedHeightCm(), 0.05);
        assertWithin(new BigDecimal("127.4"), yearly.get(0).lowerCm(), 0.05);
        assertWithin(new BigDecimal("137.2"), yearly.get(0).upperCm(), 0.05);
        assertEquals(18, yearly.get(10).age());
        assertWithin(new BigDecimal("166.9"), yearly.get(10).predictedHeightCm(), 0.05);
    }

    @Test
    void withoutParentHeightsConfidenceDropsToMedium() {
        // 同上一场景但缺父母：parental 因子权重按比例重分配（0.35/0.20/0.15）
        HeightPredictionResult result =
                engine.predict(
                        new HeightPredictionInput(
                                "female",
                                LocalDate.parse("2018-05-01"),
                                LocalDate.parse("2026-01-01"),
                                new BigDecimal("127"),
                                new BigDecimal("1.0"),
                                List.of(
                                        measurement("2024-06-01", "118"),
                                        measurement("2025-03-01", "122"),
                                        measurement("2025-09-01", "125.5"),
                                        measurement("2026-01-01", "127")),
                                null,
                                null));

        assertNull(result.midParentalHeightCm());
        assertEquals("medium", result.confidence());
        assertWithin(new BigDecimal("170.5"), result.predictedAdultHeightCm(), 0.05);
        // medium：半宽 = 6.5 × 1.0 = 6.5
        assertWithin(new BigDecimal("164.0"), result.lowerRangeCm(), 0.05);
        assertWithin(new BigDecimal("177.0"), result.upperRangeCm(), 0.05);
    }

    @Test
    void zScoreIsClampedToConfiguredLimit() {
        // z=5 超过限幅 ±3 → percentile 按 z=3 计算：Φ(3) = 99.8650
        HeightPredictionResult result =
                engine.predict(
                        new HeightPredictionInput(
                                "male",
                                LocalDate.parse("2020-01-01"),
                                LocalDate.parse("2026-01-01"),
                                new BigDecimal("120"),
                                new BigDecimal("5"),
                                List.of(),
                                null,
                                null));

        assertWithin(new BigDecimal("99.8650"), result.currentPercentile(), 0.001);
        assertEquals("low", result.confidence());
    }

    @Test
    void customWeightsAreRespected() {
        // 权重全部给年龄模型 → predicted = 120 / 0.659 = 182.0941 → 182.1
        PredictionConfig config =
                new PredictionConfig(
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ZERO,
                        BigDecimal.ONE,
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
        HeightPredictionEngine custom = new HeightPredictionEngine(config);

        HeightPredictionResult result =
                custom.predict(
                        new HeightPredictionInput(
                                "male",
                                LocalDate.parse("2020-01-01"),
                                LocalDate.parse("2026-01-01"),
                                new BigDecimal("120"),
                                new BigDecimal("0.5"),
                                List.of(
                                        measurement("2025-07-01", "116"),
                                        measurement("2026-01-01", "120")),
                                new BigDecimal("180"),
                                new BigDecimal("165")));

        assertWithin(new BigDecimal("182.1"), result.predictedAdultHeightCm(), 0.05);
        assertWithin(new BigDecimal("175.1"), result.lowerRangeCm(), 0.05);
        assertWithin(new BigDecimal("189.1"), result.upperRangeCm(), 0.05);
    }

    @Test
    void rejectsInvalidInput() {
        // gender 非 male/female
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.predict(input("other", "2020-01-01", "2026-01-01", "120")));
        // asOfDate 早于 birthDate
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.predict(input("male", "2026-01-02", "2026-01-01", "120")));
        // 当前身高为 0
        assertThrows(
                IllegalArgumentException.class,
                () -> engine.predict(input("male", "2020-01-01", "2026-01-01", "0")));
        // 当前身高缺失
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        engine.predict(
                                new HeightPredictionInput(
                                        "male",
                                        LocalDate.parse("2020-01-01"),
                                        LocalDate.parse("2026-01-01"),
                                        null,
                                        null,
                                        List.of(),
                                        null,
                                        null)));
        // 空输入
        assertThrows(IllegalArgumentException.class, () -> engine.predict(null));
    }

    private static HeightMeasurement measurement(String date, String heightCm) {
        return new HeightMeasurement(LocalDate.parse(date), new BigDecimal(heightCm));
    }

    private static HeightPredictionInput input(
            String gender, String birthDate, String asOfDate, String heightCm) {
        return new HeightPredictionInput(
                gender,
                LocalDate.parse(birthDate),
                LocalDate.parse(asOfDate),
                new BigDecimal(heightCm),
                null,
                List.of(),
                null,
                null);
    }

    private static void assertWithin(BigDecimal expected, BigDecimal actual, double tolerance) {
        assertTrue(
                expected.subtract(actual).abs().compareTo(BigDecimal.valueOf(tolerance)) <= 0,
                () -> "expected " + expected + " ± " + tolerance + " but was " + actual);
    }
}
