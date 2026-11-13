package com.growthkids.api.support;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 集成测试数据清理：按外键依赖顺序删除 it-% 测试用户及其全部数据。
 * 后续任务新增依赖 children/users 的表时，在此处按 FK 顺序补充。
 */
public final class IntegrationTestCleaner {

    private IntegrationTestCleaner() {}

    private static final String[] STATEMENTS = {
        // 无用户外键的审计表（TASK-016 webhook 测试写入；TASK-019 admin 审计）
        "DELETE FROM webhook_logs",
        "DELETE FROM audit_logs",
        // 依赖 children 的表
        "DELETE gp FROM growth_photos gp JOIN children c ON gp.child_id = c.id"
                + " JOIN users u ON c.user_id = u.id WHERE u.email LIKE 'it-%'",
        "DELETE gm FROM growth_memories gm JOIN children c ON gm.child_id = c.id"
                + " JOIN users u ON c.user_id = u.id WHERE u.email LIKE 'it-%'",
        "DELETE gr FROM growth_records gr JOIN children c ON gr.child_id = c.id"
                + " JOIN users u ON c.user_id = u.id WHERE u.email LIKE 'it-%'",
        "DELETE rp FROM growth_reports rp JOIN children c ON rp.child_id = c.id"
                + " JOIN users u ON c.user_id = u.id WHERE u.email LIKE 'it-%'",
        "DELETE c FROM children c JOIN users u ON c.user_id = u.id WHERE u.email LIKE 'it-%'",
        // 依赖 users 的表
        "DELETE rt FROM refresh_tokens rt JOIN users u ON rt.user_id = u.id"
                + " WHERE u.email LIKE 'it-%'",
        "DELETE m FROM memberships m JOIN users u ON m.user_id = u.id WHERE u.email LIKE 'it-%'",
        "DELETE p FROM payments p JOIN users u ON p.user_id = u.id WHERE u.email LIKE 'it-%'",
        "DELETE FROM users WHERE email LIKE 'it-%'",
    };

    public static void cleanUp(JdbcTemplate jdbcTemplate) {
        for (String statement : STATEMENTS) {
            jdbcTemplate.update(statement);
        }
    }
}
