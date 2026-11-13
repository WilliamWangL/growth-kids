package com.growthkids.engine.prediction;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 单次身高测量（规范 §39 measurements 元素）。 */
public record HeightMeasurement(LocalDate date, BigDecimal heightCm) {}

