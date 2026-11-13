package com.growthkids.engine.who;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * LMS Z-score / 百分位单测（TASK-009，规范 §34）。
 *
 * <p>百分位与标准正态表对照：Φ(1)=84.1345、Φ(1.96)=97.5002、Φ(-1.96)=2.4998、Φ(3)=99.8650
 * （erf 近似误差 &lt; 1.5e-7，断言容差 0.001 远大于该误差）。
 */
class LmsCalculatorTest {

    @Test
    void zScoreIsZeroWhenValueEqualsMedian() {
        BigDecimal z =
                LmsCalculator.calculateZScore(
                        new BigDecimal("50"),
                        new BigDecimal("1.0"),
                        new BigDecimal("50"),
                        new BigDecimal("0.04"));
        assertEquals(new BigDecimal("0.0000"), z);
        assertEquals(new BigDecimal("50.0000"), LmsCalculator.calculatePercentile(z));
    }

    @Test
    void zScoreUsesPowerFormulaWhenLNonZero() {
        // (55/50 - 1) / (1 × 0.04) = 2.5
        assertEquals(
                new BigDecimal("2.5000"),
                LmsCalculator.calculateZScore(
                        new BigDecimal("55"),
                        new BigDecimal("1"),
                        new BigDecimal("50"),
                        new BigDecimal("0.04")));
    }

    @Test
    void zScoreSupportsNegativeL() {
        // ((17.5/15.8)^-0.5 - 1) / (-0.5 × 0.08) ≈ 1.2453
        assertEquals(
                new BigDecimal("1.2453"),
                LmsCalculator.calculateZScore(
                        new BigDecimal("17.5"),
                        new BigDecimal("-0.5"),
                        new BigDecimal("15.8"),
                        new BigDecimal("0.08")));
    }

    @Test
    void zScoreUsesLogFormulaWhenLZero() {
        // ln(60/50) / 0.04 ≈ 4.5580
        assertEquals(
                new BigDecimal("4.5580"),
                LmsCalculator.calculateZScore(
                        new BigDecimal("60"),
                        BigDecimal.ZERO,
                        new BigDecimal("50"),
                        new BigDecimal("0.04")));
    }

    @Test
    void percentileMatchesStandardNormalTable() {
        assertWithin(new BigDecimal("84.1345"), percentile("1"), 0.001);
        assertWithin(new BigDecimal("97.5002"), percentile("1.96"), 0.001);
        assertWithin(new BigDecimal("2.4998"), percentile("-1.96"), 0.001);
        assertWithin(new BigDecimal("99.8650"), percentile("3"), 0.001);
    }

    @Test
    void rejectsInvalidInputs() {
        BigDecimal value = new BigDecimal("50");
        BigDecimal l = BigDecimal.ONE;
        BigDecimal m = new BigDecimal("50");
        BigDecimal s = new BigDecimal("0.04");

        assertThrows(
                IllegalArgumentException.class,
                () -> LmsCalculator.calculateZScore(null, l, m, s));
        assertThrows(
                IllegalArgumentException.class,
                () -> LmsCalculator.calculateZScore(BigDecimal.ZERO, l, m, s));
        assertThrows(
                IllegalArgumentException.class,
                () -> LmsCalculator.calculateZScore(new BigDecimal("-1"), l, m, s));
        assertThrows(
                IllegalArgumentException.class,
                () -> LmsCalculator.calculateZScore(value, null, m, s));
        assertThrows(
                IllegalArgumentException.class,
                () -> LmsCalculator.calculateZScore(value, l, BigDecimal.ZERO, s));
        assertThrows(
                IllegalArgumentException.class,
                () -> LmsCalculator.calculateZScore(value, l, m, BigDecimal.ZERO));
        assertThrows(
                IllegalArgumentException.class, () -> LmsCalculator.calculatePercentile(null));
    }

    private static BigDecimal percentile(String z) {
        return LmsCalculator.calculatePercentile(new BigDecimal(z));
    }

    private static void assertWithin(BigDecimal expected, BigDecimal actual, double tolerance) {
        assertTrue(
                expected.subtract(actual).abs().compareTo(BigDecimal.valueOf(tolerance)) <= 0,
                () -> "expected " + expected + " ± " + tolerance + " but was " + actual);
    }
}

