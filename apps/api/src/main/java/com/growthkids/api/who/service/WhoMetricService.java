package com.growthkids.api.who.service;

import com.growthkids.api.who.entity.WhoGrowthStandard;
import com.growthkids.api.who.mapper.WhoGrowthStandardMapper;
import com.growthkids.engine.who.LmsCalculator;
import com.growthkids.engine.who.WhoIndicator;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

/**
 * WHO 指标计算服务（TASK-009，规范 §34/§35）。
 *
 * <p>匹配规则：精确匹配 sex 与 indicator，按 age_days 取不晚于该年龄的最近锚点（§35-4），
 * 不做线性插值（§35-5）；各指标独立查询，对应数据缺失时该指标保持 null。
 * 数据集取最近一次导入的 source/version，计算成功时回填 who_version（§124）。
 */
@Service
public class WhoMetricService {

    private final WhoGrowthStandardMapper whoGrowthStandardMapper;

    public WhoMetricService(WhoGrowthStandardMapper whoGrowthStandardMapper) {
        this.whoGrowthStandardMapper = whoGrowthStandardMapper;
    }

    /**
     * 计算一条成长记录的 WHO 指标。
     *
     * @param gender child 性别（male/female/unknown）
     * @param ageDays 记录时的年龄天数（存库的 age_days）
     */
    public WhoMetrics computeFor(
            String gender,
            int ageDays,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal bmi,
            BigDecimal headCircumferenceCm) {
        String sex = whoSex(gender);
        if (sex == null) {
            // 规范 §35-1：必须精确匹配 sex，unknown 不参与匹配
            return WhoMetrics.none();
        }
        WhoGrowthStandard dataset = whoGrowthStandardMapper.findLatestImported();
        if (dataset == null) {
            return WhoMetrics.none();
        }
        String source = dataset.getSource();
        String version = dataset.getVersion();

        BigDecimal[] height =
                zAndPercentile(
                        source, version, sex, WhoIndicator.HEIGHT_FOR_AGE, ageDays, heightCm);
        BigDecimal[] weight =
                zAndPercentile(
                        source, version, sex, WhoIndicator.WEIGHT_FOR_AGE, ageDays, weightKg);
        BigDecimal[] bmiPair =
                zAndPercentile(source, version, sex, WhoIndicator.BMI_FOR_AGE, ageDays, bmi);
        BigDecimal[] head =
                zAndPercentile(
                        source,
                        version,
                        sex,
                        WhoIndicator.HEAD_CIRCUMFERENCE_FOR_AGE,
                        ageDays,
                        headCircumferenceCm);

        String whoVersion =
                height == null && weight == null && bmiPair == null && head == null ? null : version;
        return new WhoMetrics(
                z(height),
                percentile(height),
                z(weight),
                percentile(weight),
                z(bmiPair),
                percentile(bmiPair),
                z(head),
                percentile(head),
                whoVersion);
    }

    /** 返回 [zScore, percentile]；无测量值或无锚点时为 null。 */
    private BigDecimal[] zAndPercentile(
            String source,
            String version,
            String sex,
            WhoIndicator indicator,
            int ageDays,
            BigDecimal value) {
        if (value == null) {
            return null;
        }
        WhoGrowthStandard anchor =
                whoGrowthStandardMapper.findLatestUpTo(
                        source, version, sex, indicator.code(), ageDays);
        if (anchor == null) {
            return null;
        }
        BigDecimal z =
                LmsCalculator.calculateZScore(
                        value, anchor.getLValue(), anchor.getMValue(), anchor.getSValue());
        return new BigDecimal[] {z, LmsCalculator.calculatePercentile(z)};
    }

    /** gender → WHO sex；unknown/缺失无法匹配（§35-1）时返回 null。 */
    private static String whoSex(String gender) {
        return switch (gender == null ? "" : gender) {
            case "male" -> "male";
            case "female" -> "female";
            default -> null;
        };
    }

    private static BigDecimal z(BigDecimal[] pair) {
        return pair == null ? null : pair[0];
    }

    private static BigDecimal percentile(BigDecimal[] pair) {
        return pair == null ? null : pair[1];
    }
}

