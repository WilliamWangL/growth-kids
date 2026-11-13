package com.growthkids.api.prediction;

import static org.assertj.core.api.Assertions.assertThat;
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
 * Prediction API 集成测试（TASK-021，规范 §38-§45/§75）。
 *
 * <p>基准数据：男，birthDate=2024-01-01（今日约 34 月龄，落在 WHO 示例数据 0-60 月区间内），
 * 当前身高 92cm，父母 180/165cm。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PredictionControllerIntegrationTest {

    private static final String ESTIMATE = "/api/v1/prediction/estimate";
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
    }

    @Test
    void publicEstimateWorksWithoutLogin() throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(ESTIMATE)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(publicRequest())))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.currentAgeMonths").value(34))
                        .andExpect(jsonPath("$.currentHeightCm").value(92.0))
                        // 单条测量 → 无生长速度因子，置信度 low（§43）
                        .andExpect(jsonPath("$.confidence").value("low"))
                        // 中靶身高 (180+165+13)/2 = 179.0（§41）
                        .andExpect(jsonPath("$.midParentalHeightCm").value(179.0))
                        // WHO 示例数据覆盖 34 月龄 → 百分位可得
                        .andExpect(jsonPath("$.currentPercentile").isNumber())
                        // 逐年预测 6-18 岁共 13 点（§44）
                        .andExpect(jsonPath("$.yearlyPredictions", hasSize(13)))
                        .andExpect(jsonPath("$.yearlyPredictions[0].age").value(6))
                        .andExpect(jsonPath("$.yearlyPredictions[12].age").value(18))
                        .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        double predicted = body.get("predictedAdultHeightCm").asDouble();
        double lower = body.get("lowerRangeCm").asDouble();
        double upper = body.get("upperRangeCm").asDouble();
        assertThat(predicted).isBetween(150.0, 210.0);
        assertThat(lower).isLessThan(predicted);
        assertThat(upper).isGreaterThan(predicted);
        double percentile = body.get("currentPercentile").asDouble();
        assertThat(percentile).isBetween(0.0, 100.0);
    }

    @Test
    void publicEstimateWithoutParentsFallsBackToOtherFactors() throws Exception {
        Map<String, Object> request = new HashMap<>(publicRequest());
        request.remove("fatherHeightCm");
        request.remove("motherHeightCm");

        mockMvc.perform(
                        post(ESTIMATE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(request)))
                .andExpect(status().isOk())
                // 父母身高缺失 → 中靶身高字段省略
                .andExpect(jsonPath("$.midParentalHeightCm").doesNotExist())
                .andExpect(jsonPath("$.predictedAdultHeightCm").isNumber());
    }

    @Test
    void rejectsInvalidGenderAndHeight() throws Exception {
        Map<String, Object> badGender = new HashMap<>(publicRequest());
        badGender.put("gender", "unknown");
        mockMvc.perform(
                        post(ESTIMATE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(badGender)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.gender").exists());

        Map<String, Object> badHeight = new HashMap<>(publicRequest());
        badHeight.put("heightCm", 10);
        mockMvc.perform(
                        post(ESTIMATE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(badHeight)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.heightCm").exists());
    }

    @Test
    void rejectsFutureBirthDate() throws Exception {
        Map<String, Object> request = new HashMap<>(publicRequest());
        request.put("birthDate", "2099-01-01");

        mockMvc.perform(
                        post(ESTIMATE)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.birthDate").exists());
    }

    @Test
    void childPredictionUsesRecordedHeightSeries() throws Exception {
        String token = registerAndGetToken(email("pred-series"));
        long childId = createChild(token, "male", "2024-01-01");
        createRecord(token, childId, "2025-06-01", 85);
        createRecord(token, childId, "2026-06-01", 95);

        mockMvc.perform(
                        get(CHILDREN + "/" + childId + "/prediction")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentHeightCm").value(95.0))
                // 跨度 ≥ 90 天 → medium（父母身高未填，达不到 high）
                .andExpect(jsonPath("$.confidence").value("medium"))
                .andExpect(jsonPath("$.growthVelocityCmPerYear").isNumber())
                .andExpect(jsonPath("$.predictedAdultHeightCm").isNumber())
                .andExpect(jsonPath("$.yearlyPredictions", hasSize(13)));
    }

    @Test
    void childPredictionWithoutHeightRecordsIsRejected() throws Exception {
        String token = registerAndGetToken(email("pred-empty"));
        long childId = createChild(token, "male", "2024-01-01");

        mockMvc.perform(
                        get(CHILDREN + "/" + childId + "/prediction")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PREDICTION_INSUFFICIENT_DATA"));
    }

    @Test
    void childPredictionWithUnknownGenderIsRejected() throws Exception {
        String token = registerAndGetToken(email("pred-unknown"));
        long childId = createChild(token, "unknown", "2024-01-01");
        createRecord(token, childId, "2026-06-01", 95);

        mockMvc.perform(
                        get(CHILDREN + "/" + childId + "/prediction")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PREDICTION_INSUFFICIENT_DATA"));
    }

    @Test
    void otherUserChildReturnsNotFound() throws Exception {
        String owner = registerAndGetToken(email("pred-owner"));
        long childId = createChild(owner, "male", "2024-01-01");
        createRecord(owner, childId, "2026-06-01", 95);
        String other = registerAndGetToken(email("pred-other"));

        mockMvc.perform(
                        get(CHILDREN + "/" + childId + "/prediction")
                                .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));
    }

    @Test
    void childPredictionRequiresAuthentication() throws Exception {
        mockMvc.perform(get(CHILDREN + "/1/prediction")).andExpect(status().isUnauthorized());
    }

    private Map<String, Object> publicRequest() {
        return Map.of(
                "gender", "male",
                "birthDate", "2024-01-01",
                "heightCm", 92,
                "fatherHeightCm", 180,
                "motherHeightCm", 165);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long createChild(String token, String gender, String birthDate) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(CHILDREN)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json(
                                                        Map.of(
                                                                "name", "Kid",
                                                                "gender", gender,
                                                                "birthDate", birthDate))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    private void createRecord(String token, long childId, String recordDate, int heightCm)
            throws Exception {
        mockMvc.perform(
                        post(CHILDREN + "/" + childId + "/growth-records")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("recordDate", recordDate, "heightCm", heightCm))))
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
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("accessToken")
                .asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String email(String tag) {
        return "it-" + runId + "-" + tag + "@test.dev";
    }
}
