package com.growthkids.api.growth;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.support.IntegrationTestCleaner;
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
 * Growth Chart API 集成测试（TASK-012，规范 §33/§58）。
 *
 * <p>基准数据：child male birthDate=2025-01-01；三条记录
 * （2026-03-15 / 2026-06-15 / 2026-09-15 → ageMonths 14/17/20），
 * 第二条不填头围 → headCircumference 序列仅 2 点。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GrowthChartControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String CHILDREN = "/api/v1/children";
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void cleanUp() {
        IntegrationTestCleaner.cleanUp(jdbcTemplate);
        jdbcTemplate.update(
                "DELETE FROM who_growth_standards WHERE source IN ('it-who-source', 'it-who-bad')");
    }

    @Test
    void returnsSeriesGroupedByMetric() throws Exception {
        String token = registerAndGetToken(email("chart-series"));
        long childId = createChild(token, "Tom", "2025-01-01");
        JsonNode r1 = createRecord(token, childId, Map.of(
                "recordDate", "2026-03-15", "heightCm", 78, "weightKg", 10.2,
                "headCircumferenceCm", 46));
        createRecord(token, childId, Map.of("recordDate", "2026-06-15", "heightCm", 80,
                "weightKg", 10.9));
        createRecord(token, childId, Map.of("recordDate", "2026-09-15", "heightCm", 82,
                "weightKg", 11.7, "headCircumferenceCm", 47.5));

        mockMvc.perform(get(CHILDREN + "/" + childId + "/growth-chart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                // 身高序列：3 点升序，ageMonths 按实际日期计算（§37）
                .andExpect(jsonPath("$.height.records", hasSize(3)))
                .andExpect(jsonPath("$.height.records[0].date").value("2026-03-15"))
                .andExpect(jsonPath("$.height.records[0].ageMonths").value(14))
                .andExpect(jsonPath("$.height.records[0].value").value(78.0))
                .andExpect(jsonPath("$.height.records[0].percentile")
                        .value(r1.get("heightPercentile").asDouble()))
                .andExpect(jsonPath("$.height.records[1].date").value("2026-06-15"))
                .andExpect(jsonPath("$.height.records[1].ageMonths").value(17))
                .andExpect(jsonPath("$.height.records[1].value").value(80.0))
                .andExpect(jsonPath("$.height.records[2].date").value("2026-09-15"))
                .andExpect(jsonPath("$.height.records[2].ageMonths").value(20))
                .andExpect(jsonPath("$.height.records[2].value").value(82.0))
                // 体重序列：3 点
                .andExpect(jsonPath("$.weight.records", hasSize(3)))
                .andExpect(jsonPath("$.weight.records[0].value").value(10.2))
                .andExpect(jsonPath("$.weight.records[1].value").value(10.9))
                .andExpect(jsonPath("$.weight.records[2].value").value(11.7))
                // BMI 序列：3 条记录均有身高+体重 → 派生 BMI（82/11.7 → 17.400）
                .andExpect(jsonPath("$.bmi.records", hasSize(3)))
                .andExpect(jsonPath("$.bmi.records[2].value", closeTo(17.4, 0.001)))
                // 头围序列：第二条未填 → 仅 2 点（缺测量不产生点，§58）
                .andExpect(jsonPath("$.headCircumference.records", hasSize(2)))
                .andExpect(jsonPath("$.headCircumference.records[0].value").value(46.0))
                .andExpect(jsonPath("$.headCircumference.records[1].date").value("2026-09-15"))
                .andExpect(jsonPath("$.headCircumference.records[1].value").value(47.5));
    }

    @Test
    void returnsEmptySeriesWhenNoRecords() throws Exception {
        String token = registerAndGetToken(email("chart-empty"));
        long childId = createChild(token, "Empty", "2025-01-01");

        mockMvc.perform(get(CHILDREN + "/" + childId + "/growth-chart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.height.records").isEmpty())
                .andExpect(jsonPath("$.weight.records").isEmpty())
                .andExpect(jsonPath("$.bmi.records").isEmpty())
                .andExpect(jsonPath("$.headCircumference.records").isEmpty());
    }

    @Test
    void otherUserChildReturnsNotFound() throws Exception {
        String owner = registerAndGetToken(email("chart-owner"));
        long childId = createChild(owner, "Tom", "2025-01-01");
        String other = registerAndGetToken(email("chart-other"));

        mockMvc.perform(get(CHILDREN + "/" + childId + "/growth-chart")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(CHILDREN + "/1/growth-chart"))
                .andExpect(status().isUnauthorized());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long createChild(String token, String name, String birthDate) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(CHILDREN)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json(
                                                        Map.of(
                                                                "name", name,
                                                                "gender", "male",
                                                                "birthDate", birthDate))))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private JsonNode createRecord(String token, long childId, Map<String, Object> body)
            throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(CHILDREN + "/" + childId + "/growth-records")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(new HashMap<>(body))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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

