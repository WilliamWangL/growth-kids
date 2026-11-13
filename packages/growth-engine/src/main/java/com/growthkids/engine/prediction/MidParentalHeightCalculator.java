package com.growthkids.engine.prediction;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 中靶身高（规范 §41）：
 *
 * <ul>
 *   <li>男孩：(father + mother + 13) / 2
 *   <li>女孩：(father + mother − 13) / 2
 * </ul>
 *
 * <p>结果只能作为一个输入因素，不能直接当成预测结果（§41）；结果保留 1 位小数。
 */
public final class MidParentalHeightCalculator {

    /** 性别修正量（cm）。 */
    private static final BigDecimal SEX_ADJUSTMENT = new BigDecimal("13");

    /** 结果小数位数。 */
    public static final int SCALE = 1;

    private MidParentalHeightCalculator() {
        // 工具类禁止实例化
    }

    /**
     * 计算中靶身高；父母身高任一缺失时返回 null（该因子不参与加权）。
     *
     * @throws IllegalArgumentException gender 非 male/female
     */
    public static BigDecimal calculate(
            String gender, BigDecimal fatherHeightCm, BigDecimal motherHeightCm) {
        if (fatherHeightCm == null || motherHeightCm == null) {
            return null;
        }
        BigDecimal sum = fatherHeightCm.add(motherHeightCm);
        BigDecimal adjusted =
                switch (gender == null ? "" : gender) {
                    case "male" -> sum.add(SEX_ADJUSTMENT);
                    case "female" -> sum.subtract(SEX_ADJUSTMENT);
                    default -> throw new IllegalArgumentException("gender must be male or female");
                };
        return adjusted.divide(BigDecimal.TWO, SCALE, RoundingMode.HALF_UP);
    }
}

