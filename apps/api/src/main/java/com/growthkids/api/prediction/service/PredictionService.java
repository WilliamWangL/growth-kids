package com.growthkids.api.prediction.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.growthkids.api.children.dto.ChildResponse;
import com.growthkids.api.children.service.ChildService;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.growth.entity.GrowthRecord;
import com.growthkids.api.growth.mapper.GrowthRecordMapper;
import com.growthkids.api.prediction.dto.PredictionRequest;
import com.growthkids.api.prediction.dto.PredictionResponse;
import com.growthkids.api.who.service.WhoMetricService;
import com.growthkids.api.who.service.WhoMetrics;
import com.growthkids.engine.age.AgeCalculator;
import com.growthkids.engine.prediction.HeightMeasurement;
import com.growthkids.engine.prediction.HeightPredictionEngine;
import com.growthkids.engine.prediction.HeightPredictionInput;
import com.growthkids.engine.prediction.HeightPredictionResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 身高预测服务（TASK-021，规范 §38-§45/§75）。
 *
 * <p>公开计算器（无需登录，§75）与登录后孩子档案预测（§44）共用同一引擎：
 * 百分位依赖 WHO 数据（WhoMetricService），引擎本身不访问数据库（§34 职责分离）。
 */
@Service
public class PredictionService {

    private final ChildService childService;
    private final GrowthRecordMapper growthRecordMapper;
    private final WhoMetricService whoMetricService;

    public PredictionService(
            ChildService childService,
            GrowthRecordMapper growthRecordMapper,
            WhoMetricService whoMetricService) {
        this.childService = childService;
        this.growthRecordMapper = growthRecordMapper;
        this.whoMetricService = whoMetricService;
    }

    /**
     * 公开身高计算器（§75）：仅一次当前身高测量 → 无生长速度因子，
     * 置信度按 §43 记为 low（页面需展示免责声明 §45）。
     */
    public PredictionResponse estimate(PredictionRequest request) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        validateBirthDate(request.birthDate(), today);
        HeightPredictionResult result =
                predict(
                        request.gender(),
                        request.birthDate(),
                        today,
                        request.heightCm(),
                        heightZ(request.gender(), request.birthDate(), today, request.heightCm()),
                        List.of(new HeightMeasurement(today, request.heightCm())),
                        request.fatherHeightCm(),
                        request.motherHeightCm());
        return PredictionResponse.from(result);
    }

    /** 登录后按孩子档案预测（§44）：使用全部身高记录估算生长速度与置信度。 */
    public PredictionResponse estimateForChild(long userId, long childId) {
        ChildResponse child = childService.get(userId, childId);
        if (!"male".equals(child.gender()) && !"female".equals(child.gender())) {
            throw new ApiException(
                    ApiErrorCode.PREDICTION_INSUFFICIENT_DATA,
                    "Child gender must be male or female to predict height",
                    Map.of("gender", "required"));
        }
        List<GrowthRecord> records =
                growthRecordMapper.selectList(
                        new LambdaQueryWrapper<GrowthRecord>()
                                .eq(GrowthRecord::getChildId, childId)
                                .isNotNull(GrowthRecord::getHeightCm)
                                .orderByAsc(GrowthRecord::getRecordDate)
                                .orderByAsc(GrowthRecord::getId));
        if (records.isEmpty()) {
            throw new ApiException(
                    ApiErrorCode.PREDICTION_INSUFFICIENT_DATA,
                    "At least one height record is required",
                    Map.of("childId", "no height record"));
        }
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        GrowthRecord latest = records.getLast();
        // 百分位按当前年龄重算；WHO 数据未覆盖该年龄时回落到记录时点存库的 z（§34）
        BigDecimal z = heightZ(child.gender(), child.birthDate(), today, latest.getHeightCm());
        List<HeightMeasurement> measurements =
                records.stream()
                        .map(record -> new HeightMeasurement(record.getRecordDate(), record.getHeightCm()))
                        .toList();
        HeightPredictionResult result =
                predict(
                        child.gender(),
                        child.birthDate(),
                        today,
                        latest.getHeightCm(),
                        z != null ? z : latest.getHeightZ(),
                        measurements,
                        child.fatherHeightCm(),
                        child.motherHeightCm());
        return PredictionResponse.from(result);
    }

    private static HeightPredictionResult predict(
            String gender,
            LocalDate birthDate,
            LocalDate asOfDate,
            BigDecimal currentHeightCm,
            BigDecimal currentHeightZ,
            List<HeightMeasurement> measurements,
            BigDecimal fatherHeightCm,
            BigDecimal motherHeightCm) {
        return new HeightPredictionEngine()
                .predict(
                        new HeightPredictionInput(
                                gender,
                                birthDate,
                                asOfDate,
                                currentHeightCm,
                                currentHeightZ,
                                measurements,
                                fatherHeightCm,
                                motherHeightCm));
    }

    /** 当前时点 WHO 身高 z-score（§34）；性别 unknown 或数据未覆盖该年龄时为 null。 */
    private BigDecimal heightZ(
            String gender, LocalDate birthDate, LocalDate asOfDate, BigDecimal heightCm) {
        int ageDays = AgeCalculator.calculate(birthDate, asOfDate).totalDays();
        WhoMetrics metrics = whoMetricService.computeFor(gender, ageDays, heightCm, null, null, null);
        return metrics.heightZ();
    }

    private static void validateBirthDate(LocalDate birthDate, LocalDate today) {
        if (birthDate.isAfter(today)) {
            throw new ApiException(
                    ApiErrorCode.VALIDATION_ERROR,
                    "Birth date must not be in the future",
                    Map.of("birthDate", "must not be in the future"));
        }
    }
}
