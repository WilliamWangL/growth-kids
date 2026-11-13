package com.growthkids.engine.age;

import java.time.LocalDate;
import java.time.Period;
import java.time.temporal.ChronoUnit;

/**
 * 年龄计算（规范 §37）：必须基于实际日期计算，禁止使用年份相减。
 *
 * <p>输出拆分值（years/months/days）与总量（totalMonths/totalDays）。
 */
public final class AgeCalculator {

    private AgeCalculator() {
        // 工具类禁止实例化
    }

    /**
     * 计算 recordDate 时点的年龄。
     *
     * @param birthDate 出生日期
     * @param recordDate 记录日期（不得早于出生日期）
     * @throws IllegalArgumentException 参数为空或 recordDate 早于 birthDate
     */
    public static AgeResult calculate(LocalDate birthDate, LocalDate recordDate) {
        if (birthDate == null || recordDate == null) {
            throw new IllegalArgumentException("birthDate and recordDate must not be null");
        }
        if (recordDate.isBefore(birthDate)) {
            throw new IllegalArgumentException("recordDate must not be before birthDate");
        }
        Period period = Period.between(birthDate, recordDate);
        int totalMonths = (int) ChronoUnit.MONTHS.between(birthDate, recordDate);
        int totalDays = (int) ChronoUnit.DAYS.between(birthDate, recordDate);
        return new AgeResult(
                period.getYears(), period.getMonths(), period.getDays(), totalMonths, totalDays);
    }
}
