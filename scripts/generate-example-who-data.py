#!/usr/bin/env python3
"""生成 WHO 示例 LMS 数据（**合成数据，非官方标准**，仅开发/演示用）。

输出：apps/api/src/main/resources/who-data/who-standards-example.csv

背景：规范（AI_DEVELOPMENT_SPEC §31/§35）要求生产环境使用 WHO 官方数据。
本脚本以确定性公式生成形态合理的合成 LMS 数据，仅用于：
  1) 跑通导入管线（parser / validation / seed / version）；
  2) 支撑 z-score / percentile / 图表链路的开发与测试。
生产环境替换为官方数据文件的流程见 data/who/README.md。

数据布局：0-60 月，每 3 个月一个锚点；4 个指标 × 2 个性别。
"""
import math
import os

MAX_MONTH = 60
STEP_MONTHS = 3
DAYS_PER_MONTH = 30.4375
OUTPUT = os.path.join(
    os.path.dirname(__file__),
    "..",
    "apps/api/src/main/resources/who-data/who-standards-example.csv",
)


def height_m(sex, month):
    return 49.9 + 60.1 * (1 - math.exp(-month / 16)) if sex == "male" \
        else 49.1 + 58.9 * (1 - math.exp(-month / 16))


def weight_m(sex, month):
    return 3.3 + 15.2 * (1 - math.exp(-month / 18)) if sex == "male" \
        else 3.2 + 14.6 * (1 - math.exp(-month / 18))


def bmi_m(sex, month):
    return 13.4 + 3.4 * math.exp(-month / 5) if sex == "male" \
        else 13.3 + 3.3 * math.exp(-month / 5)


def head_m(sex, month):
    return 34.5 + 12.5 * (1 - math.exp(-month / 12)) if sex == "male" \
        else 33.9 + 12.2 * (1 - math.exp(-month / 12))


# indicator -> (M 函数, L, S)
INDICATORS = {
    "height_for_age": (height_m, 1.0, 0.04),
    "weight_for_age": (weight_m, 0.5, 0.11),
    "bmi_for_age": (bmi_m, -0.5, 0.08),
    "head_circumference_for_age": (head_m, 1.0, 0.04),
}


def main():
    months = list(range(0, MAX_MONTH + 1, STEP_MONTHS))
    lines = [
        "# source: growthkids-example-synthetic",
        "# version: 2026.1-example",
        "# 注意：合成示例数据，非 WHO 官方标准；生产替换流程见 data/who/README.md",
        "sex,indicator,age_days,l_value,m_value,s_value",
    ]
    for sex in ("male", "female"):
        for indicator, (m_func, l_value, s_value) in INDICATORS.items():
            for month in months:
                age_days = round(DAYS_PER_MONTH * month)
                m_value = round(m_func(sex, month), 4)
                lines.append(
                    f"{sex},{indicator},{age_days},{l_value},{m_value},{s_value}"
                )
    content = "\n".join(lines) + "\n"
    output = os.path.normpath(OUTPUT)
    os.makedirs(os.path.dirname(output), exist_ok=True)
    with open(output, "w", encoding="utf-8") as handle:
        handle.write(content)
    print(f"wrote {len(lines) - 4} rows to {output}")


if __name__ == "__main__":
    main()
