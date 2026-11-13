package com.growthkids.api.report.service;

import com.growthkids.api.report.service.ReportDataBuilder.AnnualReportData;
import com.growthkids.api.report.service.ReportDataBuilder.MemoryLine;
import com.growthkids.api.report.service.ReportDataBuilder.PhotoLine;
import com.growthkids.api.report.service.ReportDataBuilder.RecordLine;
import com.growthkids.api.report.service.ReportDataBuilder.Snapshot;
import com.growthkids.engine.prediction.HeightPredictionResult;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

/**
 * 年度报告 HTML 构建（TASK-018，§98/§99）：纯模板拼接（无模板引擎依赖），
 * 交给 ReportPdfRenderer 渲染为 PDF；openhtmltopdf 要求严格 XHTML（标签必须闭合）。
 */
@Component
public class ReportHtmlBuilder {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public String build(AnnualReportData data) {
        StringBuilder html = new StringBuilder(16 * 1024);
        html.append("<!DOCTYPE html>\n<html lang=\"zh-CN\">\n<head>\n");
        html.append("<meta charset=\"UTF-8\"/>\n<title>")
                .append(escape(data.child().name()))
                .append(" 成长年度报告 ")
                .append(data.year())
                .append("</title>\n");
        html.append("<style>\n");
        html.append("body { font-family: 'Noto Sans SC'; font-size: 11pt; color: #1e293b; }");
        html.append("h1 { font-size: 20pt; color: #4338ca; margin: 0 0 2pt 0; }");
        html.append("h2 { font-size: 13pt; color: #4338ca; margin: 14pt 0 6pt 0; border-bottom: 1pt solid #c7d2fe; padding-bottom: 2pt; }");
        html.append(".sub { color: #64748b; font-size: 9pt; margin: 0 0 12pt 0; }");
        html.append("table { width: 100%; border-collapse: collapse; font-size: 9.5pt; }");
        html.append("th, td { border: 0.5pt solid #cbd5e1; padding: 4pt 6pt; text-align: left; }");
        html.append("th { background: #eef2ff; color: #3730a3; }");
        html.append(".cards td { text-align: center; }");
        html.append(".value { font-size: 13pt; font-weight: bold; color: #0f172a; }");
        html.append(".muted { color: #64748b; font-size: 8.5pt; }");
        html.append(".photo { width: 31%; margin: 0 1%; text-align: center; }");
        html.append(".photo img { width: 100%; }");
        html.append(".memory { margin: 0 0 8pt 0; }");
        html.append(".memory .title { font-weight: bold; }");
        html.append(".summary { background: #eef2ff; border-radius: 6pt; padding: 8pt 10pt; }");
        html.append("</style>\n</head>\n<body>\n");

        appendHeader(html, data);
        appendOverview(html, data);
        appendRecords(html, data);
        appendPhotos(html, data);
        appendMemories(html, data);
        appendPrediction(html, data);
        appendSummary(html, data);

        html.append("\n</body>\n</html>");
        return html.toString();
    }

    private void appendHeader(StringBuilder html, AnnualReportData data) {
        String gender = "female".equals(data.child().gender()) ? "女" : "male".equals(data.child().gender()) ? "男" : "—";
        html.append("<h1>")
                .append(escape(data.child().name()))
                .append(" 的成长年度报告</h1>");
        html.append("<p class=\"sub\">")
                .append(data.year())
                .append(" 年 · ")
                .append(gender)
                .append(" · 出生于 ")
                .append(data.child().birthDate().format(DATE))
                .append(" · 年末年龄 ")
                .append(data.ageAtYearEnd().years())
                .append(" 岁 ")
                .append(data.ageAtYearEnd().months())
                .append(" 个月</p>\n");
    }

    private void appendOverview(StringBuilder html, AnnualReportData data) {
        Snapshot first = data.first();
        Snapshot last = data.last();
        html.append("<h2>年度概览</h2>\n");
        html.append("<table class=\"cards\"><tr>");
        appendMetric(html, "年初身高", heightText(first), "cm");
        appendMetric(html, "年末身高", heightText(last), "cm");
        appendMetric(
                html,
                "身高增长",
                first != null && last != null && first.heightCm() != null && last.heightCm() != null
                        ? last.heightCm().subtract(first.heightCm()).stripTrailingZeros().toPlainString()
                        : null,
                "cm");
        appendMetric(html, "年初体重", first == null ? null : plain(first.weightKg()), "kg");
        appendMetric(html, "年末体重", last == null ? null : plain(last.weightKg()), "kg");
        appendMetric(html, "头围变化", headDelta(first, last), "cm");
        appendMetric(
                html,
                "身高百分位",
                first != null && last != null
                                && first.heightPercentile() != null
                                && last.heightPercentile() != null
                        ? "P" + plain(first.heightPercentile()) + " → P" + plain(last.heightPercentile())
                        : null,
                "");
        html.append("</tr></table>\n");
    }

    private void appendMetric(StringBuilder html, String label, String value, String unit) {
        html.append("<td><div class=\"muted\">")
                .append(label)
                .append("</div><div class=\"value\">")
                .append(value == null ? "—" : escape(value))
                .append(unit.isEmpty() ? "" : " " + unit)
                .append("</div></td>");
    }

    private void appendRecords(StringBuilder html, AnnualReportData data) {
        html.append("<h2>成长记录（共 ").append(data.records().size()).append(" 次）</h2>\n");
        if (data.records().isEmpty()) {
            html.append("<p class=\"muted\">本年度暂无记录</p>\n");
            return;
        }
        html.append("<table><tr><th>日期</th><th>身高(cm)</th><th>体重(kg)</th><th>头围(cm)</th><th>身高百分位</th><th>备注</th></tr>\n");
        for (RecordLine line : data.records()) {
            html.append("<tr><td>").append(line.date().format(DATE))
                    .append("</td><td>").append(plain(line.heightCm()))
                    .append("</td><td>").append(plain(line.weightKg()))
                    .append("</td><td>").append(plain(line.headCircumferenceCm()))
                    .append("</td><td>").append(line.heightPercentile() == null ? "—" : "P" + plain(line.heightPercentile()))
                    .append("</td><td>").append(line.note() == null ? "" : escape(line.note()))
                    .append("</td></tr>\n");
        }
        html.append("</table>\n");
    }

    private void appendPhotos(StringBuilder html, AnnualReportData data) {
        html.append("<h2>成长瞬间（共 ").append(data.photoCount()).append(" 张，展示最近 ")
                .append(data.photos().size()).append(" 张）</h2>\n");
        if (data.photos().isEmpty()) {
            html.append("<p class=\"muted\">暂无照片</p>\n");
            return;
        }
        html.append("<table class=\"cards\"><tr>");
        for (PhotoLine photo : data.photos()) {
            html.append("<td class=\"photo\">");
            if (photo.dataUri() != null) {
                html.append("<img src=\"").append(photo.dataUri()).append("\"/>");
            }
            html.append("<div class=\"muted\">")
                    .append(photo.date() == null ? "" : photo.date().format(DATE))
                    .append(photo.caption() == null ? "" : " " + escape(photo.caption()))
                    .append("</div></td>");
        }
        html.append("</tr></table>\n");
    }

    private void appendMemories(StringBuilder html, AnnualReportData data) {
        html.append("<h2>成长故事</h2>\n");
        if (data.memories().isEmpty()) {
            html.append("<p class=\"muted\">本年度暂无故事</p>\n");
            return;
        }
        for (MemoryLine memory : data.memories()) {
            html.append("<div class=\"memory\"><span class=\"title\">")
                    .append(memory.date().format(DATE))
                    .append(" · ")
                    .append(escape(memory.title() == null ? "无标题" : memory.title()))
                    .append("</span><br/>")
                    .append(escape(memory.content()))
                    .append("</div>\n");
        }
    }

    private void appendPrediction(StringBuilder html, AnnualReportData data) {
        html.append("<h2>身高预测</h2>\n");
        HeightPredictionResult prediction = data.prediction();
        if (prediction == null || prediction.predictedAdultHeightCm() == null) {
            html.append("<p class=\"muted\">测量数据不足，暂无法预测（§43：预测仅供参考，不构成医学建议）</p>\n");
            return;
        }
        html.append("<table><tr><th>当前身高</th><th>生长速度(cm/年)</th><th>中靶身高(cm)</th><th>预测成年身高(cm)</th><th>预测区间(cm)</th><th>置信度</th></tr>\n");
        html.append("<tr><td>").append(plain(prediction.currentHeightCm()))
                .append("</td><td>").append(plain(prediction.growthVelocityCmPerYear()))
                .append("</td><td>").append(plain(prediction.midParentalHeightCm()))
                .append("</td><td>").append(plain(prediction.predictedAdultHeightCm()))
                .append("</td><td>")
                .append(prediction.lowerRangeCm() == null || prediction.upperRangeCm() == null
                        ? "—"
                        : plain(prediction.lowerRangeCm()) + " – " + plain(prediction.upperRangeCm()))
                .append("</td><td>")
                .append(prediction.confidence() == null ? "—" : escape(prediction.confidence()))
                .append("</td></tr></table>\n");
        html.append("<p class=\"muted\">预测基于 WHO 标准与遗传模型，仅供参考，不构成医学建议（§43）</p>\n");
    }

    private void appendSummary(StringBuilder html, AnnualReportData data) {
        html.append("<h2>年度总结</h2>\n");
        html.append("<div class=\"summary\">").append(escape(data.summary())).append("</div>\n");
        html.append("<p class=\"muted\" style=\"margin-top:18pt\">由 GrowthKids 生成 · ")
                .append(LocalDate.now().format(DATE))
                .append("</p>\n");
    }

    private String heightText(Snapshot snapshot) {
        return snapshot == null ? null : plain(snapshot.heightCm());
    }

    private String headDelta(Snapshot first, Snapshot last) {
        if (first == null
                || last == null
                || first.headCircumferenceCm() == null
                || last.headCircumferenceCm() == null) {
            return null;
        }
        return last.headCircumferenceCm()
                .subtract(first.headCircumferenceCm())
                .stripTrailingZeros()
                .toPlainString();
    }

    private String plain(BigDecimal value) {
        return value == null ? "—" : value.stripTrailingZeros().toPlainString();
    }

    private String escape(String text) {
        if (text == null) {
            return "";
        }
        return text
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
