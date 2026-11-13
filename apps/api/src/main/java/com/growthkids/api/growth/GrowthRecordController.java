package com.growthkids.api.growth;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.growth.dto.GrowthRecordResponse;
import com.growthkids.api.growth.dto.UpdateGrowthRecordRequest;
import com.growthkids.api.growth.service.GrowthRecordService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 成长记录修改/删除 API（TASK-007，规范 §31-§32）。 */
@RestController
@RequestMapping("/api/v1/growth-records")
@Tag(name = "Growth Records", description = "成长记录")
public class GrowthRecordController {

    private final GrowthRecordService growthRecordService;

    public GrowthRecordController(GrowthRecordService growthRecordService) {
        this.growthRecordService = growthRecordService;
    }

    @PatchMapping("/{recordId}")
    @Operation(summary = "修改成长记录", description = "PATCH 语义：缺省不变；修改后自动重算年龄与 BMI（规范 §31）")
    public GrowthRecordResponse update(
            @PathVariable long recordId, @Valid @RequestBody UpdateGrowthRecordRequest request) {
        return growthRecordService.update(CurrentUser.id(), recordId, request);
    }

    @DeleteMapping("/{recordId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除成长记录")
    public void delete(@PathVariable long recordId) {
        growthRecordService.delete(CurrentUser.id(), recordId);
    }
}
