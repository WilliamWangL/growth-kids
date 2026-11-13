package com.growthkids.api.membership;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.payment.PaymentProvider;
import com.growthkids.api.support.IntegrationTestCleaner;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
 * 会员集成测试（TASK-016，规范 §64/§66/§67）：
 * 套餐公开列表/free 默认订阅/订阅结算/限权生效（孩子/照片/时间线）/
 * webhook 验签与幂等/mock-complete 闭环/取消回落。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MembershipControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String CHILDREN = "/api/v1/children";
    private static final String PLANS = "/api/v1/membership/plans";
    private static final String MEMBERSHIP = "/api/v1/membership";
    private static final String SUBSCRIBE = "/api/v1/membership/subscribe";
    private static final String MOCK_WEBHOOK = "/api/v1/payments/webhook/mock";
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private PaymentProvider paymentProvider;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void cleanUp() {
        IntegrationTestCleaner.cleanUp(jdbcTemplate);
    }

    @Test
    void plansArePublicWithSeededPlans() throws Exception {
        // 无 token 可访问（§64 营销展示）
        mockMvc.perform(get(PLANS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("free"))
                .andExpect(jsonPath("$[0].priceAmount").value(0))
                .andExpect(jsonPath("$[0].permissions.childLimit").value(1))
                .andExpect(jsonPath("$[0].permissions.photoLimit").value(100))
                .andExpect(jsonPath("$[0].permissions.timelineLimit").value(20))
                .andExpect(jsonPath("$[1].code").value("pro"))
                .andExpect(jsonPath("$[1].priceAmount").value(4.99))
                .andExpect(jsonPath("$[1].billingPeriod").value("monthly"))
                .andExpect(jsonPath("$[1].permissions.childLimit").value(5))
                .andExpect(jsonPath("$[1].permissions.photoLimit").value(-1))
                .andExpect(jsonPath("$[1].permissions.timelineLimit").value(-1))
                .andExpect(jsonPath("$[1].permissions.advancedPrediction").value(true));
    }

    @Test
    void myMembershipDefaultsToFreeAndRequiresAuth() throws Exception {
        mockMvc.perform(get(MEMBERSHIP))
                .andExpect(status().isUnauthorized());

        String token = registerAndGetToken(email("mm-default"));
        mockMvc.perform(get(MEMBERSHIP).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("free"))
                .andExpect(jsonPath("$.status").doesNotExist())
                .andExpect(jsonPath("$.expiresAt").doesNotExist())
                .andExpect(jsonPath("$.permissions.childLimit").value(1))
                .andExpect(jsonPath("$.permissions.photoLimit").value(100))
                .andExpect(jsonPath("$.permissions.timelineLimit").value(20));
    }

    @Test
    void subscribeCreatesPendingPaymentAndRejectsInvalidPlans() throws Exception {
        String token = registerAndGetToken(email("mm-sub"));

        // free 无需订阅；未知套餐一律 404（不泄露细节）
        mockMvc.perform(post(SUBSCRIBE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planCode", "free"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMBERSHIP_PLAN_NOT_FOUND"));
        mockMvc.perform(post(SUBSCRIBE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planCode", "ultimate"))))
                .andExpect(status().isNotFound());

        MvcResult result = mockMvc.perform(post(SUBSCRIBE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planCode", "pro"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.transactionId").value(
                        org.hamcrest.Matchers.startsWith("mock-")))
                .andExpect(jsonPath("$.checkoutUrl").isString())
                .andReturn();
        String transactionId = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("transactionId").asText();

        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE transaction_id = ?", String.class, transactionId);
        org.junit.jupiter.api.Assertions.assertEquals("pending", status);
    }

    @Test
    void fullSubscriptionLifecycleEnforcesPlanLimits() throws Exception {
        String token = registerAndGetToken(email("mm-lifecycle"));
        long childId = createChild(token, "Tom", "2025-01-01");

        // Free 限额 1：第二个孩子 403
        mockMvc.perform(post(CHILDREN)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Jerry", "gender", "male", "birthDate", "2025-02-01"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHILD_LIMIT_REACHED"));

        subscribeAndComplete(token, "pro");

        mockMvc.perform(get(MEMBERSHIP).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("pro"))
                .andExpect(jsonPath("$.status").value("active"))
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.expiresAt").exists());
        // webhook 路径已将 payment 置为 succeeded
        Integer paid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE status = 'succeeded' AND paid_at IS NOT NULL",
                Integer.class);
        org.junit.jupiter.api.Assertions.assertEquals(1, paid);

        // Pro 限额 5：第 2-5 个孩子成功，第 6 个 403
        for (int i = 2; i <= 5; i++) {
            createChild(token, "Child" + i, "2025-0" + ((i % 9) + 1) + "-01");
        }
        mockMvc.perform(post(CHILDREN)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Sixth", "gender", "male", "birthDate", "2025-09-01"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHILD_LIMIT_REACHED"));

        // 取消 → 立即回落 Free；第 6 个孩子仍 403，可重新订阅
        mockMvc.perform(post(MEMBERSHIP + "/cancel").header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("free"))
                .andExpect(jsonPath("$.status").doesNotExist());
        mockMvc.perform(post(CHILDREN)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", "Sixth", "gender", "male", "birthDate", "2025-09-01"))))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(SUBSCRIBE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planCode", "pro"))))
                .andExpect(status().isCreated());
    }

    @Test
    void webhookRejectsInvalidSignatureAndLogs() throws Exception {
        String payload =
                "{\"provider\":\"mock\",\"eventType\":\"payment.succeeded\","
                        + "\"transactionId\":\"mock-unknown\",\"planCode\":\"pro\"}";

        mockMvc.perform(post(MOCK_WEBHOOK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WEBHOOK_INVALID_SIGNATURE"));
        mockMvc.perform(post(MOCK_WEBHOOK)
                        .header("X-Signature", "deadbeef")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest());

        Integer invalid = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_logs"
                        + " WHERE signature_valid = 0 AND processed = 0 AND transaction_id = ?",
                Integer.class,
                "mock-unknown");
        org.junit.jupiter.api.Assertions.assertEquals(2, invalid);
    }

    @Test
    void webhookIsIdempotentForReplayedEvents() throws Exception {
        String token = registerAndGetToken(email("mm-webhook"));
        String transactionId = subscribe(token);
        String payload = succeededPayload(transactionId, "4.99", "USD");
        String signature = paymentProvider.signPayload(payload);

        mockMvc.perform(post(MOCK_WEBHOOK)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value("true"));
        String expiresAt = jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(expires_at, '%Y-%m-%d %H:%i') FROM memberships"
                        + " JOIN users ON memberships.user_id = users.id"
                        + " WHERE users.email = ?",
                String.class,
                email("mm-webhook"));

        // 重放同一事件：幂等 200，不重复激活/不重复写 processed 日志
        mockMvc.perform(post(MOCK_WEBHOOK)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        Integer processedLogs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_logs WHERE transaction_id = ? AND processed = 1",
                Integer.class,
                transactionId);
        org.junit.jupiter.api.Assertions.assertEquals(1, processedLogs);
        String expiresAtAfter = jdbcTemplate.queryForObject(
                "SELECT DATE_FORMAT(expires_at, '%Y-%m-%d %H:%i') FROM memberships"
                        + " JOIN users ON memberships.user_id = users.id"
                        + " WHERE users.email = ?",
                String.class,
                email("mm-webhook"));
        org.junit.jupiter.api.Assertions.assertEquals(expiresAt, expiresAtAfter);
    }

    @Test
    void mockCompleteIsIdempotentAndRejectsForeignPayment() throws Exception {
        String token = registerAndGetToken(email("mm-mock"));
        String transactionId = subscribe(token);

        mockMvc.perform(post(MEMBERSHIP + "/mock-complete/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("pro"))
                .andExpect(jsonPath("$.status").value("active"));
        // 第二次调用幂等：不报错、不重复写 payment
        mockMvc.perform(post(MEMBERSHIP + "/mock-complete/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value("pro"));
        Integer payments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE transaction_id = ?", Integer.class, transactionId);
        org.junit.jupiter.api.Assertions.assertEquals(1, payments);

        // 他人交易号 404
        String other = registerAndGetToken(email("mm-mock-other"));
        mockMvc.perform(post(MEMBERSHIP + "/mock-complete/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void freeTimelineLimitEnforcedAndUnlockedByPro() throws Exception {
        String token = registerAndGetToken(email("mm-timeline"));
        long childId = createChild(token, "Tom", "2025-01-01");
        insertMemories(childId, 25);

        mockMvc.perform(get(CHILDREN + "/" + childId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(20));

        subscribeAndComplete(token, "pro");
        mockMvc.perform(get(CHILDREN + "/" + childId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(25));
    }

    @Test
    void freePhotoLimitEnforcedAndUnlockedByPro() throws Exception {
        String token = registerAndGetToken(email("mm-photo"));
        long childId = createChild(token, "Tom", "2025-01-01");
        jdbcTemplate.update(
                "INSERT INTO growth_photos (child_id, storage_key, original_filename, mime_type,"
                        + " file_size, processing_status) VALUES (?, ?, ?, 'image/jpeg', 1024, 'ready')",
                childId,
                "it/" + runId + "/quota.jpg",
                "quota.jpg");
        // 直插补齐到 100 张（storage_key 唯一）
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < 99; i++) {
            rows.add(new Object[] {childId, "it/" + runId + "/bulk-" + i + ".jpg", "bulk-" + i + ".jpg"});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO growth_photos (child_id, storage_key, original_filename, mime_type,"
                        + " file_size, processing_status) VALUES (?, ?, ?, 'image/jpeg', 1024, 'ready')",
                rows);

        mockMvc.perform(post(CHILDREN + "/" + childId + "/photos/presign")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "filename", "over.jpg", "mimeType", "image/jpeg", "size", 1024))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBERSHIP_REQUIRED"));

        subscribeAndComplete(token, "pro");
        mockMvc.perform(post(CHILDREN + "/" + childId + "/photos/presign")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "filename", "over.jpg", "mimeType", "image/jpeg", "size", 1024))))
                .andExpect(status().isCreated());
    }

    // ---- helpers ----

    /** 订阅 + mock 完成支付（走真实 webhook 处理路径）。 */
    private void subscribeAndComplete(String token, String planCode) throws Exception {
        String transactionId = subscribe(token, planCode);
        mockMvc.perform(post(MEMBERSHIP + "/mock-complete/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan").value(planCode));
    }

    private String subscribe(String token) throws Exception {
        return subscribe(token, "pro");
    }

    private String subscribe(String token, String planCode) throws Exception {
        MvcResult result = mockMvc.perform(post(SUBSCRIBE)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("planCode", planCode))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("transactionId").asText();
    }

    /** 构造与 MockPaymentProvider 相同格式的事件（金额/币种对齐种子套餐）。 */
    private String succeededPayload(String transactionId, String amount, String currency) {
        return "{\"provider\":\"mock\",\"eventType\":\"payment.succeeded\","
                + "\"transactionId\":\"" + transactionId + "\",\"amount\":" + amount
                + ",\"currency\":\"" + currency + "\",\"planCode\":\"pro\","
                + "\"providerSubscriptionId\":\"mock-sub-test\"}";
    }

    private void insertMemories(long childId, int count) {
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            rows.add(new Object[] {childId, today.minusDays(i), "故事" + i});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO growth_memories (child_id, memory_date, content) VALUES (?, ?, ?)", rows);
    }

    private long createChild(String token, String name, String birthDate) throws Exception {
        MvcResult result = mockMvc.perform(post(CHILDREN)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "gender", "male", "birthDate", birthDate))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email, "password", PASSWORD, "displayName", "Parent"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
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

