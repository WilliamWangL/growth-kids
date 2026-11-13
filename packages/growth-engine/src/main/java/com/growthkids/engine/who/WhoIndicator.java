package com.growthkids.engine.who;

import java.util.Optional;

/**
 * WHO 指标枚举（规范 §17）：与 who_growth_standards.indicator 列取值一致。
 *
 * <p>数据导入与运行时计算共用此枚举，保证两侧取值集合永不漂移。
 */
public enum WhoIndicator {
    HEIGHT_FOR_AGE("height_for_age"),
    WEIGHT_FOR_AGE("weight_for_age"),
    BMI_FOR_AGE("bmi_for_age"),
    HEAD_CIRCUMFERENCE_FOR_AGE("head_circumference_for_age"),
    WEIGHT_FOR_HEIGHT("weight_for_height");

    private final String code;

    WhoIndicator(String code) {
        this.code = code;
    }

    /** 数据库存储值（规范 §17）。 */
    public String code() {
        return code;
    }

    /** 由存储值反查枚举；未知值返回空。 */
    public static Optional<WhoIndicator> fromCode(String code) {
        for (WhoIndicator indicator : values()) {
            if (indicator.code.equals(code)) {
                return Optional.of(indicator);
            }
        }
        return Optional.empty();
    }
}
