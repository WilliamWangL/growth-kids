package com.growthkids.api.growth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.growthkids.api.children.entity.Child;
import com.growthkids.api.children.mapper.ChildMapper;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.growth.dto.CreateGrowthRecordRequest;
import com.growthkids.api.growth.dto.GrowthRecordResponse;
import com.growthkids.api.growth.dto.UpdateGrowthRecordRequest;
import com.growthkids.api.growth.entity.GrowthRecord;
import com.growthkids.api.growth.mapper.GrowthRecordMapper;
import com.growthkids.api.who.service.WhoMetricService;
import com.growthkids.api.who.service.WhoMetrics;
import com.growthkids.engine.age.AgeCalculator;
import com.growthkids.engine.bmi.BmiCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成长记录服务（TASK-007 / TASK-009，规范 §29-§32/§34/§35/§70）：
 * 写入时自动派生 age_days、BMI（growth-engine）与 WHO z-score/percentile（WhoMetricService）；
 * 持有者校验经 child 归属（§70）。
 */
@Service
public class GrowthRecordService {

    /** 测量值合理区间（cm/kg）：覆盖 0-18 岁并拦截明显异常值。 */
    private static final BigDecimal MIN_HEIGHT_CM = new BigDecimal("20");
    private static final BigDecimal MAX_HEIGHT_CM = new BigDecimal("250");
    private static final BigDecimal MIN_WEIGHT_KG = new BigDecimal("0.3");
    private static final BigDecimal MAX_WEIGHT_KG = new BigDecimal("300");
    private static final BigDecimal MIN_HEAD_CM = new BigDecimal("20");
    private static final BigDecimal MAX_HEAD_CM = new BigDecimal("70");

    private final GrowthRecordMapper growthRecordMapper;
    private final ChildMapper childMapper;
    private final WhoMetricService whoMetricService;

    public GrowthRecordService(
            GrowthRecordMapper growthRecordMapper,
            ChildMapper childMapper,
            WhoMetricService whoMetricService) {
        this.growthRecordMapper = growthRecordMapper;
        this.childMapper = childMapper;
        this.whoMetricService = whoMetricService;
    }

    @Transactional
    public GrowthRecordResponse create(long userId, long childId, CreateGrowthRecordRequest request) {
        Child child = requireOwnedChild(userId, childId);
        validateRecordDate(child.getBirthDate(), request.recordDate());
        validateMeasurements(request.heightCm(), request.weightKg(), request.headCircumferenceCm());

        GrowthRecord record = new GrowthRecord();
        record.setChildId(childId);
        record.setRecordDate(request.recordDate());
        record.setAgeDays(ageDays(child.getBirthDate(), request.recordDate()));
        record.setHeightCm(request.heightCm());
        record.setWeightKg(request.weightKg());
        record.setHeadCircumferenceCm(request.headCircumferenceCm());
        record.setBmi(bmiOrNull(request.heightCm(), request.weightKg()));
        record.setNote(normalizeOptional(request.note()));
        // 规范 §29：查询 WHO 数据计算 z-score/percentile 并回填 who_version（§34/§35）
        fillWhoMetrics(record, child);

        try {
            growthRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new ApiException(ApiErrorCode.GROWTH_RECORD_DUPLICATE_DATE);
        }
        return GrowthRecordResponse.from(growthRecordMapper.selectById(record.getId()), child.getBirthDate());
    }

    public PageResponse<GrowthRecordResponse> list(
            long userId, long childId, PageQuery query, LocalDate startDate, LocalDate endDate) {
        Child child = requireOwnedChild(userId, childId);
        Page<GrowthRecord> page = new Page<>(query.page(), query.pageSize());
        growthRecordMapper.selectPage(
                page,
                new LambdaQueryWrapper<GrowthRecord>()
                        .eq(GrowthRecord::getChildId, childId)
                        .ge(startDate != null, GrowthRecord::getRecordDate, startDate)
                        .le(endDate != null, GrowthRecord::getRecordDate, endDate)
                        .orderByDesc(GrowthRecord::getRecordDate)
                        .orderByDesc(GrowthRecord::getId));
        List<GrowthRecordResponse> items =
                page.getRecords().stream()
                        .map(record -> GrowthRecordResponse.from(record, child.getBirthDate()))
                        .toList();
        return new PageResponse<>(
                items,
                new PageResponse.Pagination(
                        page.getCurrent(), page.getSize(), page.getTotal(), page.getPages()));
    }

    /** 最近一次成长记录（TASK-011 Dashboard，规范 §112）；无记录时返回 null。 */
    public GrowthRecordResponse getLatest(long userId, long childId) {
        Child child = requireOwnedChild(userId, childId);
        GrowthRecord record =
                growthRecordMapper.selectOne(
                        new LambdaQueryWrapper<GrowthRecord>()
                                .eq(GrowthRecord::getChildId, childId)
                                .orderByDesc(GrowthRecord::getRecordDate)
                                .orderByDesc(GrowthRecord::getId)
                                .last("LIMIT 1"));
        return record == null ? null : GrowthRecordResponse.from(record, child.getBirthDate());
    }

    @Transactional
    public GrowthRecordResponse update(long userId, long recordId, UpdateGrowthRecordRequest request) {
        GrowthRecord record = requireOwned(userId, recordId);
        Child child = childMapper.selectById(record.getChildId());

        LocalDate recordDate = request.recordDate() != null ? request.recordDate() : record.getRecordDate();
        BigDecimal heightCm = request.heightCm() != null ? request.heightCm() : record.getHeightCm();
        BigDecimal weightKg = request.weightKg() != null ? request.weightKg() : record.getWeightKg();
        BigDecimal headCm =
                request.headCircumferenceCm() != null
                        ? request.headCircumferenceCm()
                        : record.getHeadCircumferenceCm();

        validateRecordDate(child.getBirthDate(), recordDate);
        validateMeasurements(heightCm, weightKg, headCm);

        LambdaUpdateWrapper<GrowthRecord> update =
                new LambdaUpdateWrapper<GrowthRecord>().eq(GrowthRecord::getId, recordId);
        boolean changed = false;
        int ageDays = record.getAgeDays();
        boolean dateChanged = !Objects.equals(recordDate, record.getRecordDate());
        if (dateChanged) {
            ageDays = ageDays(child.getBirthDate(), recordDate);
            update.set(GrowthRecord::getRecordDate, recordDate);
            update.set(GrowthRecord::getAgeDays, ageDays);
            changed = true;
        }
        if (request.heightCm() != null) {
            update.set(GrowthRecord::getHeightCm, request.heightCm());
            changed = true;
        }
        if (request.weightKg() != null) {
            update.set(GrowthRecord::getWeightKg, request.weightKg());
            changed = true;
        }
        if (request.headCircumferenceCm() != null) {
            update.set(GrowthRecord::getHeadCircumferenceCm, request.headCircumferenceCm());
            changed = true;
        }
        if (request.note() != null) {
            update.set(GrowthRecord::getNote, normalizeOptional(request.note()));
            changed = true;
        }
        BigDecimal bmi = record.getBmi();
        boolean derivedInputsChanged =
                dateChanged || request.heightCm() != null || request.weightKg() != null;
        if (derivedInputsChanged) {
            // 规范 §31：修改后必须重新计算 age / BMI
            bmi = bmiOrNull(heightCm, weightKg);
            update.set(GrowthRecord::getBmi, bmi);
            changed = true;
        }
        if (derivedInputsChanged || request.headCircumferenceCm() != null) {
            // 规范 §31：年龄或测量值变化后重算 WHO z-score/percentile（§34/§35）
            WhoMetrics metrics =
                    whoMetricService.computeFor(
                            child.getGender(), ageDays, heightCm, weightKg, bmi, headCm);
            applyWhoMetrics(update, metrics);
            changed = true;
        }
        if (changed) {
            try {
                growthRecordMapper.update(null, update);
            } catch (DuplicateKeyException e) {
                throw new ApiException(ApiErrorCode.GROWTH_RECORD_DUPLICATE_DATE);
            }
        }
        return GrowthRecordResponse.from(growthRecordMapper.selectById(recordId), child.getBirthDate());
    }

    @Transactional
    public void delete(long userId, long recordId) {
        requireOwned(userId, recordId);
        growthRecordMapper.deleteById(recordId);
    }

    /**
     * 孩子出生日期变化时重算全部记录的年龄（规范 §37）；由 ChildService 在事务内调用。
     *
     * <p>若存在早于新生日的记录则拒绝修改（否则年龄为负）。
     */
    @Transactional
    public void onChildBirthDateChanged(long childId, LocalDate newBirthDate) {
        List<GrowthRecord> records =
                growthRecordMapper.selectList(
                        new LambdaQueryWrapper<GrowthRecord>().eq(GrowthRecord::getChildId, childId));
        for (GrowthRecord record : records) {
            if (record.getRecordDate().isBefore(newBirthDate)) {
                throw new ApiException(
                        ApiErrorCode.CHILD_INVALID_DATA,
                        "Birth date must not be after existing growth record dates",
                        Map.of("birthDate", "must not be after existing growth record dates"));
            }
        }
        if (records.isEmpty()) {
            return;
        }
        growthRecordMapper.recomputeAgeDays(childId, newBirthDate);
        Child child = childMapper.selectById(childId);
        if (child == null) {
            // FK 保证 child 存在；极端情况下仅保留年龄重算结果
            return;
        }
        // 规范 §31/§34：年龄变化后按新 age_days 重算 WHO z-score/percentile
        for (GrowthRecord record : records) {
            int newAgeDays = ageDays(newBirthDate, record.getRecordDate());
            WhoMetrics metrics =
                    whoMetricService.computeFor(
                            child.getGender(),
                            newAgeDays,
                            record.getHeightCm(),
                            record.getWeightKg(),
                            record.getBmi(),
                            record.getHeadCircumferenceCm());
            LambdaUpdateWrapper<GrowthRecord> update =
                    new LambdaUpdateWrapper<GrowthRecord>().eq(GrowthRecord::getId, record.getId());
            applyWhoMetrics(update, metrics);
            growthRecordMapper.update(null, update);
        }
    }

    /** ownership 校验（规范 §70）：child 不属于当前用户时按不存在处理。 */
    private Child requireOwnedChild(long userId, long childId) {
        Child child = childMapper.selectById(childId);
        if (child == null || child.getUserId() == null || child.getUserId().longValue() != userId) {
            throw new ApiException(ApiErrorCode.CHILD_NOT_FOUND);
        }
        return child;
    }

    /** 记录级 ownership 校验：经 child 归属判断；非本人一律 GROWTH_RECORD_NOT_FOUND（不泄露存在性）。 */
    private GrowthRecord requireOwned(long userId, long recordId) {
        GrowthRecord record = growthRecordMapper.selectById(recordId);
        if (record == null) {
            throw new ApiException(ApiErrorCode.GROWTH_RECORD_NOT_FOUND);
        }
        Child child = childMapper.selectById(record.getChildId());
        if (child == null || child.getUserId() == null || child.getUserId().longValue() != userId) {
            throw new ApiException(ApiErrorCode.GROWTH_RECORD_NOT_FOUND);
        }
        return record;
    }

    private static int ageDays(LocalDate birthDate, LocalDate recordDate) {
        return AgeCalculator.calculate(birthDate, recordDate).totalDays();
    }

    private static BigDecimal bmiOrNull(BigDecimal heightCm, BigDecimal weightKg) {
        if (heightCm == null || weightKg == null) {
            return null;
        }
        return BmiCalculator.calculate(heightCm, weightKg);
    }

    /** 计算并回填单条记录的 WHO 指标（create 路径）。 */
    private void fillWhoMetrics(GrowthRecord record, Child child) {
        WhoMetrics metrics =
                whoMetricService.computeFor(
                        child.getGender(),
                        record.getAgeDays(),
                        record.getHeightCm(),
                        record.getWeightKg(),
                        record.getBmi(),
                        record.getHeadCircumferenceCm());
        record.setHeightZ(metrics.heightZ());
        record.setHeightPercentile(metrics.heightPercentile());
        record.setWeightZ(metrics.weightZ());
        record.setWeightPercentile(metrics.weightPercentile());
        record.setBmiZ(metrics.bmiZ());
        record.setBmiPercentile(metrics.bmiPercentile());
        record.setHeadZ(metrics.headZ());
        record.setHeadPercentile(metrics.headPercentile());
        record.setWhoVersion(metrics.whoVersion());
    }

    /** 将 WHO 指标写入更新语句（null 同样写入，用于清除已失效的旧计算结果）。 */
    private static void applyWhoMetrics(
            LambdaUpdateWrapper<GrowthRecord> update, WhoMetrics metrics) {
        update.set(GrowthRecord::getHeightZ, metrics.heightZ());
        update.set(GrowthRecord::getHeightPercentile, metrics.heightPercentile());
        update.set(GrowthRecord::getWeightZ, metrics.weightZ());
        update.set(GrowthRecord::getWeightPercentile, metrics.weightPercentile());
        update.set(GrowthRecord::getBmiZ, metrics.bmiZ());
        update.set(GrowthRecord::getBmiPercentile, metrics.bmiPercentile());
        update.set(GrowthRecord::getHeadZ, metrics.headZ());
        update.set(GrowthRecord::getHeadPercentile, metrics.headPercentile());
        update.set(GrowthRecord::getWhoVersion, metrics.whoVersion());
    }

    private static void validateRecordDate(LocalDate birthDate, LocalDate recordDate) {
        if (recordDate.isBefore(birthDate)) {
            throw new ApiException(
                    ApiErrorCode.GROWTH_RECORD_INVALID_DATA,
                    "Record date must not be before the child's birth date",
                    Map.of("recordDate", "must not be before birth date"));
        }
        if (recordDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new ApiException(
                    ApiErrorCode.GROWTH_RECORD_INVALID_DATA,
                    "Record date must not be in the future",
                    Map.of("recordDate", "must not be in the future"));
        }
    }

    private static void validateMeasurements(
            BigDecimal heightCm, BigDecimal weightKg, BigDecimal headCm) {
        if (heightCm == null && weightKg == null && headCm == null) {
            throw new ApiException(
                    ApiErrorCode.GROWTH_RECORD_INVALID_DATA,
                    "At least one measurement is required",
                    Map.of(
                            "measurements",
                            "at least one of heightCm, weightKg, headCircumferenceCm is required"));
        }
        if (heightCm != null && outOfRange(heightCm, MIN_HEIGHT_CM, MAX_HEIGHT_CM)) {
            throw new ApiException(
                    ApiErrorCode.GROWTH_RECORD_INVALID_HEIGHT,
                    "Height must be between 20 and 250 cm",
                    Map.of("heightCm", "must be between 20 and 250"));
        }
        if (weightKg != null && outOfRange(weightKg, MIN_WEIGHT_KG, MAX_WEIGHT_KG)) {
            throw new ApiException(
                    ApiErrorCode.GROWTH_RECORD_INVALID_WEIGHT,
                    "Weight must be between 0.3 and 300 kg",
                    Map.of("weightKg", "must be between 0.3 and 300"));
        }
        if (headCm != null && outOfRange(headCm, MIN_HEAD_CM, MAX_HEAD_CM)) {
            throw new ApiException(
                    ApiErrorCode.GROWTH_RECORD_INVALID_DATA,
                    "Head circumference must be between 20 and 70 cm",
                    Map.of("headCircumferenceCm", "must be between 20 and 70"));
        }
    }

    private static boolean outOfRange(BigDecimal value, BigDecimal min, BigDecimal max) {
        return value.compareTo(min) < 0 || value.compareTo(max) > 0;
    }

    private static String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
