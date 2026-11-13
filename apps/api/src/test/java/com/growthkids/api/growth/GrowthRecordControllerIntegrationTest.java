package com.growthkids.api.growth;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.support.IntegrationTestCleaner;
import java.time.LocalDate;
import java.util.HashMap;
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
 * Growth Record API 集成测试（TASK-007：CRUD + age/BMI 派生计算 + ownership）。
 *
 * <p>基准数据：child birthDate=2025-01-01；recordDate=2026-09-15 时 age
 * = 1y8m14d / totalDays=622 / totalMonths=20；82cm+11.7kg → BMI=17.400。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GrowthRecordControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String CHILDREN = "/api/v1/children";
    private static final String GROWTH_RECORDS = "/api/v1/growth-records";
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void cleanUp() {
        IntegrationTestCleaner.cleanUp(jdbcTemplate);
        // 其他套件（WhoDataImporterIntegrationTest）导入的测试数据集若残留会改变 active dataset，
        // 清理以保证 WHO 指标按内置示例数据（2026.1-example）计算
        jdbcTemplate.update(
                "DELETE FROM who_growth_standards WHERE source IN ('it-who-source', 'it-who-bad')");
    }

    @Test
    void createComputesAgeAndBmi() throws Exception {
        String token = registerAndGetToken(email("gr-create"));
        long childId = createChild(token, "Tom", "2025-01-01");
        mockMvc.perform(
                        post(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                Map.of(
                                                        "recordDate", "2026-09-15",
                                                        "heightCm", 82,
                                                        "weightKg", 11.7,
                                                        "headCircumferenceCm", 47.5,
                                                        "note", "最近开始喜欢跑步"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.childId").value(String.valueOf(childId)))
                .andExpect(jsonPath("$.recordDate").value("2026-09-15"))
                .andExpect(jsonPath("$.age.years").value(1))
                .andExpect(jsonPath("$.age.months").value(8))
                .andExpect(jsonPath("$.age.days").value(14))
                .andExpect(jsonPath("$.age.totalMonths").value(20))
                .andExpect(jsonPath("$.age.totalDays").value(622))
                .andExpect(jsonPath("$.bmi").value(17.4))
                .andExpect(jsonPath("$.note").value("最近开始喜欢跑步"))
                // WHO 指标（TASK-009）：male 622 天取 548 天锚点，z/percentile 为确定性值
                .andExpect(jsonPath("$.whoVersion").value("2026.1-example"))
                .andExpect(jsonPath("$.heightZ").value(closeTo(-2.3452, 0.001)))
                .andExpect(jsonPath("$.heightPercentile").value(closeTo(0.9508, 0.001)))
                .andExpect(jsonPath("$.weightZ").value(closeTo(-0.8718, 0.001)))
                .andExpect(jsonPath("$.weightPercentile").value(closeTo(19.1659, 0.001)))
                .andExpect(jsonPath("$.bmiZ").value(closeTo(2.9850, 0.001)))
                .andExpect(jsonPath("$.bmiPercentile").value(closeTo(99.8582, 0.001)))
                .andExpect(jsonPath("$.headZ").value(closeTo(1.8599, 0.001)))
                .andExpect(jsonPath("$.headPercentile").value(closeTo(96.8550, 0.001)));

        // 库中 age_days 与响应 totalDays 一致（WHO 匹配基准，规范 §35）
        Integer ageDays =
                jdbcTemplate.queryForObject(
                        "SELECT age_days FROM growth_records WHERE child_id = ?", Integer.class, childId);
        assertEquals(622, ageDays);
    }

    @Test
    void createWithUnknownGenderSkipsWhoMetrics() throws Exception {
        String token = registerAndGetToken(email("gr-unknown-gender"));
        long childId = createChild(token, "Sam", "2025-01-01", "unknown");
        mockMvc.perform(
                        post(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", "2026-09-15", "heightCm", 82))))
                .andExpect(status().isCreated())
                // §35-1 精确匹配 sex：unknown 不参与 WHO 匹配 → z/percentile/whoVersion 为 null（不输出）
                .andExpect(jsonPath("$.whoVersion").doesNotExist())
                .andExpect(jsonPath("$.heightZ").doesNotExist());
    }

    @Test
    void createRejectsFutureRecordDate() throws Exception {
        String token = registerAndGetToken(email("gr-future"));
        long childId = createChild(token, "Tom", "2025-01-01");
        String future = LocalDate.now().plusDays(1).toString();
        mockMvc.perform(
                        post(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", future, "heightCm", 82))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GROWTH_RECORD_INVALID_DATA"))
                .andExpect(jsonPath("$.error.details.recordDate").value(not(emptyOrNullString())));
    }

    @Test
    void createRejectsRecordDateBeforeBirthDate() throws Exception {
        String token = registerAndGetToken(email("gr-before-birth"));
        long childId = createChild(token, "Tom", "2025-01-01");
        mockMvc.perform(
                        post(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", "2024-12-31", "heightCm", 50))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GROWTH_RECORD_INVALID_DATA"))
                .andExpect(jsonPath("$.error.details.recordDate").value(not(emptyOrNullString())));
    }

    @Test
    void createRejectsMissingMeasurements() throws Exception {
        String token = registerAndGetToken(email("gr-no-measure"));
        long childId = createChild(token, "Tom", "2025-01-01");
        mockMvc.perform(
                        post(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", "2026-09-15", "note", "only note"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GROWTH_RECORD_INVALID_DATA"));
    }

    @Test
    void createRejectsInvalidHeightAndWeight() throws Exception {
        String token = registerAndGetToken(email("gr-invalid-values"));
        long childId = createChild(token, "Tom", "2025-01-01");
        mockMvc.perform(
                        post(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", "2026-09-15", "heightCm", 300))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GROWTH_RECORD_INVALID_HEIGHT"));
        mockMvc.perform(
                        post(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", "2026-09-15", "weightKg", 999))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("GROWTH_RECORD_INVALID_WEIGHT"));
    }

    @Test
    void createDuplicatesDateReturns409() throws Exception {
        String token = registerAndGetToken(email("gr-dup"));
        long childId = createChild(token, "Tom", "2025-01-01");
        createRecord(token, childId, Map.of("recordDate", "2026-09-15", "heightCm", 82));
        mockMvc.perform(
                        post(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", "2026-09-15", "weightKg", 11.7))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROWTH_RECORD_DUPLICATE_DATE"));
    }

    @Test
    void listFiltersByDateRangeAndOrdersDesc() throws Exception {
        String token = registerAndGetToken(email("gr-list"));
        long childId = createChild(token, "Tom", "2025-01-01");
        createRecord(token, childId, Map.of("recordDate", "2026-06-01", "heightCm", 79));
        createRecord(token, childId, Map.of("recordDate", "2026-07-01", "heightCm", 80));
        createRecord(token, childId, Map.of("recordDate", "2026-08-01", "heightCm", 81));

        mockMvc.perform(get(recordsPath(childId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(3)))
                .andExpect(jsonPath("$.items[0].recordDate").value("2026-08-01"))
                .andExpect(jsonPath("$.items[2].recordDate").value("2026-06-01"))
                .andExpect(jsonPath("$.pagination.total").value(3));

        mockMvc.perform(
                        get(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .param("startDate", "2026-07-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.items[1].recordDate").value("2026-07-01"));

        mockMvc.perform(
                        get(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .param("startDate", "2026-06-01")
                                .param("endDate", "2026-06-30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].recordDate").value("2026-06-01"));
    }

    @Test
    void updateRecomputesAgeAndBmi() throws Exception {
        String token = registerAndGetToken(email("gr-update"));
        long childId = createChild(token, "Tom", "2025-01-01");
        long recordId =
                createRecord(
                        token,
                        childId,
                        Map.of("recordDate", "2026-09-15", "heightCm", 82, "weightKg", 11.7));

        Map<String, Object> patchBody = new HashMap<>();
        patchBody.put("recordDate", "2026-08-01");
        patchBody.put("heightCm", 80);
        mockMvc.perform(
                        patch(GROWTH_RECORDS + "/" + recordId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(patchBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordDate").value("2026-08-01"))
                // 2025-01-01 → 2026-08-01 = 577 天
                .andExpect(jsonPath("$.age.totalDays").value(577))
                // 11.7 / 0.8² = 18.28125 → 18.281
                .andExpect(jsonPath("$.bmi").value(18.281))
                // 未提供的字段保持不变
                .andExpect(jsonPath("$.weightKg").value(11.7))
                // 日期/身高变化 → WHO 指标重算（锚点仍为 548 天）
                .andExpect(jsonPath("$.whoVersion").value("2026.1-example"))
                .andExpect(jsonPath("$.heightZ").value(closeTo(-2.8977, 0.001)))
                .andExpect(jsonPath("$.heightPercentile").value(closeTo(0.1880, 0.001)))
                .andExpect(jsonPath("$.weightZ").value(closeTo(-0.8718, 0.001)))
                .andExpect(jsonPath("$.bmiZ").value(closeTo(3.5221, 0.001)))
                .andExpect(jsonPath("$.bmiPercentile").value(closeTo(99.9786, 0.001)));

        mockMvc.perform(get(recordsPath(childId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].age.totalDays").value(577));
    }

    @Test
    void updateToDuplicateDateReturns409() throws Exception {
        String token = registerAndGetToken(email("gr-update-dup"));
        long childId = createChild(token, "Tom", "2025-01-01");
        createRecord(token, childId, Map.of("recordDate", "2026-06-01", "heightCm", 79));
        long recordId =
                createRecord(token, childId, Map.of("recordDate", "2026-07-01", "heightCm", 80));

        mockMvc.perform(
                        patch(GROWTH_RECORDS + "/" + recordId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", "2026-06-01"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("GROWTH_RECORD_DUPLICATE_DATE"));
    }

    @Test
    void deleteRemovesRecordPhysically() throws Exception {
        String token = registerAndGetToken(email("gr-delete"));
        long childId = createChild(token, "Tom", "2025-01-01");
        long recordId =
                createRecord(token, childId, Map.of("recordDate", "2026-09-15", "heightCm", 82));

        mockMvc.perform(
                        delete(GROWTH_RECORDS + "/" + recordId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        Integer count =
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM growth_records WHERE id = ?", Integer.class, recordId);
        assertEquals(0, count);

        mockMvc.perform(
                        patch(GROWTH_RECORDS + "/" + recordId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("heightCm", 83))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROWTH_RECORD_NOT_FOUND"));
    }

    @Test
    void operationsAreOwnershipIsolated() throws Exception {
        String tokenA = registerAndGetToken(email("gr-iso-a"));
        String tokenB = registerAndGetToken(email("gr-iso-b"));
        long childId = createChild(tokenA, "Tom", "2025-01-01");
        long recordId =
                createRecord(tokenA, childId, Map.of("recordDate", "2026-09-15", "heightCm", 82));

        // B 访问 A 的 child 记录（列表/创建）→ 404，不泄露存在性
        mockMvc.perform(get(recordsPath(childId)).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));
        mockMvc.perform(
                        post(recordsPath(childId))
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", "2026-08-01", "heightCm", 80))))
                .andExpect(status().isNotFound());

        // B 修改/删除 A 的记录 → 404
        mockMvc.perform(
                        patch(GROWTH_RECORDS + "/" + recordId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("heightCm", 99))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GROWTH_RECORD_NOT_FOUND"));
        mockMvc.perform(
                        delete(GROWTH_RECORDS + "/" + recordId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound());
    }

    @Test
    void createOnUnknownChildReturns404() throws Exception {
        String token = registerAndGetToken(email("gr-unknown-child"));
        mockMvc.perform(
                        post(recordsPath(999_999_999L))
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", "2026-09-15", "heightCm", 82))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));
    }

    @Test
    void childBirthDateChangeRecomputesRecordAge() throws Exception {
        String token = registerAndGetToken(email("gr-recompute"));
        long childId = createChild(token, "Tom", "2025-01-01");
        long recordId =
                createRecord(token, childId, Map.of("recordDate", "2026-09-15", "heightCm", 82));

        // 出生日期改为 2025-06-01 → 该记录年龄重算：2025-06-01 → 2026-09-15 = 471 天
        mockMvc.perform(
                        patch(CHILDREN + "/" + childId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("birthDate", "2025-06-01"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value("2025-06-01"));

        Integer ageDays =
                jdbcTemplate.queryForObject(
                        "SELECT age_days FROM growth_records WHERE id = ?", Integer.class, recordId);
        assertEquals(471, ageDays);

        // WHO 指标按新年龄（锚点 457 天）重算
        mockMvc.perform(get(recordsPath(childId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].age.totalDays").value(471))
                .andExpect(jsonPath("$.items[0].whoVersion").value("2026.1-example"))
                .andExpect(jsonPath("$.items[0].heightZ").value(closeTo(-1.2908, 0.001)))
                .andExpect(jsonPath("$.items[0].heightPercentile").value(closeTo(9.8387, 0.001)));
    }

    @Test
    void childBirthDateChangeConflictingWithRecordsReturns400() throws Exception {
        String token = registerAndGetToken(email("gr-recompute-conflict"));
        long childId = createChild(token, "Tom", "2025-01-01");
        createRecord(token, childId, Map.of("recordDate", "2026-09-15", "heightCm", 82));

        // 新生日 2026-10-01 晚于已有记录日期 → 拒绝（否则年龄为负）
        mockMvc.perform(
                        patch(CHILDREN + "/" + childId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("birthDate", "2026-10-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CHILD_INVALID_DATA"))
                .andExpect(jsonPath("$.error.details.birthDate").value(not(emptyOrNullString())));

        // 原出生日期保持不变（事务回滚）
        mockMvc.perform(get(CHILDREN + "/" + childId).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.birthDate").value("2025-01-01"));
    }

    private String recordsPath(long childId) {
        return CHILDREN + "/" + childId + "/growth-records";
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long createChild(String token, String name, String birthDate) throws Exception {
        return createChild(token, name, birthDate, "male");
    }

    private long createChild(String token, String name, String birthDate, String gender)
            throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(CHILDREN)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json(
                                                        Map.of(
                                                                "name", name,
                                                                "gender", gender,
                                                                "birthDate", birthDate))))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private long createRecord(String token, long childId, Map<String, Object> body) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(recordsPath(childId))
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(body)))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode response = objectMapper.readTree(result.getResponse().getContentAsString());
        return response.get("id").asLong();
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(REGISTER)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json(
                                                        Map.of(
                                                                "email", email,
                                                                "password", PASSWORD,
                                                                "displayName", "Parent"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String email(String tag) {
        return "it-" + runId + "-" + tag + "@test.dev";
    }
}
