package com.growthkids.api.admin;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.support.IntegrationTestCleaner;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * 后台管理集成测试（TASK-019，规范 §77/§71）：
 * 角色门禁（401/403）/dashboard 聚合/用户列表/角色变更+审计+重登生效/
 * 孩子列表含软删/支付过滤/日志两类（webhook+audit）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AdminControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String CHILDREN = "/api/v1/children";
    private static final String SUBSCRIBE = "/api/v1/membership/subscribe";
    private static final String MEMBERSHIP = "/api/v1/membership";
    private static final String DONATION_CHECKOUT = "/api/v1/donations/checkout";
    private static final String DONATION = "/api/v1/donations";
    private static final String ADMIN = "/api/v1/admin";
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void cleanUp() {
        IntegrationTestCleaner.cleanUp(jdbcTemplate);
    }

    @Test
    void nonAdminAndAnonymousAreRejected() throws Exception {
        mockMvc.perform(get(ADMIN + "/dashboard")).andExpect(status().isUnauthorized());

        String token = registerAndGetToken(email("ad-plain"));
        mockMvc.perform(get(ADMIN + "/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(ADMIN + "/users").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardAggregatesPlatformStats() throws Exception {
        registerAndGetToken(email("ad-admin"));
        jdbcTemplate.update("UPDATE users SET role = 'admin' WHERE email = ?", email("ad-admin"));
        String admin = loginAndGetToken(email("ad-admin")); // 重新登录，新 token 才携带 admin role

        String user = registerAndGetToken(email("ad-user"));
        long userChildId = createChild(user, "小华");
        createRecord(user, userChildId, "2026-01-15");
        createRecord(user, userChildId, "2026-03-01");
        subscribeAndComplete(user);
        donateAndComplete(user);
        jdbcTemplate.update(
                "INSERT INTO growth_memories (child_id, memory_date, content) VALUES (?, CURDATE(), '第一次跑步')",
                userChildId);
        jdbcTemplate.update(
                "INSERT INTO growth_photos (child_id, storage_key, original_filename, mime_type,"
                        + " file_size, processing_status) VALUES (?, ?, ?, 'image/jpeg', 1024, 'ready')",
                userChildId,
                "it/" + runId + "/admin.jpg",
                "admin.jpg");
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email("ad-user"));
        jdbcTemplate.update(
                "INSERT INTO growth_reports (child_id, user_id, report_type, year, status, storage_key)"
                        + " VALUES (?, ?, 'annual', 2025, 'ready', ?)",
                userChildId,
                userId,
                "it/" + runId + "/admin-report.pdf");
        // admin 自己的孩子创建后软删除
        long adminChildId = createChild(admin, "小管理员");
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete(CHILDREN + "/" + adminChildId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ADMIN + "/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(2))
                .andExpect(jsonPath("$.activeUsers").value(2))
                .andExpect(jsonPath("$.children.total").value(2))
                .andExpect(jsonPath("$.children.active").value(1))
                .andExpect(jsonPath("$.children.deleted").value(1))
                .andExpect(jsonPath("$.growthRecords").value(2))
                .andExpect(jsonPath("$.photos").value(1))
                .andExpect(jsonPath("$.memories").value(1))
                .andExpect(jsonPath("$.activeMemberships").value(1))
                .andExpect(jsonPath("$.payments.membershipSucceeded").value(1))
                .andExpect(jsonPath("$.payments.membershipRevenue").value(4.99))
                .andExpect(jsonPath("$.payments.donationSucceeded").value(1))
                .andExpect(jsonPath("$.payments.donationRevenue").value(5.0))
                .andExpect(jsonPath("$.payments.pending").value(0))
                .andExpect(jsonPath("$.reports.total").value(1))
                .andExpect(jsonPath("$.reports.ready").value(1))
                .andExpect(jsonPath("$.who.source").isNotEmpty())
                .andExpect(jsonPath("$.who.version").isNotEmpty())
                .andExpect(jsonPath("$.who.rows").isNotEmpty());
    }

    @Test
    void userListShowsPlanAndChildCount() throws Exception {
        registerAndGetToken(email("ad-list"));
        jdbcTemplate.update("UPDATE users SET role = 'admin' WHERE email = ?", email("ad-list"));
        String admin = loginAndGetToken(email("ad-list")); // 重新登录，新 token 才携带 admin role

        String user = registerAndGetToken(email("ad-list-user"));
        createChild(user, "小清单");
        subscribeAndComplete(user);

        mockMvc.perform(get(ADMIN + "/users").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(2))
                // id 倒序：后注册的普通用户在前
                .andExpect(jsonPath("$.items[0].email").value(email("ad-list-user")))
                .andExpect(jsonPath("$.items[0].role").value("user"))
                .andExpect(jsonPath("$.items[0].plan").value("pro"))
                .andExpect(jsonPath("$.items[0].childCount").value(1))
                .andExpect(jsonPath("$.items[1].email").value(email("ad-list")))
                .andExpect(jsonPath("$.items[1].role").value("admin"))
                .andExpect(jsonPath("$.items[1].plan").doesNotExist());
    }

    @Test
    void updateRoleWritesAuditAndTakesEffectAfterRelogin() throws Exception {
        registerAndGetToken(email("ad-role"));
        jdbcTemplate.update("UPDATE users SET role = 'admin' WHERE email = ?", email("ad-role"));
        String admin = loginAndGetToken(email("ad-role")); // 重新登录，新 token 才携带 admin role
        String user = registerAndGetToken(email("ad-role-user"));
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email("ad-role-user"));

        // 非法角色值 400；不存在用户 404
        mockMvc.perform(patch(ADMIN + "/users/" + userId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "superadmin"))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch(ADMIN + "/users/999999/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "admin"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("USER_NOT_FOUND"));

        // 提升为 admin
        mockMvc.perform(patch(ADMIN + "/users/" + userId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "admin"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("admin"));

        // 审计日志已写入（§71）
        Integer audits = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'admin.update_user_role'"
                        + " AND entity_type = 'user' AND entity_id = ?",
                Integer.class,
                userId);
        Assertions.assertEquals(1, audits);

        // 旧 token 仍是 user 角色（403）；重新登录后新 token 带 admin（200）
        mockMvc.perform(get(ADMIN + "/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
        String newToken = loginAndGetToken(email("ad-role-user"));
        mockMvc.perform(get(ADMIN + "/dashboard").header(HttpHeaders.AUTHORIZATION, bearer(newToken)))
                .andExpect(status().isOk());

        // 幂等重放同角色：200 且不重复写审计
        mockMvc.perform(patch(ADMIN + "/users/" + userId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "admin"))))
                .andExpect(status().isOk());
        Integer auditsAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_logs WHERE action = 'admin.update_user_role'"
                        + " AND entity_type = 'user' AND entity_id = ?",
                Integer.class,
                userId);
        Assertions.assertEquals(1, auditsAfter);
    }

    @Test
    void childrenListIncludesSoftDeleted() throws Exception {
        registerAndGetToken(email("ad-child"));
        jdbcTemplate.update("UPDATE users SET role = 'admin' WHERE email = ?", email("ad-child"));
        String admin = loginAndGetToken(email("ad-child")); // 重新登录，新 token 才携带 admin role
        String user = registerAndGetToken(email("ad-child-user"));
        subscribeAndComplete(user); // Pro 限额 5，才能建第二个孩子
        long activeChild = createChild(user, "保留的孩子");
        long deletedChild = createChild(user, "删除的孩子");
        mockMvc.perform(
                        org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                                .delete(CHILDREN + "/" + deletedChild)
                                .header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(ADMIN + "/children").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(2))
                .andExpect(jsonPath("$.items[?(@.id == " + activeChild + ")].status")
                        .value(org.hamcrest.Matchers.hasItem("active")))
                .andExpect(jsonPath("$.items[?(@.id == " + deletedChild + ")].status")
                        .value(org.hamcrest.Matchers.hasItem("deleted")))
                .andExpect(jsonPath("$.items[?(@.id == " + deletedChild + ")].ownerEmail")
                        .value(org.hamcrest.Matchers.hasItem(email("ad-child-user"))));
    }

    @Test
    void paymentsListFiltersByTypeAndStatus() throws Exception {
        registerAndGetToken(email("ad-pay"));
        jdbcTemplate.update("UPDATE users SET role = 'admin' WHERE email = ?", email("ad-pay"));
        String admin = loginAndGetToken(email("ad-pay")); // 重新登录，新 token 才携带 admin role
        String user = registerAndGetToken(email("ad-pay-user"));
        subscribeAndComplete(user);
        donateAndComplete(user);
        // 一笔 pending 捐赠（不 mock 完成）
        mockMvc.perform(post(DONATION_CHECKOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(user))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 8))))
                .andExpect(status().isCreated());

        mockMvc.perform(get(ADMIN + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("paymentType", "donation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(2))
                .andExpect(jsonPath("$.items[0].paymentType").value("donation"))
                .andExpect(jsonPath("$.items[0].userEmail").value(email("ad-pay-user")));
        mockMvc.perform(get(ADMIN + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("paymentType", "membership")
                        .queryParam("status", "succeeded"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(1))
                .andExpect(jsonPath("$.items[0].paymentType").value("membership"))
                .andExpect(jsonPath("$.items[0].status").value("succeeded"));
        // 非法过滤值 400
        mockMvc.perform(get(ADMIN + "/payments")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("paymentType", "savings"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void logsReturnWebhookAndAuditKinds() throws Exception {
        registerAndGetToken(email("ad-logs"));
        jdbcTemplate.update("UPDATE users SET role = 'admin' WHERE email = ?", email("ad-logs"));
        String admin = loginAndGetToken(email("ad-logs")); // 重新登录，新 token 才携带 admin role
        String user = registerAndGetToken(email("ad-logs-user"));
        subscribeAndComplete(user); // 产生一条 processed webhook 日志

        // 角色变更产生 audit 日志
        long userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email("ad-logs-user"));
        mockMvc.perform(patch(ADMIN + "/users/" + userId + "/role")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("role", "admin"))))
                .andExpect(status().isOk());

        mockMvc.perform(get(ADMIN + "/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("kind", "webhook"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("webhook"))
                .andExpect(jsonPath("$.items[0].processed").value(true));
        mockMvc.perform(get(ADMIN + "/logs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .queryParam("kind", "audit"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].kind").value("audit"))
                .andExpect(jsonPath("$.items[0].summary").value("admin.update_user_role"))
                .andExpect(jsonPath("$.items[0].details").isNotEmpty());
        // 默认 kind=webhook
        mockMvc.perform(get(ADMIN + "/logs").header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].kind").value("webhook"));
    }

    // ---- helpers ----

    private void donateAndComplete(String token) throws Exception {
        MvcResult result = mockMvc.perform(post(DONATION_CHECKOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 5))))
                .andExpect(status().isCreated())
                .andReturn();
        String transactionId = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("transactionId")
                .asText();
        mockMvc.perform(post(DONATION + "/mock-complete/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private void subscribeAndComplete(String token) throws Exception {
        MvcResult result = mockMvc.perform(post(SUBSCRIBE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planCode", "pro"))))
                .andExpect(status().isCreated())
                .andReturn();
        String transactionId = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("transactionId")
                .asText();
        mockMvc.perform(post(MEMBERSHIP + "/mock-complete/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private long createChild(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post(CHILDREN)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "gender", "female", "birthDate", "2023-05-20"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asLong();
    }

    private void createRecord(String token, long childId, String recordDate) throws Exception {
        mockMvc.perform(post(CHILDREN + "/" + childId + "/growth-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recordDate", recordDate,
                                "heightCm", 96.0,
                                "weightKg", 14.5))))
                .andExpect(status().isCreated());
    }

    private String loginAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(LOGIN)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email, "password", PASSWORD, "displayName", "Admin"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String email(String tag) {
        return "it-" + runId + "-" + tag + "@test.dev";
    }
}
