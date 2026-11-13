package com.growthkids.api.who.importer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * WHO 数据导入前校验（TASK-008，规范 §35-7）。
 *
 * <p>在 parser 格式校验之上执行数据级检查，聚合全部错误后一次性报告（便于修复数据文件）：
 * 重复行、分组内 age_days 严格递增、M/S 必须为正、age_days 上限。
 */
@Component
public class WhoDataValidator {

    /** 19 岁 ≈ 6939 天；留余量做上限拦截。 */
    static final int MAX_AGE_DAYS = 7000;

    /** 单次报告的错误上限（避免超大文件刷屏）。 */
    private static final int MAX_REPORTED_ERRORS = 10;

    public void validate(WhoDataSet dataSet) {
        List<String> errors = new ArrayList<>();
        if (dataSet.rows().isEmpty()) {
            errors.add("no data rows");
        }
        Set<String> seenKeys = new HashSet<>();
        Map<String, Integer> lastAgeByGroup = new HashMap<>();
        int index = 0;
        for (ParsedLmsRow row : dataSet.rows()) {
            index++;
            String group = row.sex() + "|" + row.indicator().code();
            String key = group + "|" + row.ageDays();
            if (!seenKeys.add(key)) {
                errors.add("row " + index + ": duplicate " + key);
            }
            Integer previousAge = lastAgeByGroup.get(group);
            if (previousAge != null && row.ageDays() <= previousAge) {
                errors.add(
                        "row "
                                + index
                                + ": age_days not strictly increasing in group "
                                + group
                                + " ("
                                + row.ageDays()
                                + " after "
                                + previousAge
                                + ")");
            }
            lastAgeByGroup.put(group, row.ageDays());
            if (row.m().signum() <= 0) {
                errors.add("row " + index + ": m_value must be > 0, got " + row.m());
            }
            if (row.s().signum() <= 0) {
                errors.add("row " + index + ": s_value must be > 0, got " + row.s());
            }
            if (row.ageDays() > MAX_AGE_DAYS) {
                errors.add("row " + index + ": age_days exceeds " + MAX_AGE_DAYS);
            }
        }
        if (!errors.isEmpty()) {
            throw new WhoDataException(
                    "WHO data validation failed with "
                            + errors.size()
                            + " error(s): "
                            + errors.subList(0, Math.min(errors.size(), MAX_REPORTED_ERRORS)));
        }
    }
}
