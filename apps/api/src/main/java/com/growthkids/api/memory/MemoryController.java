package com.growthkids.api.memory;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.memory.dto.CreateMemoryRequest;
import com.growthkids.api.memory.dto.MemoryResponse;
import com.growthkids.api.memory.dto.UpdateMemoryRequest;
import com.growthkids.api.memory.service.MemoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 成长故事 API（TASK-015，规范 §114/§16；时间线读取侧见 §52）。 */
@RestController
@Tag(name = "Memories", description = "成长故事")
public class MemoryController {

    private final MemoryService memoryService;

    public MemoryController(MemoryService memoryService) {
        this.memoryService = memoryService;
    }

    @PostMapping("/api/v1/children/{childId}/memories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建成长故事", description = "日期不可未来/早于出生，标题可选，内容必填（§114）")
    public MemoryResponse create(
            @PathVariable long childId, @Valid @RequestBody CreateMemoryRequest request) {
        return memoryService.create(CurrentUser.id(), childId, request);
    }

    @GetMapping("/api/v1/children/{childId}/memories")
    @Operation(summary = "成长故事列表", description = "分页返回，按日期倒序")
    public PageResponse<MemoryResponse> list(
            @PathVariable long childId,
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return memoryService.list(CurrentUser.id(), childId, PageQuery.of(page, pageSize));
    }

    @PatchMapping("/api/v1/memories/{memoryId}")
    @Operation(summary = "编辑成长故事", description = "缺省不变；title 空串清空；content 不可清空")
    public MemoryResponse update(
            @PathVariable long memoryId, @Valid @RequestBody UpdateMemoryRequest request) {
        return memoryService.update(CurrentUser.id(), memoryId, request);
    }

    @DeleteMapping("/api/v1/memories/{memoryId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除成长故事", description = "物理删除；关联照片解关联保留")
    public void delete(@PathVariable long memoryId) {
        memoryService.delete(CurrentUser.id(), memoryId);
    }
}
