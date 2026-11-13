package com.growthkids.engine.age;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.LocalDate;
import org.junit.jupiter.api.Test;

/** 年龄计算单测（TASK-007，规范 §37）。 */
class AgeCalculatorTest {

    @Test
    void splitsAgeAcrossYearMonthDayBoundaries() {
        AgeResult age = AgeCalculator.calculate(LocalDate.of(2025, 1, 1), LocalDate.of(2026, 9, 15));
        assertEquals(1, age.years());
        assertEquals(8, age.months());
        assertEquals(14, age.days());
        assertEquals(20, age.totalMonths());
        assertEquals(622, age.totalDays());
    }

    @Test
    void sameDayIsAllZero() {
        AgeResult age = AgeCalculator.calculate(LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 15));
        assertEquals(0, age.years());
        assertEquals(0, age.months());
        assertEquals(0, age.days());
        assertEquals(0, age.totalMonths());
        assertEquals(0, age.totalDays());
    }

    @Test
    void handlesLeapDayBirth() {
        // 2024-02-29 → 2025-03-01：1 岁 0 月 1 天（共 366 天）
        AgeResult age = AgeCalculator.calculate(LocalDate.of(2024, 2, 29), LocalDate.of(2025, 3, 1));
        assertEquals(1, age.years());
        assertEquals(0, age.months());
        assertEquals(1, age.days());
        assertEquals(12, age.totalMonths());
        assertEquals(366, age.totalDays());
    }

    @Test
    void monthBoundaryUsesCalendarMonths() {
        // 2026-01-31 → 2026-03-01：0 岁 1 月 1 天（周期按日历推算）
        AgeResult age = AgeCalculator.calculate(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 3, 1));
        assertEquals(0, age.years());
        assertEquals(1, age.months());
        assertEquals(1, age.days());
        assertEquals(29, age.totalDays());
    }

    @Test
    void rejectsRecordDateBeforeBirthDate() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AgeCalculator.calculate(LocalDate.of(2026, 1, 1), LocalDate.of(2025, 12, 31)));
    }

    @Test
    void rejectsNullArguments() {
        assertThrows(
                IllegalArgumentException.class,
                () -> AgeCalculator.calculate(null, LocalDate.of(2026, 1, 1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> AgeCalculator.calculate(LocalDate.of(2026, 1, 1), null));
    }
}
