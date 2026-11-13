package com.growthkids.api.who.importer;

import com.growthkids.engine.who.WhoIndicator;
import java.math.BigDecimal;

/** CSV 解析出的单行 LMS 数据（导入前形态，规范 §17）。 */
public record ParsedLmsRow(
        String sex, WhoIndicator indicator, int ageDays, BigDecimal l, BigDecimal m, BigDecimal s) {}
