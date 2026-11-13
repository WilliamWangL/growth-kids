package com.growthkids.engine.prediction;

import com.growthkids.engine.who.LmsCalculator;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Period;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * 身高预测引擎 V1（TASK-010，规范 §38-§45）。
 *
 * <p>多因素组合（§42）：predicted = Σ wᵢ·Hᵢ / Σ wᵢ（权重集中在 {@link PredictionConfig}，
 * 不可用因子（无 z/无父母/无速度）按比例重分配权重）：
 *
 * <ul>
 *   <li>percentile：成年中位 + z×成年SD（追踪假设）；z 由调用方用 WHO 数据计算后传入
 *   <li>parental：中靶身高（§41）
 *   <li>velocity：年龄模型剩余增长 × clamp(实际速度/期望速度)，速度仅作为趋势修正
 *   <li>age_model：当前身高 / 成熟度系数
 * </ul>
 *
 * <p>predicted/range 保留 1 位小数；置信度规则见 §43。成熟度表与成年参照为公开中位数据的
 * 近似值，仅用于成长趋势参考（§19：不得表述为医学诊断或确定结果）。
 */
public final class HeightPredictionEngine {

    /** 年度预测起始年龄下限（规范 §44 页面展示 6-18 岁）。 */
    private static final int YEARLY_MIN_AGE = 6;

    /** 成年年龄（成熟度=100%）。 */
    private static final int ADULT_AGE = 18;

    /** 成熟度表（% 成年身高，按周岁；由公开中位身高近似，仅作估算）。 */
    private static final BigDecimal[] BOYS_MATURITY =
            toFractions(
                    "28.4", "43.0", "49.5", "54.6", "58.7", "62.5", "65.9", "69.2", "72.3",
                    "75.3", "78.3", "81.3", "84.7", "88.6", "92.7", "96.0", "98.2", "99.5",
                    "100.0");

    private static final BigDecimal[] GIRLS_MATURITY =
            toFractions(
                    "30.1", "45.4", "52.6", "58.3", "63.0", "67.1", "70.9", "74.3", "77.7",
                    "81.3", "85.0", "89.0", "92.8", "96.4", "98.4", "99.5", "100.0", "100.0",
                    "100.0");

    private final PredictionConfig config;

    public HeightPredictionEngine() {
        this(PredictionConfig.defaults());
    }

    public HeightPredictionEngine(PredictionConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("config must not be null");
        }
        this.config = config;
    }

    /**
     * 执行预测。
     *
     * @throws IllegalArgumentException 性别/日期/当前身高非法
     */
    public HeightPredictionResult predict(HeightPredictionInput input) {
        validate(input);
        boolean male = "male".equals(input.gender());
        int ageMonths = (int) ChronoUnit.MONTHS.between(input.birthDate(), input.asOfDate());
        int ageYears = Period.between(input.birthDate(), input.asOfDate()).getYears();
        BigDecimal adultMedian = male ? config.adultMedianMale() : config.adultMedianFemale();
        BigDecimal adultSd = male ? config.adultSdMale() : config.adultSdFemale();
        BigDecimal fNow = fraction(male, ageYears);
        BigDecimal current = input.currentHeightCm();

        // 各因子 → 成年身高估算（cm，scale 4）
        BigDecimal velocity = GrowthVelocityCalculator.calculate(input.measurements());
        BigDecimal z = clampedZ(input.currentHeightZ());
        BigDecimal percentileComponent =
                z == null
                        ? null
                        : adultMedian.add(z.multiply(adultSd)).setScale(4, RoundingMode.HALF_UP);
        BigDecimal parentalComponent =
                MidParentalHeightCalculator.calculate(
                        input.gender(), input.fatherHeightCm(), input.motherHeightCm());
        BigDecimal ageModelComponent = current.divide(fNow, 4, RoundingMode.HALF_UP);
        BigDecimal velocityComponent =
                velocityComponent(
                        male, ageYears, fNow, velocity, adultMedian, current, ageModelComponent);

        String confidence =
                confidence(input.measurements(), input.fatherHeightCm(), input.motherHeightCm());
        BigDecimal predicted =
                combine(percentileComponent, parentalComponent, velocityComponent, ageModelComponent);
        BigDecimal halfRange =
                adultSd.multiply(rangeFactor(confidence)).setScale(4, RoundingMode.HALF_UP);

        List<HeightPredictionResult.YearlyPrediction> yearly =
                yearlyPredictions(male, ageYears, fNow, current, predicted, halfRange);

        BigDecimal percentile = z == null ? null : LmsCalculator.calculatePercentile(z);
        return new HeightPredictionResult(
                ageMonths,
                current,
                percentile,
                velocity,
                parentalComponent,
                predicted.setScale(1, RoundingMode.HALF_UP),
                predicted.subtract(halfRange).setScale(1, RoundingMode.HALF_UP),
                predicted.add(halfRange).setScale(1, RoundingMode.HALF_UP),
                confidence,
                yearly);
    }

    /** velocity 因子：年龄模型剩余增长 × clamp(实际速度/期望速度)；速度缺失时返回 null。 */
    private BigDecimal velocityComponent(
            boolean male,
            int ageYears,
            BigDecimal fNow,
            BigDecimal velocity,
            BigDecimal adultMedian,
            BigDecimal current,
            BigDecimal ageModelComponent) {
        if (velocity == null || fNow.signum() <= 0) {
            return null;
        }
        BigDecimal fNext = fraction(male, Math.min(ageYears + 1, ADULT_AGE));
        BigDecimal expectedVelocity = adultMedian.multiply(fNext.subtract(fNow));
        if (expectedVelocity.signum() <= 0) {
            return null;
        }
        BigDecimal ratio =
                velocity
                        .divide(expectedVelocity, 4, RoundingMode.HALF_UP)
                        .max(config.minVelocityRatio())
                        .min(config.maxVelocityRatio());
        return current
                .add(ageModelComponent.subtract(current).multiply(ratio))
                .setScale(4, RoundingMode.HALF_UP);
    }

    /** 加权组合（规范 §42）：仅统计权重 > 0 且可用的因子，缺失因子权重按比例重分配。 */
    private BigDecimal combine(
            BigDecimal percentileComponent,
            BigDecimal parentalComponent,
            BigDecimal velocityComponent,
            BigDecimal ageModelComponent) {
        BigDecimal[][] factors = {
            {config.weightPercentile(), percentileComponent},
            {config.weightParental(), parentalComponent},
            {config.weightVelocity(), velocityComponent},
            {config.weightAgeModel(), ageModelComponent},
        };
        BigDecimal totalWeight = BigDecimal.ZERO;
        BigDecimal weightedSum = BigDecimal.ZERO;
        for (BigDecimal[] factor : factors) {
            BigDecimal weight = factor[0];
            BigDecimal height = factor[1];
            if (height != null && weight != null && weight.signum() > 0) {
                totalWeight = totalWeight.add(weight);
                weightedSum = weightedSum.add(weight.multiply(height));
            }
        }
        if (totalWeight.signum() == 0) {
            // 极端配置兜底：年龄模型始终可用
            return ageModelComponent;
        }
        return weightedSum.divide(totalWeight, 4, RoundingMode.HALF_UP);
    }

    /** 置信度（规范 §43）：数据完整度，不是医学意义准确率。 */
    private String confidence(
            List<HeightMeasurement> measurements,
            BigDecimal fatherHeightCm,
            BigDecimal motherHeightCm) {
        List<HeightMeasurement> valid = GrowthVelocityCalculator.validSorted(measurements);
        if (valid.size() < 2) {
            return "low";
        }
        long spanDays =
                ChronoUnit.DAYS.between(valid.get(0).date(), valid.get(valid.size() - 1).date());
        if (spanDays < config.minMediumSpanDays()) {
            return "low";
        }
        boolean parentsPresent = fatherHeightCm != null && motherHeightCm != null;
        if (valid.size() >= config.minHighRecords()
                && spanDays >= config.minHighSpanDays()
                && parentsPresent) {
            return "high";
        }
        return "medium";
    }

    /** 预测区间半宽 = 成年 SD × 置信度系数。 */
    private BigDecimal rangeFactor(String confidence) {
        return switch (confidence) {
            case "high" -> config.rangeFactorHigh();
            case "medium" -> config.rangeFactorMedium();
            default -> config.rangeFactorLow();
        };
    }

    /** 年度预测（规范 §44）：按成熟度进度在 [当前身高, 预测成年身高] 间插值。 */
    private List<HeightPredictionResult.YearlyPrediction> yearlyPredictions(
            boolean male,
            int ageYears,
            BigDecimal fNow,
            BigDecimal current,
            BigDecimal predicted,
            BigDecimal halfRange) {
        List<HeightPredictionResult.YearlyPrediction> yearly = new ArrayList<>();
        BigDecimal remainingFraction = BigDecimal.ONE.subtract(fNow);
        if (remainingFraction.signum() <= 0) {
            return yearly;
        }
        for (int age = Math.max(YEARLY_MIN_AGE, ageYears + 1); age <= ADULT_AGE; age++) {
            BigDecimal progress =
                    fraction(male, age)
                            .subtract(fNow)
                            .divide(remainingFraction, 6, RoundingMode.HALF_UP)
                            .max(BigDecimal.ZERO)
                            .min(BigDecimal.ONE);
            BigDecimal height = current.add(predicted.subtract(current).multiply(progress));
            yearly.add(
                    new HeightPredictionResult.YearlyPrediction(
                            age,
                            height.setScale(1, RoundingMode.HALF_UP),
                            height.subtract(halfRange).setScale(1, RoundingMode.HALF_UP),
                            height.add(halfRange).setScale(1, RoundingMode.HALF_UP)));
        }
        return yearly;
    }

    private BigDecimal clampedZ(BigDecimal z) {
        if (z == null) {
            return null;
        }
        BigDecimal limit = config.maxAbsZ();
        return z.max(limit.negate()).min(limit);
    }

    private static BigDecimal fraction(boolean male, int ageYears) {
        int index = Math.max(0, Math.min(ageYears, ADULT_AGE));
        return male ? BOYS_MATURITY[index] : GIRLS_MATURITY[index];
    }

    private static BigDecimal[] toFractions(String... percentages) {
        BigDecimal[] table = new BigDecimal[percentages.length];
        for (int i = 0; i < percentages.length; i++) {
            table[i] = new BigDecimal(percentages[i]).movePointLeft(2);
        }
        return table;
    }

    private static void validate(HeightPredictionInput input) {
        if (input == null) {
            throw new IllegalArgumentException("input must not be null");
        }
        if (!"male".equals(input.gender()) && !"female".equals(input.gender())) {
            throw new IllegalArgumentException("gender must be male or female");
        }
        if (input.birthDate() == null || input.asOfDate() == null) {
            throw new IllegalArgumentException("birthDate and asOfDate must not be null");
        }
        if (input.asOfDate().isBefore(input.birthDate())) {
            throw new IllegalArgumentException("asOfDate must not be before birthDate");
        }
        if (input.currentHeightCm() == null || input.currentHeightCm().signum() <= 0) {
            throw new IllegalArgumentException("currentHeightCm must be positive");
        }
    }
}

