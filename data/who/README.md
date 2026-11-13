# WHO 生长标准数据（data/who）

> 规范要求：**必须使用官方来源数据**（AI_DEVELOPMENT_SPEC §35-6、§135 TASK-008）；
> 原始 WHO 数据不可被业务代码修改（§11/§31）。

## 当前数据状态

仓库内置的运行时数据文件为：

```text
apps/api/src/main/resources/who-data/who-standards-example.csv
```

⚠️ 该文件是**合成示例数据**（`source=growthkids-example-synthetic`），由
`scripts/generate-example-who-data.py` 确定性生成，**不是 WHO 官方标准**。
它仅用于开发/测试跑通导入管线、z-score/percentile 计算与图表链路。

**生产部署前必须用官方数据文件替换**（流程见下文）。

## 官方数据来源

| 年龄段 | 数据集 | 官方获取入口 |
|---|---|---|
| 0–5 岁 | WHO Child Growth Standards (2006) | https://www.who.int/tools/child-growth-standards/standards（各指标页 Expanded tables，xlsx） |
| 5–19 岁 | WHO Growth Reference (2007) | https://www.who.int/tools/growth-reference-data-for-5to19-years |
| 辅助 | CDC 托管的 WHO LMS CSV（0–24 月，按月） | https://www.cdc.gov/growthcharts/who-data-files.htm |
| 辅助 | WHO Anthro / igrowup R macro（含 txt 表） | WHO 官网软件下载页 |

## 目标 CSV 格式

导入器接受的核心列（UTF-8；`#` 开头为注释；前两行元数据必需）：

```csv
# source: who-official
# version: 2006
sex,indicator,age_days,l_value,m_value,s_value
male,height_for_age,0,1.0,49.8842,0.03795
```

- `sex`：`male` | `female`（精确匹配，§35-1）
- `indicator`：`height_for_age` | `weight_for_age` | `bmi_for_age` |
  `head_circumference_for_age` | `weight_for_height`（§17）
- `age_days`：≥ 0 整数；同分组内严格递增
- `l_value` / `m_value` / `s_value`：十进制；M、S 必须 > 0

## 替换步骤（生产）

1. 从上述官方入口下载对应指标的 LMS 表（xlsx/csv/txt）。
2. 转换为目标 CSV 格式：
   - 0–5 岁按日（0–1856 天）或按月（0–60 月，`age_days = round(30.4375 × month)`）；
   - 5–19 岁按月（61–228 月）；
   - `source` 建议固定为 `who-official`，`version` 使用数据集标识（如 `2006` / `2007`）。
3. 将文件放入 `apps/api/src/main/resources/who-data/`（打包内置），或放服务器磁盘后配置：

   ```yaml
   app:
     who:
       data-file: file:/data/growthkids/who/who-standards-official.csv
   ```

4. 重启应用：启动时自动导入（幂等；同 source+version 已存在则跳过）。
   数据更新时 **bump version**（旧版本数据保留，便于审计与回滚）。
5. 导入后校验由导入器自动执行（格式 + 数据级）；失败会阻止启动并输出错误行号。

## 运行时行为（规范 §35）

- 查询按 `source + version + sex + indicator + age_days` 精确匹配；
- 数据粒度为离散锚点时，取**不晚于 age_days 的最近锚点**（官方允许的区间处理，§35-4），
  不允许随意线性插值（§35-5）；
- 每条成长记录保存当时使用的 `who_version`（growth_records.who_version，§124）；
- WHO 版本升级后可执行指标重算（§124 recalculate growth metrics）。

