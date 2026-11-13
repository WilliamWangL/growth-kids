package com.growthkids.api.donation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.payment.PaymentProvider;
import com.growthkids.api.support.IntegrationTestCleaner;
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
 * 捐赠集成测试（TASK-017，规范 §65/§66/§67）：
 * checkout 金额校验/mock-complete 幂等与 ownership/webhook 同端点按 payment_type 分发
 * （含拒签与 failed 事件）/历史隔离与分页。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DonationControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String DONATIONS = "/api/v1/donations";
    private static final String CHECKOUT = "/api/v1/donations/checkout";
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
    void checkoutCreatesPendingDonationAndValidatesAmount() throws Exception {
        String token = registerAndGetToken(email("dn-checkout"));

        MvcResult result = mockMvc.perform(post(CHECKOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 5))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.provider").value("mock"))
                .andExpect(jsonPath("$.transactionId").isNotEmpty())
                .andExpect(jsonPath("$.checkoutUrl").isNotEmpty())
                .andExpect(jsonPath("$.amount").value(5))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andReturn();
        String transactionId = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("transactionId")
                .asText();

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT payment_type, status, currency, user_id IS NOT NULL AS has_user"
                        + " FROM payments WHERE transaction_id = ?",
                transactionId);
        assertThat(row)
                .containsEntry("payment_type", "donation")
                .containsEntry("status", "pending")
                .containsEntry("currency", "USD")
                .containsEntry("has_user", 1L);

        // 金额范围/小数位（Bean Validation → VALIDATION_ERROR 400）
        mockMvc.perform(post(CHECKOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 0.3))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(CHECKOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 20000))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(CHECKOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 3.333))))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post(CHECKOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("currency", "USD"))))
                .andExpect(status().isBadRequest());

        // 非本版币种 → DONATION_INVALID_AMOUNT；小写 usd 归一化后接受
        mockMvc.perform(post(CHECKOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 5, "currency", "EUR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DONATION_INVALID_AMOUNT"));
        mockMvc.perform(post(CHECKOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 3, "currency", "usd"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void mockCompleteIsIdempotentAndRejectsForeignPayment() throws Exception {
        String token = registerAndGetToken(email("dn-mock"));
        String other = registerAndGetToken(email("dn-mock-other"));
        String transactionId = checkout(token, 5);

        // 他人单据不可见（§65）
        mockMvc.perform(post(DONATIONS + "/mock-complete/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("PAYMENT_NOT_FOUND"));

        mockMvc.perform(post(DONATIONS + "/mock-complete/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"))
                .andExpect(jsonPath("$.amount").value(5))
                .andExpect(jsonPath("$.paidAt").isNotEmpty());

        // 幂等重放：仍 succeeded，不重复写单/日志
        mockMvc.perform(post(DONATIONS + "/mock-complete/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("succeeded"));
        Integer payments = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM payments WHERE transaction_id = ?",
                Integer.class,
                transactionId);
        assertThat(payments).isEqualTo(1);
        Integer processedLogs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_logs WHERE transaction_id = ? AND processed = 1",
                Integer.class,
                transactionId);
        assertThat(processedLogs).isEqualTo(1);
    }

    @Test
    void webhookProcessesDonationEventsEndToEnd() throws Exception {
        String token = registerAndGetToken(email("dn-webhook"));
        String transactionId = checkout(token, 10);
        String payload =
                "{\"provider\":\"mock\",\"eventType\":\"payment.succeeded\","
                        + "\"transactionId\":\"" + transactionId + "\",\"amount\":10,\"currency\":\"USD\"}";

        // 无签名 → 400（审计日志随事务提交，§67）
        mockMvc.perform(post(MOCK_WEBHOOK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WEBHOOK_INVALID_SIGNATURE"));

        // 垃圾回调（非 JSON 原文）：验签失败仍 400，审计日志包裹原文落库而非 500
        mockMvc.perform(post(MOCK_WEBHOOK)
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not-json-garbage"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("WEBHOOK_INVALID_SIGNATURE"));

        // 有效签名 → 200，donation 单经同一入口分发置 succeeded
        String signature = paymentProvider.signPayload(payload);
        mockMvc.perform(post(MOCK_WEBHOOK)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.received").value("true"));
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM payments WHERE transaction_id = ?",
                        String.class,
                        transactionId))
                .isEqualTo("succeeded");

        // 重放 → 幂等 200，仅一条 processed 日志
        mockMvc.perform(post(MOCK_WEBHOOK)
                        .header("X-Signature", signature)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isOk());
        Integer processedLogs = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM webhook_logs WHERE transaction_id = ? AND processed = 1",
                Integer.class,
                transactionId);
        assertThat(processedLogs).isEqualTo(1);

        // payment.failed 事件（另一单）→ 置 failed
        String failedTx = checkout(token, 20);
        String failedPayload =
                "{\"provider\":\"mock\",\"eventType\":\"payment.failed\","
                        + "\"transactionId\":\"" + failedTx + "\",\"amount\":20,\"currency\":\"USD\"}";
        mockMvc.perform(post(MOCK_WEBHOOK)
                        .header("X-Signature", paymentProvider.signPayload(failedPayload))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(failedPayload))
                .andExpect(status().isOk());
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT status FROM payments WHERE transaction_id = ?",
                        String.class,
                        failedTx))
                .isEqualTo("failed");
    }

    @Test
    void historyListsOnlyMyDonationsWithPagination() throws Exception {
        String token = registerAndGetToken(email("dn-history"));
        String other = registerAndGetToken(email("dn-history-other"));

        String tx1 = checkout(token, 3);
        String tx2 = checkout(token, 5);
        String tx3 = checkout(token, 10);
        mockComplete(token, tx1);
        mockComplete(token, tx2);
        checkout(other, 50);

        mockMvc.perform(get(DONATIONS).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(3))
                .andExpect(jsonPath("$.pagination.total").value(3))
                .andExpect(jsonPath("$.items[0].transactionId").value(tx3))
                .andExpect(jsonPath("$.items[0].status").value("pending"))
                .andExpect(jsonPath("$.items[1].transactionId").value(tx2))
                .andExpect(jsonPath("$.items[1].status").value("succeeded"))
                .andExpect(jsonPath("$.items[2].transactionId").value(tx1))
                .andExpect(jsonPath("$.items[2].status").value("succeeded"));

        mockMvc.perform(get(DONATIONS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .param("page", "1")
                        .param("pageSize", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.pagination.total").value(3))
                .andExpect(jsonPath("$.pagination.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].transactionId").value(tx3));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(CHECKOUT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", 5))))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(DONATIONS)).andExpect(status().isUnauthorized());
    }

    private String checkout(String token, double amount) throws Exception {
        MvcResult result = mockMvc.perform(post(CHECKOUT)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("amount", amount))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("transactionId")
                .asText();
    }

    private void mockComplete(String token, String transactionId) throws Exception {
        mockMvc.perform(post(DONATIONS + "/mock-complete/" + transactionId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email, "password", PASSWORD, "displayName", "Parent"))))
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
