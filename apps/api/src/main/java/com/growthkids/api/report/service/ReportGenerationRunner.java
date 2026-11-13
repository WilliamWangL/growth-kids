package com.growthkids.api.report.service;

import com.growthkids.api.report.entity.GrowthReport;
import com.growthkids.api.report.mapper.GrowthReportMapper;
import com.growthkids.api.photo.storage.LocalStorageService;
import java.io.ByteArrayInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步报告生成（TASK-018，§99 report-generation 异步）：
 * pending→generating→ready/failed；失败不抛出（状态机落库），与缩略图异步同模式。
 */
@Component
public class ReportGenerationRunner {

    private static final Logger log = LoggerFactory.getLogger(ReportGenerationRunner.class);

    private final GrowthReportMapper reportMapper;

    private final ReportDataBuilder dataBuilder;

    private final ReportHtmlBuilder htmlBuilder;

    private final ReportPdfRenderer pdfRenderer;

    private final LocalStorageService storageService;

    public ReportGenerationRunner(
            GrowthReportMapper reportMapper,
            ReportDataBuilder dataBuilder,
            ReportHtmlBuilder htmlBuilder,
            ReportPdfRenderer pdfRenderer,
            LocalStorageService storageService) {
        this.reportMapper = reportMapper;
        this.dataBuilder = dataBuilder;
        this.htmlBuilder = htmlBuilder;
        this.pdfRenderer = pdfRenderer;
        this.storageService = storageService;
    }

    @Async
    public void generate(long reportId, String storageKey) {
        GrowthReport report = reportMapper.selectById(reportId);
        if (report == null) {
            return;
        }
        try {
            updateStatus(report, GrowthReport.STATUS_GENERATING, null);
            ReportDataBuilder.AnnualReportData data =
                    dataBuilder.build(report.getUserId(), report.getChildId(), report.getYear());
            String html = htmlBuilder.build(data);
            byte[] pdf = pdfRenderer.render(html);
            storageService.write(storageKey, new ByteArrayInputStream(pdf));
            updateStatus(report, GrowthReport.STATUS_READY, storageKey);
        } catch (Exception e) {
            log.warn("Report generation failed id={} year={}", reportId, report.getYear(), e);
            updateStatus(report, GrowthReport.STATUS_FAILED, null);
        }
    }

    private void updateStatus(GrowthReport report, String status, String storageKey) {
        report.setStatus(status);
        report.setStorageKey(storageKey);
        reportMapper.updateById(report);
    }
}
