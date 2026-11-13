package com.growthkids.engine.who;

import java.math.BigDecimal;

/**
 * WHO LMS 参考值（规范 §34）：L=Box-Cox 幂，M=中位数，S=变异系数。
 *
 * <p>Z = ((X/M)^L - 1) / (L×S)，L=0 时 Z = ln(X/M) / S。
 */
public record LmsReference(BigDecimal l, BigDecimal m, BigDecimal s) {}
