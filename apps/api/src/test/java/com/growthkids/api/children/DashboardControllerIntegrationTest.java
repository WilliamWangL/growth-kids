package com.growthkids.api.children;

import static org.hamcrest.Matchers.closeTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.support.IntegrationTestCleaner;
import com.growthkids.engine.age.AgeCalculator;
import com.growthkids.engine.age.AgeResult;
import java.time.LocalDate;
import java.time.ZoneOffset;
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
 * Dashboard API 集成测试（TASK-011，规范 §54/§112）：
 * 孩子信息 + 当前年龄（今天基准）+ 最近一次成长记录；ownership 校验 §70。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardControllerIntegrationTest {

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
    void returnsChildSummaryWithoutRecords() throws Exception {
        String token = registerAndGetToken(email("dash-empty"));
        LocalDate birth = LocalDate.now(ZoneOffset.UTC).minusMonths(19);
        long childId = createChild(token, "Tom", birth.toString());

        MvcResult result =
                mockMvc.perform(
                                get(dashboardPath(childId))
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.child.id").value(String.valueOf(childId)))
                        .andExpect(jsonPath("$.child.name").value("Tom"))
                        .andExpect(jsonPath("$.child.birthDate").value(birth.toString()))
                        .andExpect(jsonPath("$.age.years").value(1))
                        // 无成长记录时 latestRecord 为 null（JSON 省略，规范 §40 惯例）
                        .andExpect(jsonPath("$.latestRecord").doesNotExist())
                        .andReturn();

        JsonNode age =
                objectMapper.readTree(result.getResponse().getContentAsString()).get("age");
        AgeResult expected = AgeCalculator.calculate(birth, LocalDate.now(ZoneOffset.UTC));
        assertEquals(expected.totalMonths(), age.get("totalMonths").asInt());
        assertEquals(expected.months(), age.get("months").asInt());
        assertEquals(expected.days(), age.get("days").asInt());
    }

    @Test
    void returnsLatestRecordOrderedByDate() throws Exception {
        String token = registerAndGetToken(email("dash-latest"));
        long childId = createChild(token, "Tom", "2025-01-01");
        createRecord(token, childId, Map.of("recordDate", "2026-07-01", "heightCm", 78));
        createRecord(token, childId, Map.of("recordDate", "2026-08-15", "heightCm", 80));
        createRecord(
                token,
                childId,
                Map.of(
                        "recordDate", "2026-09-15",
                        "heightCm", 82,
                        "weightKg", 11.7,
                        "headCircumferenceCm", 47.5));

        mockMvc.perform(
                        get(dashboardPath(childId)).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.latestRecord.recordDate").value("2026-09-15"))
                .andExpect(jsonPath("$.latestRecord.heightCm").value(82))
                .andExpect(jsonPath("$.latestRecord.weightKg").value(11.7))
                .andExpect(jsonPath("$.latestRecord.headCircumferenceCm").value(47.5))
                .andExpect(jsonPath("$.latestRecord.bmi").value(17.4))
                // 记录年龄（记录日基准，非今天）：622 天 = 1y8m14d / 20 个月
                .andExpect(jsonPath("$.latestRecord.age.totalDays").value(622))
                .andExpect(jsonPath("$.latestRecord.age.totalMonths").value(20))
                // WHO 指标（TASK-009）：male 622 天取 548 锚点
                .andExpect(jsonPath("$.latestRecord.whoVersion").value("2026.1-example"))
                .andExpect(jsonPath("$.latestRecord.heightZ").value(closeTo(-2.3452, 0.001)))
                .andExpect(
                        jsonPath("$.latestRecord.heightPercentile")
                                .value(closeTo(0.9508, 0.001)));
    }

    @Test
    void otherUserChildReturnsNotFound() throws Exception {
        String tokenA = registerAndGetToken(email("dash-owner"));
        long childId = createChild(tokenA, "Tom", "2025-01-01");
        String tokenB = registerAndGetToken(email("dash-intruder"));

        mockMvc.perform(
                        get(dashboardPath(childId)).header(HttpHeaders.AUTHORIZATION, bearer(tokenB)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(dashboardPath(1))).andExpect(status().isUnauthorized());
    }

    private String dashboardPath(long childId) {
        return CHILDREN + "/" + childId + "/dashboard";
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

    private void createRecord(String token, long childId, Map<String, Object> body) throws Exception {
        mockMvc.perform(
                        post(CHILDREN + "/" + childId + "/growth-records")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(body)))
                .andExpect(status().isCreated());
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
