package com.growthkids.api.growth;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.growth.dto.CreateGrowthRecordRequest;
import com.growthkids.api.growth.dto.GrowthRecordResponse;
import com.growthkids.api.growth.service.GrowthRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 孩子成长记录 API（TASK-007，规范 §29-§30）。 */
@RestController
@RequestMapping("/api/v1/children/{childId}/growth-records")
@Tag(name = "Growth Records", description = "成长记录")
public class ChildGrowthRecordController {

    private final GrowthRecordService growthRecordService;

    public ChildGrowthRecordController(GrowthRecordService growthRecordService) {
        this.growthRecordService = growthRecordService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
            summary = "创建成长记录",
            description = "自动计算年龄与 BMI 并保存；同一孩子同一日期仅允许一条（409）")
    public GrowthRecordResponse create(
            @PathVariable long childId, @Valid @RequestBody CreateGrowthRecordRequest request) {
        return growthRecordService.create(CurrentUser.id(), childId, request);
    }

    @GetMapping
    @Operation(summary = "查询成长记录", description = "分页 + startDate/endDate 过滤，按记录日期倒序")
    public PageResponse<GrowthRecordResponse> list(
            @PathVariable long childId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return growthRecordService.list(
                CurrentUser.id(), childId, PageQuery.of(page, pageSize), startDate, endDate);
    }
}
