package com.growthkids.api.report;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.report.dto.CreateReportRequest;
import com.growthkids.api.report.dto.ReportResponse;
import com.growthkids.api.report.service.AnnualReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 年度报告 API（TASK-018，规范 §98/§99/§110）：
 * 创建（Pro 权益，异步生成）/列表/下载；PDF 生成与存储见 service 层。
 */
@RestController
@Tag(name = "Reports", description = "年度报告（Pro 权益）")
public class ReportController {

    private final AnnualReportService reportService;

    public ReportController(AnnualReportService reportService) {
        this.reportService = reportService;
    }

    @PostMapping("/api/v1/children/{childId}/reports")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建年度报告", description = "Pro 权益（§64）；同年重复创建为重生成；创建后异步生成 PDF（§99），前端轮询状态")
    public ReportResponse create(
            @PathVariable long childId, @Valid @RequestBody CreateReportRequest request) {
        return reportService.create(CurrentUser.id(), childId, request);
    }

    @GetMapping("/api/v1/children/{childId}/reports")
    @Operation(summary = "报告列表", description = "按年份倒序分页；status pending/generating/ready/failed")
    public PageResponse<ReportResponse> list(
            @PathVariable long childId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return reportService.list(CurrentUser.id(), childId, PageQuery.of(page, pageSize));
    }

    @GetMapping("/api/v1/reports/{reportId}/download")
    @Operation(summary = "下载 PDF", description = "仅 ready 状态；流式返回 application/pdf")
    public ResponseEntity<InputStreamResource> download(@PathVariable long reportId) {
        AnnualReportService.ReportFile file = reportService.open(CurrentUser.id(), reportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .contentLength(file.size())
                .body(new InputStreamResource(file.stream()));
    }
}
