package com.growthkids.engine.bmi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** BMI 计算单测（TASK-007，规范 §36）。 */
class BmiCalculatorTest {

    @Test
    void computesSpecExample() {
        // 规范 §36 示例：82cm / 11.7kg → 11.7 / 0.82² = 17.400（3 位小数）
        BigDecimal bmi = BmiCalculator.calculate(new BigDecimal("82"), new BigDecimal("11.7"));
        assertEquals(new BigDecimal("17.400"), bmi);
    }

    @Test
    void keepsThreeDecimalScaleForExactValues() {
        BigDecimal bmi = BmiCalculator.calculate(new BigDecimal("100"), new BigDecimal("16"));
        assertEquals(new BigDecimal("16.000"), bmi);
        assertEquals(BmiCalculator.SCALE, bmi.scale());
    }

    @Test
    void roundsHalfUp() {
        // 0.5kg / 1.12m² = 0.398596... → 0.399
        BigDecimal bmi = BmiCalculator.calculate(new BigDecimal("112"), new BigDecimal("0.5"));
        assertEquals(new BigDecimal("0.399"), bmi);
    }

    @Test
    void rejectsNonPositiveValues() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BmiCalculator.calculate(BigDecimal.ZERO, new BigDecimal("10")));
        assertThrows(
                IllegalArgumentException.class,
                () -> BmiCalculator.calculate(new BigDecimal("80"), BigDecimal.ZERO));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BmiCalculator.calculate(null, new BigDecimal("10")));
        assertThrows(
                IllegalArgumentException.class,
                () -> BmiCalculator.calculate(new BigDecimal("80"), null));
    }
}
