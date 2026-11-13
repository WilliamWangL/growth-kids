package com.growthkids.api.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.growthkids.api.children.dto.ChildResponse;
import com.growthkids.api.children.service.ChildService;
import com.growthkids.api.growth.entity.GrowthRecord;
import com.growthkids.api.growth.mapper.GrowthRecordMapper;
import com.growthkids.api.memory.entity.GrowthMemory;
import com.growthkids.api.memory.mapper.GrowthMemoryMapper;
import com.growthkids.api.photo.entity.GrowthPhoto;
import com.growthkids.api.photo.mapper.GrowthPhotoMapper;
import com.growthkids.api.photo.storage.LocalStorageService;
import com.growthkids.engine.age.AgeCalculator;
import com.growthkids.engine.age.AgeResult;
import com.growthkids.engine.prediction.HeightMeasurement;
import com.growthkids.engine.prediction.HeightPredictionEngine;
import com.growthkids.engine.prediction.HeightPredictionInput;
import com.growthkids.engine.prediction.HeightPredictionResult;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 年度报告数据组装（TASK-018，规范 §98）：
 * 基本信息/年龄/年初年末快照/身高体重头围与百分位变化/成长照片/成长故事/身高预测/年度总结。
 */
@Component
public class ReportDataBuilder {

    private static final int MAX_MEMORIES = 5;

    private static final int MAX_PHOTOS = 6;

    private final GrowthRecordMapper recordMapper;

    private final GrowthMemoryMapper memoryMapper;

    private final GrowthPhotoMapper photoMapper;

    private final ChildService childService;

    private final LocalStorageService storageService;

    public ReportDataBuilder(
            GrowthRecordMapper recordMapper,
            GrowthMemoryMapper memoryMapper,
            GrowthPhotoMapper photoMapper,
            ChildService childService,
            LocalStorageService storageService) {
        this.recordMapper = recordMapper;
        this.memoryMapper = memoryMapper;
        this.photoMapper = photoMapper;
        this.childService = childService;
        this.storageService = storageService;
    }

    /** 年度报告数据（§98 十二项；快照缺失项为 null，展示层补 “—”）。 */
    public AnnualReportData build(long userId, long childId, int year) {
        ChildResponse child = childService.get(userId, childId);
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        LocalDate yearEnd = LocalDate.of(year, 12, 31);

        List<GrowthRecord> yearRecords = recordMapper
                .selectList(
                        new LambdaQueryWrapper<GrowthRecord>()
                                .eq(GrowthRecord::getChildId, childId)
                                .ge(GrowthRecord::getRecordDate, yearStart)
                                .le(GrowthRecord::getRecordDate, yearEnd)
                                .orderByAsc(GrowthRecord::getRecordDate))
                .stream()
                .toList();

        // 全部历史（含往年）供预测：升序身高序列 + 最新一条含 z 值记录
        List<GrowthRecord> allRecords = recordMapper
                .selectList(
                        new LambdaQueryWrapper<GrowthRecord>()
                                .eq(GrowthRecord::getChildId, childId)
                                .orderByAsc(GrowthRecord::getRecordDate))
                .stream()
                .toList();

        List<MemoryLine> memories = memoryMapper
                .selectList(
                        new LambdaQueryWrapper<GrowthMemory>()
                                .eq(GrowthMemory::getChildId, childId)
                                .ge(GrowthMemory::getMemoryDate, yearStart)
                                .le(GrowthMemory::getMemoryDate, yearEnd)
                                .orderByDesc(GrowthMemory::getMemoryDate)
                                .last("LIMIT " + MAX_MEMORIES))
                .stream()
                .map(memory -> new MemoryLine(
                        memory.getMemoryDate(), memory.getTitle(), memory.getContent()))
                .toList();

        int photoCount = Math.toIntExact(photoMapper.selectCount(
                new LambdaQueryWrapper<GrowthPhoto>().eq(GrowthPhoto::getChildId, childId)));
        List<PhotoLine> photos = photoMapper
                .selectList(
                        new LambdaQueryWrapper<GrowthPhoto>()
                                .eq(GrowthPhoto::getChildId, childId)
                                .orderByDesc(GrowthPhoto::getId)
                                .last("LIMIT " + MAX_PHOTOS))
                .stream()
                .map(this::toPhotoLine)
                .toList();

        GrowthRecord first = yearRecords.isEmpty() ? null : yearRecords.getFirst();
        GrowthRecord last = yearRecords.isEmpty() ? null : yearRecords.getLast();
        AgeResult age = AgeCalculator.calculate(child.birthDate(), yearEnd);
        HeightPredictionResult prediction = predict(child, allRecords, yearEnd);

        return new AnnualReportData(
                child,
                year,
                age,
                Snapshot.of(first),
                Snapshot.of(last),
                yearRecords.stream().map(RecordLine::of).toList(),
                memories,
                photoCount,
                photos,
                prediction,
                buildSummary(child, year, first, last, yearRecords.size(), photoCount, memories.size()));
    }

    private HeightPredictionResult predict(
            ChildResponse child, List<GrowthRecord> allRecords, LocalDate asOfDate) {
        List<GrowthRecord> withHeight =
                allRecords.stream().filter(r -> r.getHeightCm() != null).toList();
        if (withHeight.isEmpty()) {
            return null;
        }
        List<HeightMeasurement> measurements = withHeight.stream()
                .map(r -> new HeightMeasurement(r.getRecordDate(), r.getHeightCm()))
                .toList();
        GrowthRecord latest = withHeight.getLast();
        HeightPredictionInput input = new HeightPredictionInput(
                child.gender(),
                child.birthDate(),
                asOfDate,
                latest.getHeightCm(),
                latest.getHeightZ(),
                measurements,
                child.fatherHeightCm(),
                child.motherHeightCm());
        return new HeightPredictionEngine().predict(input);
    }

    /** 年度总结（规则模板，非 AI）：仅拼接有数据的句子。 */
    private String buildSummary(
            ChildResponse child,
            int year,
            GrowthRecord first,
            GrowthRecord last,
            int recordCount,
            int photoCount,
            int memoryCount) {
        StringBuilder summary = new StringBuilder();
        if (first != null && first.getHeightCm() != null && last.getHeightCm() != null) {
            BigDecimal delta = last.getHeightCm().subtract(first.getHeightCm());
            summary.append(child.name())
                    .append("在 ")
                    .append(year)
                    .append(" 年从 ")
                    .append(first.getHeightCm().stripTrailingZeros().toPlainString())
                    .append(" cm 长到 ")
                    .append(last.getHeightCm().stripTrailingZeros().toPlainString())
                    .append(" cm，全年长高 ")
                    .append(delta.setScale(1, RoundingMode.HALF_UP).toPlainString())
                    .append(" cm；");
        }
        if (first != null
                && first.getHeightPercentile() != null
                && last.getHeightPercentile() != null) {
            summary.append("身高百分位从 P")
                    .append(first.getHeightPercentile().stripTrailingZeros().toPlainString())
                    .append(" 到 P")
                    .append(last.getHeightPercentile().stripTrailingZeros().toPlainString())
                    .append("；");
        }
        summary.append("这一年共记录 ")
                .append(recordCount)
                .append(" 次成长测量、")
                .append(photoCount)
                .append(" 张照片和 ")
                .append(memoryCount)
                .append(" 个成长故事，感谢每一份用心记录。");
        return summary.toString();
    }

    private PhotoLine toPhotoLine(GrowthPhoto photo) {
        String dataUri = null;
        String key = photo.getThumbnailKey() != null ? photo.getThumbnailKey() : photo.getStorageKey();
        if (key != null && storageService.exists(key)) {
            try (InputStream in = storageService.open(key)) {
                String base64 = Base64.getEncoder().encodeToString(in.readAllBytes());
                String mime = photo.getThumbnailKey() != null ? "image/jpeg" : photo.getMimeType();
                dataUri = "data:" + mime + ";base64," + base64;
            } catch (Exception ignored) {
                // 单张照片读取失败不阻断报告生成
            }
        }
        LocalDate date = photo.getCreatedAt() != null ? photo.getCreatedAt().toLocalDate() : null;
        return new PhotoLine(date, photo.getCaption(), dataUri);
    }

    /** 年初/年末快照：取该年最早/最晚一条记录（§98）。 */
    public record Snapshot(
            LocalDate date,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal headCircumferenceCm,
            BigDecimal heightPercentile) {

        public static Snapshot of(GrowthRecord record) {
            if (record == null) {
                return null;
            }
            return new Snapshot(
                    record.getRecordDate(),
                    record.getHeightCm(),
                    record.getWeightKg(),
                    record.getHeadCircumferenceCm(),
                    record.getHeightPercentile());
        }
    }

    public record RecordLine(
            LocalDate date,
            BigDecimal heightCm,
            BigDecimal weightKg,
            BigDecimal headCircumferenceCm,
            BigDecimal heightPercentile,
            String note) {

        public static RecordLine of(GrowthRecord record) {
            return new RecordLine(
                    record.getRecordDate(),
                    record.getHeightCm(),
                    record.getWeightKg(),
                    record.getHeadCircumferenceCm(),
                    record.getHeightPercentile(),
                    record.getNote());
        }
    }

    public record MemoryLine(LocalDate date, String title, String content) {}

    public record PhotoLine(LocalDate date, String caption, String dataUri) {}

    public record AnnualReportData(
            ChildResponse child,
            int year,
            AgeResult ageAtYearEnd,
            Snapshot first,
            Snapshot last,
            List<RecordLine> records,
            List<MemoryLine> memories,
            int photoCount,
            List<PhotoLine> photos,
            HeightPredictionResult prediction,
            String summary) {}
}
