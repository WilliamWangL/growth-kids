package com.growthkids.api.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.common.security.AuthProperties;
import com.growthkids.api.common.security.JwtService;
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
 * Auth API 集成测试（规范 §24-§27、§68、§71）。
 * 使用 growthkids_test 库与本地 Redis（docker compose 专属端口）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
    private static final String REFRESH = "/api/v1/auth/refresh";
    private static final String LOGOUT = "/api/v1/auth/logout";
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;

    /** 每次运行使用唯一后缀，避免 Redis 限流计数与数据库残留跨次运行干扰。 */
    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void cleanUp() {
        IntegrationTestCleaner.cleanUp(jdbcTemplate);
    }

    @Test
    void registerIssuesTokensAndStringId() throws Exception {
        String email = email("register");
        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("email", email, "password", PASSWORD, "displayName", "William"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.id").isString())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.displayName").value("William"))
                .andExpect(jsonPath("$.accessToken").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshToken").value(not(emptyOrNullString())))
                .andExpect(header().string("X-Request-Id", containsString("req_")));
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String email = email("duplicate");
        registerUser(email);
        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("AUTH_EMAIL_EXISTS"))
                .andExpect(jsonPath("$.requestId").value(not(emptyOrNullString())));
    }

    @Test
    void registerRejectsInvalidPayload() throws Exception {
        mockMvc.perform(
                        post(REGISTER)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("email", "not-an-email", "password", "short"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.email").exists())
                .andExpect(jsonPath("$.error.details.password").exists());
    }

    @Test
    void loginSucceedsWithCorrectPassword() throws Exception {
        String email = email("login-ok");
        registerUser(email);
        mockMvc.perform(
                        post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.accessToken").value(not(emptyOrNullString())))
                .andExpect(jsonPath("$.refreshToken").value(not(emptyOrNullString())));
    }

    @Test
    void loginRejectsWrongPassword() throws Exception {
        String email = email("login-bad");
        registerUser(email);
        mockMvc.perform(
                        post(LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("email", email, "password", "WrongPassword123!"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_CREDENTIALS"));
    }

    @Test
    void refreshRotatesTokenAndDetectsReuse() throws Exception {
        String email = email("refresh");
        JsonNode registered = registerUser(email);
        String first = registered.get("refreshToken").asText();

        // 正常轮换：旧 token 换新 token
        JsonNode rotated = refresh(first);
        String second = rotated.get("refreshToken").asText();
        Assertions.assertNotEquals(first, second);

        // 复用已轮换的旧 token：判定泄露，返回 401 并撤销该用户全部 token
        mockMvc.perform(post(REFRESH).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", first))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));

        // 泄露判定后，新 token 也已被撤销
        mockMvc.perform(post(REFRESH).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", second))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void refreshRejectsUnknownToken() throws Exception {
        mockMvc.perform(
                        post(REFRESH).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", "unknown-token"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void logoutRevokesRefreshTokenIdempotently() throws Exception {
        String email = email("logout");
        JsonNode registered = registerUser(email);
        String refreshToken = registered.get("refreshToken").asText();

        mockMvc.perform(post(LOGOUT).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());
        // 幂等：重复登出仍返回 204
        mockMvc.perform(post(LOGOUT).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isNoContent());
        // 已登出 token 不可再刷新
        mockMvc.perform(post(REFRESH).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void protectedEndpointRequiresValidAccessToken() throws Exception {
        // 无 token → 401 AUTH_UNAUTHORIZED
        mockMvc.perform(get("/api/v1/children"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_UNAUTHORIZED"))
                .andExpect(jsonPath("$.requestId").value(not(emptyOrNullString())));

        // 有效 access token 可通过认证（GET /children 已由 TASK-006 实现 → 200）
        String email = email("protected");
        JsonNode registered = registerUser(email);
        String accessToken = registered.get("accessToken").asText();
        mockMvc.perform(get("/api/v1/children").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isArray());
    }

    @Test
    void expiredAccessTokenReturnsTokenExpiredCode() throws Exception {
        AuthProperties properties = new AuthProperties();
        properties.setJwtAccessSecret("test-access-secret-0123456789-0123456789");
        properties.setIssuer("growthkids");
        properties.setAccessTokenTtlSeconds(-60);
        String expiredToken = new JwtService(properties).issueAccessToken(1L, "user");

        mockMvc.perform(get("/api/v1/children").header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOKEN_EXPIRED"));
    }

    @Test
    void loginBruteForceProtectionBlocksAfterMaxAttempts() throws Exception {
        String email = email("bruteforce");
        registerUser(email);

        // 默认邮箱维度上限 10 次：前 10 次失败均为 401
        for (int i = 0; i < 10; i++) {
            mockMvc.perform(
                            post(LOGIN)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(Map.of("email", email, "password", "WrongPassword123!"))))
                    .andExpect(status().isUnauthorized());
        }
        // 第 11 次触发限流
        mockMvc.perform(
                        post(LOGIN)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("email", email, "password", "WrongPassword123!"))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("AUTH_TOO_MANY_ATTEMPTS"));
    }

    @Test
    void successfulLoginClearsFailureCounter() throws Exception {
        String email = email("clear-counter");
        registerUser(email);

        // 4 次失败后成功登录
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(
                            post(LOGIN)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(Map.of("email", email, "password", "WrongPassword123!"))))
                    .andExpect(status().isUnauthorized());
        }
        mockMvc.perform(
                        post(LOGIN).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk());

        // 计数已清零：再失败 7 次（若未清零则总计 11 次会触发 429）仍全部为 401
        for (int i = 0; i < 7; i++) {
            mockMvc.perform(
                            post(LOGIN)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(json(Map.of("email", email, "password", "WrongPassword123!"))))
                    .andExpect(status().isUnauthorized());
        }
    }

    private JsonNode registerUser(String email) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(REGISTER)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(Map.of("email", email, "password", PASSWORD, "displayName", "William"))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return body(result);
    }

    private JsonNode refresh(String refreshToken) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(REFRESH).contentType(MediaType.APPLICATION_JSON).content(json(Map.of("refreshToken", refreshToken))))
                        .andExpect(status().isOk())
                        .andReturn();
        return body(result);
    }

    private JsonNode body(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String email(String tag) {
        return "it-" + runId + "-" + tag + "@test.dev";
    }
}
