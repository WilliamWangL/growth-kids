package com.growthkids.api.who.importer;

import com.growthkids.engine.who.WhoIndicator;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * WHO LMS CSV 解析器（TASK-008，规范 §35-7）。
 *
 * <p>文件格式（UTF-8）：
 *
 * <pre>
 * # source: who-official
 * # version: 2006
 * sex,indicator,age_days,l_value,m_value,s_value
 * male,height_for_age,0,1.0,49.9,0.04
 * </pre>
 *
 * <p>解析阶段仅做格式级校验（列数/枚举/数值）；数据级校验（重复/递增/完整性）
 * 由 {@link WhoDataValidator} 在全量读取后执行。任何错误立即抛 {@link WhoDataException}，
 * 不产生部分导入。
 */
@Component
public class WhoCsvParser {

    private static final String SOURCE_PREFIX = "# source:";
    private static final String VERSION_PREFIX = "# version:";
    private static final String HEADER = "sex,indicator,age_days,l_value,m_value,s_value";
    private static final String SEX_MALE = "male";
    private static final String SEX_FEMALE = "female";

    /** 解析数据流为完整数据集；调用方负责关闭流。 */
    public WhoDataSet parse(InputStream in) {
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String source = null;
            String version = null;
            boolean headerSeen = false;
            List<ParsedLmsRow> rows = new ArrayList<>();
            String line;
            int lineNumber = 0;
            while ((line = reader.readLine()) != null) {
                lineNumber++;
                line = stripBom(line).trim();
                if (line.isEmpty()) {
                    continue;
                }
                if (line.startsWith("#")) {
                    if (line.startsWith(SOURCE_PREFIX)) {
                        source = line.substring(SOURCE_PREFIX.length()).trim();
                    } else if (line.startsWith(VERSION_PREFIX)) {
                        version = line.substring(VERSION_PREFIX.length()).trim();
                    }
                    continue;
                }
                if (!headerSeen) {
                    if (!HEADER.equals(line)) {
                        throw fail(lineNumber, "unexpected header, expected: " + HEADER);
                    }
                    headerSeen = true;
                    continue;
                }
                rows.add(parseRow(line, lineNumber));
            }
            if (source == null || source.isEmpty()) {
                throw new WhoDataException("missing '# source:' metadata line");
            }
            if (version == null || version.isEmpty()) {
                throw new WhoDataException("missing '# version:' metadata line");
            }
            if (!headerSeen) {
                throw new WhoDataException("missing column header row: " + HEADER);
            }
            return new WhoDataSet(source, version, rows);
        } catch (IOException e) {
            throw new WhoDataException("failed to read WHO data stream: " + e.getMessage(), e);
        }
    }

    private static ParsedLmsRow parseRow(String line, int lineNumber) {
        String[] fields = line.split(",", -1);
        if (fields.length != 6) {
            throw fail(lineNumber, "expected 6 columns, got " + fields.length);
        }
        String sex = fields[0].trim();
        if (!SEX_MALE.equals(sex) && !SEX_FEMALE.equals(sex)) {
            throw fail(lineNumber, "sex must be 'male' or 'female', got '" + sex + "'");
        }
        String indicatorCode = fields[1].trim();
        WhoIndicator indicator =
                WhoIndicator.fromCode(indicatorCode)
                        .orElseThrow(
                                () -> fail(lineNumber, "unknown indicator '" + indicatorCode + "'"));
        int ageDays = parseInt(fields[2].trim(), lineNumber, "age_days");
        if (ageDays < 0) {
            throw fail(lineNumber, "age_days must be >= 0, got " + ageDays);
        }
        BigDecimal l = parseDecimal(fields[3].trim(), lineNumber, "l_value");
        BigDecimal m = parseDecimal(fields[4].trim(), lineNumber, "m_value");
        BigDecimal s = parseDecimal(fields[5].trim(), lineNumber, "s_value");
        return new ParsedLmsRow(sex, indicator, ageDays, l, m, s);
    }

    private static int parseInt(String value, int lineNumber, String field) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            throw fail(lineNumber, field + " must be an integer, got '" + value + "'");
        }
    }

    private static BigDecimal parseDecimal(String value, int lineNumber, String field) {
        try {
            return new BigDecimal(value);
        } catch (NumberFormatException e) {
            throw fail(lineNumber, field + " must be a decimal number, got '" + value + "'");
        }
    }

    private static WhoDataException fail(int lineNumber, String message) {
        return new WhoDataException("WHO data parse error at line " + lineNumber + ": " + message);
    }

    private static String stripBom(String line) {
        return !line.isEmpty() && line.charAt(0) == '\uFEFF' ? line.substring(1) : line;
    }
}
