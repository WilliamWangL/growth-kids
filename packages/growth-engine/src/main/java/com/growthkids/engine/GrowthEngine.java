package com.growthkids.engine;

/**
 * Growth Engine 入口标记类。
 *
 * 计算能力分阶段实现：
 * - TASK-007：年龄计算（{@code age} 包，ageYears/ageMonths/ageDays/ageTotalDays）、
 *   BMI 计算（{@code bmi} 包）
 * - TASK-009：Z-score（LMS 公式，L=0 与 L!=0 分支）、Percentile（基于正态分布 CDF）
 * - TASK-010：身高预测（mid-parental height、growth velocity、多因素加权）
 *
 * 规范要求（AI_DEVELOPMENT_SPEC §18）：
 * 百分位和 Z-score 计算必须集中在本模块，业务代码不得散落实现。
 */
public final class GrowthEngine {

    /** 引擎版本号，随算法调整递增。 */
    public static final String VERSION = "1.0.0";

    private GrowthEngine() {
        // 工具类禁止实例化
    }
}
