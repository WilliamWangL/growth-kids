package com.growthkids.api.admin.service;

import com.growthkids.api.admin.dto.AdminChildResponse;
import com.growthkids.api.admin.dto.AdminLogResponse;
import com.growthkids.api.admin.dto.AdminPaymentResponse;
import com.growthkids.api.admin.dto.AdminUserResponse;
import com.growthkids.api.admin.dto.UpdateRoleRequest;
import com.growthkids.api.common.audit.AuditService;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.user.entity.User;
import com.growthkids.api.user.mapper.UserMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 后台管理服务（TASK-019，§77）：用户/孩子/支付/日志分页列表与角色管理。
 * 列表为跨表只读查询，统一 JdbcTemplate 原生 SQL（admin 需要看软删除数据，
 * 绕开 @TableLogic）；角色变更写 audit_logs（§71）。
 */
@Service
public class AdminService {

    private final JdbcTemplate jdbcTemplate;

    private final UserMapper userMapper;

    private final AuditService auditService;

    public AdminService(JdbcTemplate jdbcTemplate, UserMapper userMapper, AuditService auditService) {
        this.jdbcTemplate = jdbcTemplate;
        this.userMapper = userMapper;
        this.auditService = auditService;
    }

    /** 用户分页（id 倒序）：附活跃孩子数与当前 active 套餐。 */
    public PageResponse<AdminUserResponse> users(PageQuery query) {
        long total = requireCount("SELECT COUNT(*) FROM users");
        List<AdminUserResponse> items = jdbcTemplate.query(
                "SELECT u.id, u.email, u.display_name, u.role, u.status, u.created_at,"
                        + " (SELECT COUNT(*) FROM children c WHERE c.user_id = u.id AND c.status = 'active')"
                        + " AS child_count,"
                        + " (SELECT m.plan FROM memberships m WHERE m.user_id = u.id AND m.status = 'active'"
                        + " ORDER BY m.id DESC LIMIT 1) AS plan"
                        + " FROM users u ORDER BY u.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AdminUserResponse(
                        rs.getLong("id"),
                        rs.getString("email"),
                        rs.getString("display_name"),
                        rs.getString("role"),
                        rs.getString("status"),
                        rs.getString("plan"),
                        rs.getLong("child_count"),
                        rs.getTimestamp("created_at") == null
                                ? null
                                : rs.getTimestamp("created_at").toLocalDateTime()),
                query.pageSize(),
                (query.page() - 1) * query.pageSize());
        return new PageResponse<>(items, new PageResponse.Pagination(
                query.page(), query.pageSize(), total, (total + query.pageSize() - 1) / query.pageSize()));
    }

    /** 孩子分页（id 倒序，含软删除）：绕开 @TableLogic 由原生 SQL 全量返回。 */
    public PageResponse<AdminChildResponse> children(PageQuery query) {
        long total = requireCount("SELECT COUNT(*) FROM children");
        List<AdminChildResponse> items = jdbcTemplate.query(
                "SELECT c.id, c.user_id, u.email AS owner_email, c.name, c.gender, c.birth_date,"
                        + " c.status, c.created_at"
                        + " FROM children c JOIN users u ON c.user_id = u.id"
                        + " ORDER BY c.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AdminChildResponse(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("owner_email"),
                        rs.getString("name"),
                        rs.getString("gender"),
                        rs.getDate("birth_date") == null ? null : rs.getDate("birth_date").toLocalDate(),
                        rs.getString("status"),
                        rs.getTimestamp("created_at").toLocalDateTime()),
                query.pageSize(),
                (query.page() - 1) * query.pageSize());
        return new PageResponse<>(items, new PageResponse.Pagination(
                query.page(), query.pageSize(), total, (total + query.pageSize() - 1) / query.pageSize()));
    }

    /** 支付分页（id 倒序）：可按 paymentType/status 过滤（白名单校验后拼接）。 */
    public PageResponse<AdminPaymentResponse> payments(
            PageQuery query, String paymentType, String status) {
        if (paymentType != null && !List.of("membership", "donation").contains(paymentType)) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
        }
        if (status != null
                && !List.of("pending", "succeeded", "failed").contains(status)) {
            throw new ApiException(ApiErrorCode.VALIDATION_ERROR);
        }
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (paymentType != null && !paymentType.isBlank()) {
            where.append(" AND p.payment_type = '").append(paymentType).append("'");
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND p.status = '").append(status).append("'");
        }
        long total = requireCount("SELECT COUNT(*) FROM payments p" + where);
        List<AdminPaymentResponse> items = jdbcTemplate.query(
                "SELECT p.id, p.user_id, u.email AS user_email, p.provider, p.transaction_id, p.amount,"
                        + " p.currency, p.payment_type, p.status, p.paid_at, p.created_at"
                        + " FROM payments p JOIN users u ON p.user_id = u.id"
                        + where
                        + " ORDER BY p.id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AdminPaymentResponse(
                        rs.getLong("id"),
                        rs.getLong("user_id"),
                        rs.getString("user_email"),
                        rs.getString("provider"),
                        rs.getString("transaction_id"),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency"),
                        rs.getString("payment_type"),
                        rs.getString("status"),
                        rs.getTimestamp("paid_at") == null
                                ? null
                                : rs.getTimestamp("paid_at").toLocalDateTime(),
                        rs.getTimestamp("created_at") == null
                                ? null
                                : rs.getTimestamp("created_at").toLocalDateTime()),
                query.pageSize(),
                (query.page() - 1) * query.pageSize());
        return new PageResponse<>(items, new PageResponse.Pagination(
                query.page(), query.pageSize(), total, (total + query.pageSize() - 1) / query.pageSize()));
    }

    /**
     * 日志分页（§77 错误日志）：kind=webhook（webhook_logs，支付回调审计）
     * 或 audit（audit_logs，管理操作审计），均按 id 倒序。
     */
    public PageResponse<AdminLogResponse> logs(PageQuery query, String kind) {
        if ("audit".equals(kind)) {
            return auditLogs(query);
        }
        return webhookLogs(query);
    }

    private PageResponse<AdminLogResponse> webhookLogs(PageQuery query) {
        long total = requireCount("SELECT COUNT(*) FROM webhook_logs");
        List<AdminLogResponse> items = jdbcTemplate.query(
                "SELECT id, provider, event_type, transaction_id, signature_valid, processed, error,"
                        + " created_at FROM webhook_logs ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AdminLogResponse(
                        rs.getLong("id"),
                        "webhook",
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("provider") + " " + rs.getString("event_type"),
                        rs.getString("transaction_id"),
                        null,
                        rs.getBoolean("processed"),
                        rs.getString("error"),
                        null),
                query.pageSize(),
                (query.page() - 1) * query.pageSize());
        return pageOf(items, query, total);
    }

    private PageResponse<AdminLogResponse> auditLogs(PageQuery query) {
        long total = requireCount("SELECT COUNT(*) FROM audit_logs");
        List<AdminLogResponse> items = jdbcTemplate.query(
                "SELECT id, user_id, action, request_id, details, created_at"
                        + " FROM audit_logs ORDER BY id DESC LIMIT ? OFFSET ?",
                (rs, rowNum) -> new AdminLogResponse(
                        rs.getLong("id"),
                        "audit",
                        rs.getTimestamp("created_at").toLocalDateTime(),
                        rs.getString("action"),
                        rs.getString("request_id"),
                        rs.getObject("user_id") == null ? null : rs.getLong("user_id"),
                        null,
                        null,
                        rs.getString("details")),
                query.pageSize(),
                (query.page() - 1) * query.pageSize());
        return pageOf(items, query, total);
    }

    /** 变更用户角色（§77 user 管理）：写审计日志（§71），新角色自下次登录生效。 */
    @Transactional
    public AdminUserResponse updateRole(long operatorId, long targetUserId, UpdateRoleRequest request) {
        User target = userMapper.selectById(targetUserId);
        if (target == null) {
            throw new ApiException(ApiErrorCode.USER_NOT_FOUND);
        }
        String oldRole = target.getRole();
        if (!oldRole.equals(request.role())) {
            target.setRole(request.role());
            userMapper.updateById(target);
            Map<String, Object> details = new HashMap<>();
            details.put("from", oldRole);
            details.put("to", request.role());
            details.put("email", target.getEmail());
            auditService.record(operatorId, "admin.update_user_role", "user", targetUserId, details);
        }
        User reloaded = userMapper.selectById(targetUserId);
        return new AdminUserResponse(
                reloaded.getId(),
                reloaded.getEmail(),
                reloaded.getDisplayName(),
                reloaded.getRole(),
                reloaded.getStatus(),
                activePlanOf(reloaded.getId()),
                activeChildCountOf(reloaded.getId()),
                reloaded.getCreatedAt());
    }

    private String activePlanOf(long userId) {
        List<String> plans = jdbcTemplate.queryForList(
                "SELECT plan FROM memberships WHERE user_id = ? AND status = 'active'"
                        + " ORDER BY id DESC LIMIT 1",
                String.class,
                userId);
        return plans.isEmpty() ? null : plans.getFirst();
    }

    private long activeChildCountOf(long userId) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM children WHERE user_id = ? AND status = 'active'",
                Long.class,
                userId);
        return count == null ? 0 : count;
    }

    private long requireCount(String sql) {
        Long count = jdbcTemplate.queryForObject(sql, Long.class);
        return count == null ? 0 : count;
    }

    private PageResponse<AdminLogResponse> pageOf(
            List<AdminLogResponse> items, PageQuery query, long total) {
        return new PageResponse<>(items, new PageResponse.Pagination(
                query.page(), query.pageSize(), total, (total + query.pageSize() - 1) / query.pageSize()));
    }
}
