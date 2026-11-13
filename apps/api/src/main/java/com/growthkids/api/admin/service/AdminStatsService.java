package com.growthkids.api.admin.service;

import com.growthkids.api.admin.dto.AdminDashboardResponse;
import com.growthkids.api.who.entity.WhoGrowthStandard;
import com.growthkids.api.who.mapper.WhoGrowthStandardMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 后台聚合统计（TASK-019，§77）：全表 COUNT/SUM，数量级在可控范围（单库），
 * 直接 JdbcTemplate 聚合最直白；WHO 版本取最新导入数据集元信息（§124）。
 */
@Service
public class AdminStatsService {

    private final JdbcTemplate jdbcTemplate;

    private final WhoGrowthStandardMapper whoMapper;

    public AdminStatsService(JdbcTemplate jdbcTemplate, WhoGrowthStandardMapper whoMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.whoMapper = whoMapper;
    }

    public AdminDashboardResponse dashboard() {
        long totalUsers = count("SELECT COUNT(*) FROM users");
        long activeUsers = count("SELECT COUNT(*) FROM users WHERE status = 'active'");
        long childrenTotal = count("SELECT COUNT(*) FROM children");
        long childrenActive = count("SELECT COUNT(*) FROM children WHERE status = 'active'");
        long growthRecords = count("SELECT COUNT(*) FROM growth_records");
        long photos = count("SELECT COUNT(*) FROM growth_photos");
        long memories = count("SELECT COUNT(*) FROM growth_memories");
        long activeMemberships =
                count("SELECT COUNT(*) FROM memberships WHERE status = 'active'");

        long membershipSucceeded = count(
                "SELECT COUNT(*) FROM payments WHERE payment_type = 'membership' AND status = 'succeeded'");
        BigDecimal membershipRevenue = sum(
                "SELECT COALESCE(SUM(amount), 0) FROM payments"
                        + " WHERE payment_type = 'membership' AND status = 'succeeded'");
        long donationSucceeded = count(
                "SELECT COUNT(*) FROM payments WHERE payment_type = 'donation' AND status = 'succeeded'");
        BigDecimal donationRevenue = sum(
                "SELECT COALESCE(SUM(amount), 0) FROM payments"
                        + " WHERE payment_type = 'donation' AND status = 'succeeded'");
        long pending = count("SELECT COUNT(*) FROM payments WHERE status = 'pending'");
        long failed = count("SELECT COUNT(*) FROM payments WHERE status = 'failed'");

        long reportTotal = count("SELECT COUNT(*) FROM growth_reports");
        long reportReady = count("SELECT COUNT(*) FROM growth_reports WHERE status = 'ready'");

        return new AdminDashboardResponse(
                totalUsers,
                activeUsers,
                new AdminDashboardResponse.ChildStats(
                        childrenTotal, childrenActive, childrenTotal - childrenActive),
                growthRecords,
                photos,
                memories,
                activeMemberships,
                new AdminDashboardResponse.PaymentStats(
                        membershipSucceeded,
                        membershipRevenue,
                        donationSucceeded,
                        donationRevenue,
                        pending,
                        failed),
                new AdminDashboardResponse.ReportStats(reportTotal, reportReady),
                whoVersion());
    }

    /** WHO 数据集版本（§124）：source/version + 行数 + 最新导入时间。 */
    private AdminDashboardResponse.WhoVersionInfo whoVersion() {
        WhoGrowthStandard latest = whoMapper.findLatestImported();
        if (latest == null) {
            return new AdminDashboardResponse.WhoVersionInfo(null, null, 0, null);
        }
        Long rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM who_growth_standards WHERE source = ? AND version = ?",
                Long.class,
                latest.getSource(),
                latest.getVersion());
        String importedAt = jdbcTemplate.queryForObject(
                "SELECT MAX(created_at) FROM who_growth_standards WHERE source = ? AND version = ?",
                String.class,
                latest.getSource(),
                latest.getVersion());
        return new AdminDashboardResponse.WhoVersionInfo(
                latest.getSource(), latest.getVersion(), rows == null ? 0 : rows, importedAt);
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }

    private BigDecimal sum(String sql) {
        BigDecimal value = jdbcTemplate.queryForObject(sql, BigDecimal.class);
        return value == null ? BigDecimal.ZERO : value;
    }
}
