package com.growthkids.api.user;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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

/** User API 集成测试（TASK-005：profile / update profile / timezone / locale）。 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String ME = "/api/v1/users/me";
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
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get(ME))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"));
    }

    @Test
    void meReturnsProfileWithDefaults() throws Exception {
        String accessToken = registerAndGetAccessToken(email("profile-default"));
        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.email").value(email("profile-default")))
                .andExpect(jsonPath("$.displayName").value("William"))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.locale").value("en-US"))
                .andExpect(jsonPath("$.createdAt").value(endsWith("Z")));
    }

    @Test
    void updateProfilePersistsChanges() throws Exception {
        String email = email("profile-update");
        String accessToken = registerAndGetAccessToken(email);

        mockMvc.perform(
                        patch(ME)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(
                                                Map.of(
                                                        "displayName", "Tommy",
                                                        "timezone", "Asia/Shanghai",
                                                        "locale", "zh-CN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Tommy"))
                .andExpect(jsonPath("$.timezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.locale").value("zh-CN"));

        // 再次读取确认已持久化
        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("Tommy"))
                .andExpect(jsonPath("$.timezone").value("Asia/Shanghai"))
                .andExpect(jsonPath("$.locale").value("zh-CN"));
    }

    @Test
    void updateProfileCanClearOptionalField() throws Exception {
        String accessToken = registerAndGetAccessToken(email("profile-clear"));
        mockMvc.perform(
                        patch(ME)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("displayName", ""))))
                .andExpect(status().isOk())
                // default-property-inclusion=non_null：清空后字段不出现
                .andExpect(jsonPath("$.displayName").doesNotExist());

        mockMvc.perform(get(ME).header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").doesNotExist());
    }

    @Test
    void updateProfileRejectsInvalidTimezone() throws Exception {
        String accessToken = registerAndGetAccessToken(email("profile-bad-tz"));
        mockMvc.perform(
                        patch(ME)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("timezone", "Mars/Olympus"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.timezone").value(not(emptyOrNullString())));
    }

    @Test
    void updateProfileRejectsUnsupportedLocale() throws Exception {
        String accessToken = registerAndGetAccessToken(email("profile-bad-locale"));
        mockMvc.perform(
                        patch(ME)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("locale", "fr-FR"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.locale").value(not(emptyOrNullString())));
    }

    @Test
    void updateProfileEmptyBodyIsNoop() throws Exception {
        String accessToken = registerAndGetAccessToken(email("profile-noop"));
        mockMvc.perform(
                        patch(ME)
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayName").value("William"))
                .andExpect(jsonPath("$.timezone").value("UTC"))
                .andExpect(jsonPath("$.locale").value("en-US"));
    }

    private String registerAndGetAccessToken(String email) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(REGISTER)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(Map.of("email", email, "password", PASSWORD, "displayName", "William"))))
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
