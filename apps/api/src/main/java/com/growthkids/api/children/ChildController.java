package com.growthkids.api.children;

import com.growthkids.api.children.dto.ChildResponse;
import com.growthkids.api.children.dto.CreateChildRequest;
import com.growthkids.api.children.dto.UpdateChildRequest;
import com.growthkids.api.children.service.ChildService;
import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 孩子管理 API（TASK-006，规范 §28）。 */
@RestController
@RequestMapping("/api/v1/children")
@Tag(name = "Children", description = "孩子管理")
public class ChildController {

    private final ChildService childService;

    public ChildController(ChildService childService) {
        this.childService = childService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "创建孩子", description = "创建孩子档案；受会员孩子数量上限约束（Free=1）")
    public ChildResponse create(@Valid @RequestBody CreateChildRequest request) {
        return childService.create(CurrentUser.id(), request);
    }

    @GetMapping
    @Operation(summary = "查询孩子列表", description = "分页查询当前用户的孩子（默认 page=1 pageSize=20，pageSize≤100）")
    public PageResponse<ChildResponse> list(
            @RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
        return childService.list(CurrentUser.id(), PageQuery.of(page, pageSize));
    }

    @GetMapping("/{childId}")
    @Operation(summary = "查询孩子详情")
    public ChildResponse detail(@PathVariable long childId) {
        return childService.get(CurrentUser.id(), childId);
    }

    @PatchMapping("/{childId}")
    @Operation(summary = "修改孩子", description = "PATCH 语义：字段缺省保持不变，空字符串清空（avatarUrl/notes）")
    public ChildResponse update(
            @PathVariable long childId, @Valid @RequestBody UpdateChildRequest request) {
        return childService.update(CurrentUser.id(), childId, request);
    }

    @DeleteMapping("/{childId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "删除孩子", description = "软删除（status=deleted）")
    public void delete(@PathVariable long childId) {
        childService.delete(CurrentUser.id(), childId);
    }
}
