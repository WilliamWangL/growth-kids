package com.growthkids.api.who.importer;

import java.util.List;

/** 单个 WHO 数据文件解析结果：source/version 元数据 + 全部行（规范 §35-6）。 */
public record WhoDataSet(String source, String version, List<ParsedLmsRow> rows) {

    /** 行数（含重复行，用于日志）。 */
    public int size() {
        return rows.size();
    }
}
