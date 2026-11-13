package com.growthkids.api.admin;

import com.growthkids.api.admin.dto.AdminDashboardResponse;
import com.growthkids.api.admin.dto.AdminUserResponse;
import com.growthkids.api.admin.dto.UpdateRoleRequest;
import com.growthkids.api.admin.service.AdminService;
import com.growthkids.api.admin.service.AdminStatsService;
import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台管理 API（TASK-019，规范 §77/§110）：
 * dashboard 统计/users/children/payments/membership/WHO version/logs。
 * 路由级权限已在 SecurityConfig（/api/v1/admin/** hasRole ADMIN），
 * 方法级 @PreAuthorize 双保险（防未来路由配置漂移）。
 */
@RestController
@RequestMapping("/api/v1/admin")
@Tag(name = "Admin", description = "后台管理（仅 admin）")
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminStatsService statsService;

    private final AdminService adminService;

    public AdminController(AdminStatsService statsService, AdminService adminService) {
        this.statsService = statsService;
        this.adminService = adminService;
    }

    @GetMapping("/dashboard")
    @Operation(summary = "统计总览", description = "用户/孩子/记录/照片/会员/支付/捐赠/报告/WHO 版本（§77）")
    public AdminDashboardResponse dashboard() {
        return statsService.dashboard();
    }

    @GetMapping("/users")
    @Operation(summary = "用户列表", description = "分页，附活跃孩子数与当前套餐")
    public PageResponse<AdminUserResponse> users(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return adminService.users(PageQuery.of(page, pageSize));
    }

    @PatchMapping("/users/{userId}/role")
    @Operation(summary = "变更用户角色", description = "user↔admin；写审计日志（§71）；新角色自下次登录生效")
    public AdminUserResponse updateRole(
            @PathVariable long userId, @Valid @RequestBody UpdateRoleRequest request) {
        return adminService.updateRole(CurrentUser.id(), userId, request);
    }

    @GetMapping("/children")
    @Operation(summary = "孩子列表", description = "分页，含已软删除（admin 视角全量）")
    public PageResponse<?> children(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize) {
        return adminService.children(PageQuery.of(page, pageSize));
    }

    @GetMapping("/payments")
    @Operation(summary = "支付列表", description = "分页，可按 paymentType/status 过滤（membership+donation）")
    public PageResponse<?> payments(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(required = false) String paymentType,
            @RequestParam(required = false) String status) {
        return adminService.payments(PageQuery.of(page, pageSize), paymentType, status);
    }

    @GetMapping("/logs")
    @Operation(summary = "日志列表", description = "kind=webhook（支付回调）/audit（管理操作），按 id 倒序分页")
    public PageResponse<?> logs(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(defaultValue = "webhook") String kind) {
        return adminService.logs(PageQuery.of(page, pageSize), kind);
    }
}
