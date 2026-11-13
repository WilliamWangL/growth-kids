package com.growthkids.api.children;

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

/** Child API 集成测试（TASK-006：CRUD + 软删除 + ownership）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ChildControllerIntegrationTest {

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
    void createReturns201WithStringId() throws Exception {
        String token = registerAndGetToken(email("child-create"));
        mockMvc.perform(
                        post(CHILDREN)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                Map.of(
                                                        "name", "Tom",
                                                        "gender", "male",
                                                        "birthDate", "2025-01-01",
                                                        "fatherHeightCm", 170,
                                                        "motherHeightCm", 163))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.name").value("Tom"))
                .andExpect(jsonPath("$.gender").value("male"))
                .andExpect(jsonPath("$.birthDate").value("2025-01-01"))
                .andExpect(jsonPath("$.fatherHeightCm").value(170))
                .andExpect(jsonPath("$.motherHeightCm").value(163));
    }

    @Test
    void createRejectsFutureBirthDate() throws Exception {
        String token = registerAndGetToken(email("child-future"));
        String future = LocalDate.now().plusDays(1).toString();
        mockMvc.perform(
                        post(CHILDREN)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("name", "Future", "gender", "female", "birthDate", future))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("CHILD_INVALID_DATA"))
                .andExpect(jsonPath("$.error.details.birthDate").value(not(emptyOrNullString())));
    }

    @Test
    void createRejectsInvalidGender() throws Exception {
        String token = registerAndGetToken(email("child-gender"));
        mockMvc.perform(
                        post(CHILDREN)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("name", "Tom", "gender", "robot", "birthDate", "2025-01-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.gender").value(not(emptyOrNullString())));
    }

    @Test
    void createEnforcesFreeChildLimit() throws Exception {
        String token = registerAndGetToken(email("child-limit"));
        createChild(token, "First", "2024-05-05");
        mockMvc.perform(
                        post(CHILDREN)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("name", "Second", "gender", "male", "birthDate", "2025-01-01"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("CHILD_LIMIT_REACHED"));
    }

    @Test
    void listReturnsOnlyOwnChildren() throws Exception {
        String tokenA = registerAndGetToken(email("child-owner-a"));
        String tokenB = registerAndGetToken(email("child-owner-b"));
        createChild(tokenA, "Alice", "2023-03-03");

        mockMvc.perform(get(CHILDREN).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].name").value("Alice"))
                .andExpect(jsonPath("$.pagination.page").value(1))
                .andExpect(jsonPath("$.pagination.total").value(1));

        mockMvc.perform(get(CHILDREN).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void detailAndUpdateAreOwnershipIsolated() throws Exception {
        String tokenA = registerAndGetToken(email("child-iso-a"));
        String tokenB = registerAndGetToken(email("child-iso-b"));
        long childId = createChild(tokenA, "Bob", "2022-02-02");

        // B 访问 A 的孩子 → 404（不泄露存在性）
        mockMvc.perform(get(CHILDREN + "/" + childId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));
        mockMvc.perform(
                        patch(CHILDREN + "/" + childId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("name", "Hacked"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(CHILDREN + "/" + childId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenB))
                .andExpect(status().isNotFound());

        // A 自己可以正常访问
        mockMvc.perform(get(CHILDREN + "/" + childId).header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Bob"));
    }

    @Test
    void updatePersistsPatchChanges() throws Exception {
        String token = registerAndGetToken(email("child-update"));
        long childId = createChild(token, "Cindy", "2021-06-06");

        Map<String, Object> patchBody = new HashMap<>();
        patchBody.put("name", "Cindy New");
        patchBody.put("fatherHeightCm", 178);
        patchBody.put("notes", "loves running");
        mockMvc.perform(
                        patch(CHILDREN + "/" + childId)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(patchBody)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cindy New"))
                .andExpect(jsonPath("$.fatherHeightCm").value(178))
                .andExpect(jsonPath("$.notes").value("loves running"))
                // 未提供的字段保持不变
                .andExpect(jsonPath("$.gender").value("male"))
                .andExpect(jsonPath("$.birthDate").value("2021-06-06"));

        mockMvc.perform(get(CHILDREN + "/" + childId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cindy New"));
    }

    @Test
    void deleteSoftDeletesChild() throws Exception {
        String token = registerAndGetToken(email("child-delete"));
        long childId = createChild(token, "Dora", "2020-10-10");

        mockMvc.perform(delete(CHILDREN + "/" + childId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNoContent());

        // 删除后不可见
        mockMvc.perform(get(CHILDREN + "/" + childId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(CHILDREN).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));

        // 数据库中仍是软删除
        String status =
                jdbcTemplate.queryForObject("SELECT status FROM children WHERE id = ?", String.class, childId);
        assertEquals("deleted", status);

        // 重复删除 → 404
        mockMvc.perform(delete(CHILDREN + "/" + childId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    private long createChild(String token, String name, String birthDate) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(CHILDREN)
                                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(Map.of("name", name, "gender", "male", "birthDate", birthDate))))
                        .andExpect(status().isCreated())
                        .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("id").asLong();
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(REGISTER)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(Map.of("email", email, "password", PASSWORD, "displayName", "Parent"))))
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
