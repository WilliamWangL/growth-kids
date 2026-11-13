package com.growthkids.engine.age;

/**
 * 年龄计算结果（规范 §37）。
 *
 * <p>years/months/days 为拆分显示值；totalMonths/totalDays 为总量，
 * 其中 totalDays 用于 WHO 标准数据按 age_days 匹配（§35）。
 */
public record AgeResult(int years, int months, int days, int totalMonths, int totalDays) {}
