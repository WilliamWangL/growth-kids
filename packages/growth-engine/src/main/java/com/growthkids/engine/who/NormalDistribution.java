package com.growthkids.engine.who;

/**
 * 标准正态分布工具（TASK-009，规范 §34）：用于把 Z-score 转为百分位 P = Φ(Z)。
 *
 * <p>erf 采用 Abramowitz-Stegun 7.1.26 有理逼近（绝对误差 &lt; 1.5e-7），
 * 对百分位（×100、保留 4 位小数）而言精度绰绰有余。
 */
public final class NormalDistribution {

    /** √2。 */
    private static final double SQRT_TWO = Math.sqrt(2.0);

    // A&S 7.1.26 逼近系数
    private static final double P = 0.3275911;
    private static final double A1 = 0.254829592;
    private static final double A2 = -0.284496736;
    private static final double A3 = 1.421413741;
    private static final double A4 = -1.453152027;
    private static final double A5 = 1.061405429;

    private NormalDistribution() {
        // 工具类禁止实例化
    }

    /** 标准正态分布累积分布函数 Φ(z)，返回值 ∈ (0, 1)。 */
    public static double cdf(double z) {
        return 0.5 * (1.0 + erf(z / SQRT_TWO));
    }

    /** erf(x) 近似（A&S 7.1.26，Horner 形式）。 */
    static double erf(double x) {
        double sign = x >= 0 ? 1.0 : -1.0;
        double absX = Math.abs(x);
        double t = 1.0 / (1.0 + P * absX);
        double poly = ((((A5 * t + A4) * t + A3) * t + A2) * t + A1) * t;
        return sign * (1.0 - poly * Math.exp(-absX * absX));
    }
}

