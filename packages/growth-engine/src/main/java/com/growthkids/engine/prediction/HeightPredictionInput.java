package com.growthkids.engine.prediction;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 身高预测输入（规范 §39，两项扩展）：
 *
 * <ul>
 *   <li>asOfDate：计算基准日（引擎不做"今天"假设，保证可测试/可重放；服务端传入请求日）
 *   <li>currentHeightZ：当前身高 WHO z-score（可选）。百分位依赖 WHO 数据（存库），
 *       由 API 层用 WHO 成长标准算出后传入；无 WHO 数据时为 null
 * </ul>
 */
public record HeightPredictionInput(
        String gender,
        LocalDate birthDate,
        LocalDate asOfDate,
        BigDecimal currentHeightCm,
        BigDecimal currentHeightZ,
        List<HeightMeasurement> measurements,
        BigDecimal fatherHeightCm,
        BigDecimal motherHeightCm) {}

