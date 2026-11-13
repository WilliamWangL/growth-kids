package com.growthkids.engine.prediction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 中靶身高单测（TASK-010，规范 §41）：
 *
 * <ul>
 *   <li>男孩：(father + mother + 13) / 2
 *   <li>女孩：(father + mother - 13) / 2
 *   <li>父母任一缺失 → null（该因子不参与加权，§41：只能作为输入因素）
 * </ul>
 */
class MidParentalHeightCalculatorTest {

    @Test
    void boyAddsThirteenThenHalves() {
        // (180 + 165 + 13) / 2 = 179.0
        assertEquals(
                new BigDecimal("179.0"),
                MidParentalHeightCalculator.calculate(
                        "male", new BigDecimal("180"), new BigDecimal("165")));
    }

    @Test
    void girlSubtractsThirteenThenHalves() {
        // (172 + 158 - 13) / 2 = 158.5
        assertEquals(
                new BigDecimal("158.5"),
                MidParentalHeightCalculator.calculate(
                        "female", new BigDecimal("172"), new BigDecimal("158")));
    }

    @Test
    void roundsToSingleDecimal() {
        // (178.4 + 164.7 + 13) / 2 = 178.05 → 178.1（HALF_UP）
        assertEquals(
                new BigDecimal("178.1"),
                MidParentalHeightCalculator.calculate(
                        "male", new BigDecimal("178.4"), new BigDecimal("164.7")));
    }

    @Test
    void returnsNullWhenEitherParentMissing() {
        assertNull(MidParentalHeightCalculator.calculate("male", null, new BigDecimal("165")));
        assertNull(MidParentalHeightCalculator.calculate("male", new BigDecimal("180"), null));
        assertNull(MidParentalHeightCalculator.calculate("female", null, null));
    }

    @Test
    void rejectsUnknownGenderWhenBothParentsPresent() {
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MidParentalHeightCalculator.calculate(
                                "unknown", new BigDecimal("180"), new BigDecimal("165")));
        assertThrows(
                IllegalArgumentException.class,
                () ->
                        MidParentalHeightCalculator.calculate(
                                null, new BigDecimal("180"), new BigDecimal("165")));
    }
}
