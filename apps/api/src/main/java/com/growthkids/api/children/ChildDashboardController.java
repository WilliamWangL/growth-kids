package com.growthkids.api.children;

import com.growthkids.api.children.dto.DashboardResponse;
import com.growthkids.api.children.service.DashboardService;
import com.growthkids.api.common.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 孩子主页摘要 API（TASK-011，规范 §54/§112）。 */
@RestController
@RequestMapping("/api/v1/children/{childId}/dashboard")
@Tag(name = "Dashboard", description = "孩子主页摘要")
public class ChildDashboardController {

    private final DashboardService dashboardService;

    public ChildDashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping
    @Operation(
            summary = "孩子主页摘要",
            description = "孩子信息 + 当前年龄 + 最近一次成长记录（规范 §54/§112）")
    public DashboardResponse get(@PathVariable long childId) {
        return dashboardService.get(CurrentUser.id(), childId);
    }
}
