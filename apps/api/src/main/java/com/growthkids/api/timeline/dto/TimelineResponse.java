package com.growthkids.api.timeline.dto;

import com.growthkids.api.growth.entity.GrowthRecord;
import com.growthkids.api.memory.entity.GrowthMemory;
import com.growthkids.engine.age.AgeCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 合并时间线响应（TASK-014，规范 §52）。
 *
 * <p>单一 item 结构承载两种类型（Jackson non_null 省略空字段，与示例结构一致）：
 * growth_record（heightCm/weightKg/headCircumferenceCm/note/photos）与
 * memory（title/content/photos）；照片含短期签名 URL（§120 规则 29），
 * 月龄由 AgeCalculator 按实际日期计算（§37）。
 */
public record TimelineResponse(List<TimelineItem> items) {

    public record TimelineItem(
            String type,
            LocalDate date,
            Integer ageMonths,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal headCircumferenceCm,
            String note,
            List<TimelinePhoto> photos,
            String title,
            String content) {

        public record TimelinePhoto(long id, String thumbnailUrl, String viewUrl) {}

        public static TimelineItem fromRecord(
                GrowthRecord record, LocalDate birthDate, List<TimelinePhoto> photos) {
            return new TimelineItem(
                    "growth_record",
                    record.getRecordDate(),
                    ageMonths(birthDate, record.getRecordDate()),
                    record.getHeightCm(),
                    record.getWeightKg(),
                    record.getHeadCircumferenceCm(),
                    record.getNote(),
                    photos,
                    null,
                    null);
        }

        public static TimelineItem fromMemory(
                GrowthMemory memory, LocalDate birthDate, List<TimelinePhoto> photos) {
            return new TimelineItem(
                    "memory",
                    memory.getMemoryDate(),
                    ageMonths(birthDate, memory.getMemoryDate()),
                    null,
                    null,
                    null,
                    null,
                    photos,
                    memory.getTitle(),
                    memory.getContent());
        }

        private static int ageMonths(LocalDate birthDate, LocalDate date) {
            return AgeCalculator.calculate(birthDate, date).totalMonths();
        }
    }
}
