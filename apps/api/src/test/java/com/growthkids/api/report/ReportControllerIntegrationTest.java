package com.growthkids.api.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.support.IntegrationTestCleaner;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
import org.springframework.test.web.servlet.ResultMatcher;

/**
 * 年度报告集成测试（TASK-018，规范 §98/§99/§110）：
 * Pro 权益门禁/年份与数据校验/异步生成状态机（pending→generating→ready）/
 * PDF 下载（magic bytes）/同年重生成幂等/未就绪与越权下载。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReportControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String CHILDREN = "/api/v1/children";
    private static final String SUBSCRIBE = "/api/v1/membership/subscribe";
    private static final String MEMBERSHIP = "/api/v1/membership";
    private static final String PASSWORD = "StrongPassword123!";

    // 样本年份：6 月及以后取当年（2 月/6 月样本日期必为过去），否则取去年
    private static final int YEAR =
            LocalDate.now(ZoneOffset.UTC).getMonthValue() >= 6
                    ? LocalDate.now(ZoneOffset.UTC).getYear()
                    : LocalDate.now(ZoneOffset.UTC).getYear() - 1;
    private static final LocalDate D1 = LocalDate.of(YEAR, 2, 15);
    private static final LocalDate D2 = LocalDate.of(YEAR, 6, 10);

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void cleanUp() {
        IntegrationTestCleaner.cleanUp(jdbcTemplate);
    }

    @Test
    void freeUserCannotCreateReport() throws Exception {
        String token = registerAndGetToken(email("rp-free"));
        long childId = createChild(token);
        createRecord(token, childId, D1, 92.5, 13.8, 49.5);

        // §64：年度报告为 Pro 权益（权限门禁优先于数据检查）
        mockMvc.perform(post(reportsPath(childId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("year", YEAR))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("MEMBERSHIP_REQUIRED"));
    }

    @Test
    void createValidatesYearAndData() throws Exception {
        String token = registerAndGetToken(email("rp-validate"));
        subscribeAndComplete(token);
        long childId = createChild(token);
        createRecord(token, childId, D1, 92.5, 13.8, 49.5);

        // 年份越界（§98：year 1900-2100）
        mockMvc.perform(post(reportsPath(childId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("year", 1899))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
        mockMvc.perform(post(reportsPath(childId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("year", 2101))))
                .andExpect(status().isBadRequest());

        // 年份合法但当年无记录（§98）
        mockMvc.perform(post(reportsPath(childId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("year", YEAR + 4))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REPORT_NO_DATA"));

        // 他人孩子 404（other 为 Free 时先撞权限门禁 403，升级后才能触达归属检查）
        String other = registerAndGetToken(email("rp-validate-other"));
        subscribeAndComplete(other);
        mockMvc.perform(post(reportsPath(childId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("year", YEAR))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));

        // 列表需认证；本人列表为空
        mockMvc.perform(get(reportsPath(childId))).andExpect(status().isUnauthorized());
        mockMvc.perform(get(reportsPath(childId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(0));
    }

    @Test
    void fullLifecycleGeneratesPdfAndDownloads() throws Exception {
        String token = registerAndGetToken(email("rp-lifecycle"));
        subscribeAndComplete(token);
        long childId = createChild(token);
        createRecord(token, childId, D1, 92.5, 13.8, 49.5);
        createRecord(token, childId, D2, 95.5, 14.6, 49.8);
        // 成长故事 + 照片（文件不存在 → 报告跳过嵌入但不失败）
        jdbcTemplate.update(
                "INSERT INTO growth_memories (child_id, memory_date, title, content) VALUES (?, ?, ?, ?)",
                childId,
                D2,
                "第一次自己穿衣服",
                "小明坚持自己扣扣子，虽然花了十分钟，但完成后特别自豪。");
        jdbcTemplate.update(
                "INSERT INTO growth_photos (child_id, storage_key, original_filename, mime_type,"
                        + " file_size, caption, processing_status) VALUES (?, ?, ?, 'image/jpeg', 1024, ?, 'ready')",
                childId,
                "it/" + runId + "/report-photo.jpg",
                "report-photo.jpg",
                "公园放风筝");

        MvcResult created = mockMvc.perform(post(reportsPath(childId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("year", YEAR))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportType").value("annual"))
                .andExpect(jsonPath("$.year").value(YEAR))
                .andExpect(jsonPath("$.status").value("pending"))
                .andReturn();
        long reportId = objectMapper
                .readTree(created.getResponse().getContentAsString())
                .get("id")
                .asLong();

        // §99：异步生成（pending→generating→ready），轮询终态
        Assertions.assertEquals("ready", awaitTerminalStatus(reportId), "报告应生成成功");
        String storageKey = jdbcTemplate.queryForObject(
                "SELECT storage_key FROM growth_reports WHERE id = ?", String.class, reportId);
        assertThat(storageKey).startsWith("reports/").endsWith(".pdf");

        MvcResult download = mockMvc.perform(get("/api/v1/reports/" + reportId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(contentTypePdf())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                org.hamcrest.Matchers.containsString("attachment")))
                .andReturn();
        byte[] pdf = download.getResponse().getContentAsByteArray();
        assertThat(pdf.length).isGreaterThan(1000);
        assertThat(new String(pdf, 0, 4, StandardCharsets.US_ASCII)).isEqualTo("%PDF");

        // 列表返回 ready 状态
        mockMvc.perform(get(reportsPath(childId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].status").value("ready"));
    }

    @Test
    void regenerateSameYearIsIdempotent() throws Exception {
        String token = registerAndGetToken(email("rp-regen"));
        subscribeAndComplete(token);
        long childId = createChild(token);
        createRecord(token, childId, D1, 92.5, 13.8, 49.5);

        mockMvc.perform(post(reportsPath(childId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("year", YEAR))))
                .andExpect(status().isCreated());
        long reportId = jdbcTemplate.queryForObject(
                "SELECT id FROM growth_reports WHERE child_id = ? AND year = ?",
                Long.class,
                childId,
                YEAR);
        Assertions.assertEquals("ready", awaitTerminalStatus(reportId));

        // 数据更新后同年重生成：复用同一行（uq_child_report），状态重置再走异步
        createRecord(token, childId, D2, 95.5, 14.6, 49.8);
        mockMvc.perform(post(reportsPath(childId))
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("year", YEAR))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(reportId))
                .andExpect(jsonPath("$.status").value("pending"));
        Assertions.assertEquals("ready", awaitTerminalStatus(reportId));

        Integer rows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_reports WHERE child_id = ? AND year = ?",
                Integer.class,
                childId,
                YEAR);
        Assertions.assertEquals(1, rows, "同年重生成应复用同一行");
        mockMvc.perform(get("/api/v1/reports/" + reportId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
    }

    @Test
    void downloadRejectsNotReadyAndForeignReport() throws Exception {
        String owner = registerAndGetToken(email("rp-dl-owner"));
        long ownerUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email("rp-dl-owner"));
        long childId = createChild(owner);

        // 直插 pending 行（不触发异步，时序确定）：未 ready 下载 409
        jdbcTemplate.update(
                "INSERT INTO growth_reports (child_id, user_id, report_type, year, status)"
                        + " VALUES (?, ?, 'annual', ?, 'pending')",
                childId,
                ownerUserId,
                YEAR);
        long pendingId = jdbcTemplate.queryForObject(
                "SELECT id FROM growth_reports WHERE child_id = ? AND status = 'pending'",
                Long.class,
                childId);
        mockMvc.perform(get("/api/v1/reports/" + pendingId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_READY"));

        // 直插 ready 行（他人可寻址）：非本人 404（不泄露存在性）
        jdbcTemplate.update(
                "INSERT INTO growth_reports (child_id, user_id, report_type, year, status, storage_key)"
                        + " VALUES (?, ?, 'annual', ?, 'ready', ?)",
                childId,
                ownerUserId,
                YEAR - 1,
                "it/" + runId + "/foreign.pdf");
        long readyId = jdbcTemplate.queryForObject(
                "SELECT id FROM growth_reports WHERE child_id = ? AND status = 'ready'",
                Long.class,
                childId);
        String other = registerAndGetToken(email("rp-dl-other"));
        mockMvc.perform(get("/api/v1/reports/" + readyId + "/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("REPORT_NOT_FOUND"));

        // 不存在的报告 404；下载需认证
        mockMvc.perform(get("/api/v1/reports/999999/download")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/reports/" + readyId + "/download"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    /** 轮询异步生成终态（ready/failed），最多 20s（首次含字体解包）。 */
    private String awaitTerminalStatus(long reportId) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 20_000;
        String status = null;
        while (System.currentTimeMillis() < deadline) {
            status = jdbcTemplate.queryForObject(
                    "SELECT status FROM growth_reports WHERE id = ?", String.class, reportId);
            if ("ready".equals(status) || "failed".equals(status)) {
                return status;
            }
            Thread.sleep(200);
        }
        return status;
    }

    private static ResultMatcher contentTypePdf() {
        return org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                .contentType(MediaType.APPLICATION_PDF);
    }

    private static String reportsPath(long childId) {
        return CHILDREN + "/" + childId + "/reports";
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

    private long createChild(String token) throws Exception {
        MvcResult result = mockMvc.perform(post(CHILDREN)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "name", "小明",
                                "gender", "male",
                                "birthDate", "2023-03-10",
                                "fatherHeightCm", 175,
                                "motherHeightCm", 162))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asLong();
    }

    private void createRecord(
            String token, long childId, LocalDate date, double height, double weight, double head)
            throws Exception {
        mockMvc.perform(post(CHILDREN + "/" + childId + "/growth-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "recordDate", date.toString(),
                                "heightCm", height,
                                "weightKg", weight,
                                "headCircumferenceCm", head))))
                .andExpect(status().isCreated());
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
