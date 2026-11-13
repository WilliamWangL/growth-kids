package com.growthkids.api.growth.dto;

import com.growthkids.api.growth.entity.GrowthRecord;
import com.growthkids.engine.age.AgeCalculator;
import com.growthkids.engine.age.AgeResult;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** 成长记录响应（规范 §29-§32）。id/childId 序列化为字符串。 */
public record GrowthRecordResponse(
        String id,
        String childId,
        LocalDate recordDate,
        Age age,
        BigDecimal heightCm,
        BigDecimal weightKg,
        BigDecimal headCircumferenceCm,
        BigDecimal bmi,
        BigDecimal heightZ,
        BigDecimal heightPercentile,
        BigDecimal weightZ,
        BigDecimal weightPercentile,
        BigDecimal bmiZ,
        BigDecimal bmiPercentile,
        BigDecimal headZ,
        BigDecimal headPercentile,
        String whoVersion,
        String note,
        Instant createdAt,
        Instant updatedAt) {

    /** 年龄（规范 §37）：拆分显示值 + 总量（totalDays 即库中 age_days）。 */
    public record Age(int years, int months, int days, int totalMonths, int totalDays) {}

    public static GrowthRecordResponse from(GrowthRecord record, LocalDate birthDate) {
        AgeResult age = AgeCalculator.calculate(birthDate, record.getRecordDate());
        return new GrowthRecordResponse(
                String.valueOf(record.getId()),
                String.valueOf(record.getChildId()),
                record.getRecordDate(),
                new Age(age.years(), age.months(), age.days(), age.totalMonths(), age.totalDays()),
                record.getHeightCm(),
                record.getWeightKg(),
                record.getHeadCircumferenceCm(),
                record.getBmi(),
                record.getHeightZ(),
                record.getHeightPercentile(),
                record.getWeightZ(),
                record.getWeightPercentile(),
                record.getBmiZ(),
                record.getBmiPercentile(),
                record.getHeadZ(),
                record.getHeadPercentile(),
                record.getWhoVersion(),
                record.getNote(),
                toInstant(record.getCreatedAt()),
                toInstant(record.getUpdatedAt()));
    }

    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC);
    }
}
