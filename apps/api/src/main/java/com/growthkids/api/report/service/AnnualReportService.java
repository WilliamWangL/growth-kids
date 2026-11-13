package com.growthkids.api.report.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.growth.entity.GrowthRecord;
import com.growthkids.api.growth.mapper.GrowthRecordMapper;
import com.growthkids.api.membership.service.PlanResolver;
import com.growthkids.api.children.service.ChildService;
import com.growthkids.api.photo.storage.LocalStorageService;
import com.growthkids.api.report.dto.CreateReportRequest;
import com.growthkids.api.report.dto.ReportResponse;
import com.growthkids.api.report.entity.GrowthReport;
import com.growthkids.api.report.mapper.GrowthReportMapper;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 年度报告服务（TASK-018，§98/§99/§110）：
 * Pro 权益（§64 annualReport/pdfExport）→ 创建/重生成都走异步生成，
 * uq_child_report 同年幂等（数据更新后可重出）。
 */
@Service
public class AnnualReportService {

    private final GrowthReportMapper reportMapper;

    private final GrowthRecordMapper recordMapper;

    private final ChildService childService;

    private final PlanResolver planResolver;

    private final LocalStorageService storageService;

    private final ReportGenerationRunner generationRunner;

    public AnnualReportService(
            GrowthReportMapper reportMapper,
            GrowthRecordMapper recordMapper,
            ChildService childService,
            PlanResolver planResolver,
            LocalStorageService storageService,
            ReportGenerationRunner generationRunner) {
        this.reportMapper = reportMapper;
        this.recordMapper = recordMapper;
        this.childService = childService;
        this.planResolver = planResolver;
        this.storageService = storageService;
        this.generationRunner = generationRunner;
    }

    /** 创建（或重生）年度报告：校验权限与数据，置 pending 并异步生成（§99）。 */
    @Transactional
    public ReportResponse create(long userId, long childId, CreateReportRequest request) {
        // §64：年度报告 + PDF 导出为 Pro 权益
        if (!planResolver.resolve(userId).permissions().annualReport()) {
            throw new ApiException(ApiErrorCode.MEMBERSHIP_REQUIRED);
        }
        childService.get(userId, childId);
        int year = request.year();
        long recordCount = recordMapper.selectCount(
                new LambdaQueryWrapper<GrowthRecord>()
                        .eq(GrowthRecord::getChildId, childId)
                        .ge(GrowthRecord::getRecordDate, LocalDate.of(year, 1, 1))
                        .le(GrowthRecord::getRecordDate, LocalDate.of(year, 12, 31)));
        if (recordCount == 0) {
            throw new ApiException(ApiErrorCode.REPORT_NO_DATA);
        }

        GrowthReport report = findReport(childId, year);
        if (report == null) {
            report = new GrowthReport();
            report.setChildId(childId);
            report.setUserId(userId);
            report.setReportType(GrowthReport.TYPE_ANNUAL);
            report.setYear(year);
            report.setStatus(GrowthReport.STATUS_PENDING);
            try {
                reportMapper.insert(report);
            } catch (DuplicateKeyException e) {
                // 并发同年重复创建：复用已有行
                report = findReport(childId, year);
            }
        } else {
            // 同年重生成：重置状态，数据更新后可重出（uq_child_report 幂等；
            // MyBatis Plus 忽略 null 字段更新，旧 storageKey 由生成成功时覆盖）
            report.setStatus(GrowthReport.STATUS_PENDING);
            reportMapper.updateById(report);
        }
        String storageKey = storageKey(report);
        long reportId = report.getId();
        // 事务提交后再触发异步生成：避免 Runner 新连接读不到未提交行
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                generationRunner.generate(reportId, storageKey);
            }
        });
        return ReportResponse.of(reportMapper.selectById(report.getId()));
    }

    /** 某孩子的报告列表（按年份倒序）。 */
    public PageResponse<ReportResponse> list(long userId, long childId, PageQuery query) {
        childService.get(userId, childId);
        Page<GrowthReport> page = new Page<>(query.page(), query.pageSize());
        reportMapper.selectPage(
                page,
                new LambdaQueryWrapper<GrowthReport>()
                        .eq(GrowthReport::getChildId, childId)
                        .orderByDesc(GrowthReport::getYear));
        return PageResponse.of(page).mapRecords(ReportResponse::of);
    }

    /** 下载：仅 ready 且本人单据；返回流与文件名。 */
    public ReportFile open(long userId, long reportId) {
        GrowthReport report = requireOwned(userId, reportId);
        if (!GrowthReport.STATUS_READY.equals(report.getStatus()) || report.getStorageKey() == null) {
            throw new ApiException(ApiErrorCode.REPORT_NOT_READY);
        }
        InputStream in = storageService.open(report.getStorageKey());
        String filename = "growthkids-report-" + report.getYear() + ".pdf";
        return new ReportFile(in, filename, storageService.size(report.getStorageKey()));
    }

    private GrowthReport findReport(long childId, int year) {
        List<GrowthReport> rows = reportMapper.selectList(
                new LambdaQueryWrapper<GrowthReport>()
                        .eq(GrowthReport::getChildId, childId)
                        .eq(GrowthReport::getReportType, GrowthReport.TYPE_ANNUAL)
                        .eq(GrowthReport::getYear, year)
                        .last("LIMIT 1"));
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private GrowthReport requireOwned(long userId, long reportId) {
        GrowthReport report = reportMapper.selectById(reportId);
        if (report == null || report.getUserId() == null || report.getUserId() != userId) {
            throw new ApiException(ApiErrorCode.REPORT_NOT_FOUND);
        }
        return report;
    }

    private String storageKey(GrowthReport report) {
        return "reports/" + report.getChildId() + "/annual-" + report.getYear() + "-" + report.getId() + ".pdf";
    }

    /** 下载文件句柄（流由调用方负责关闭）。 */
    public record ReportFile(InputStream stream, String filename, long size) {}
}
