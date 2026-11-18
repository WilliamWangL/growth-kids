package com.growthkids.api.deal;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.support.IntegrationTestCleaner;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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
 * 优惠商品 API 集成测试（TASK-022）。
 *
 * <p>公开侧验证「上架 + 投放窗口」可见性、分类/月龄/关键词过滤；
 * 后台侧验证 ADMIN 权限、CRUD 以及与公开可见性的联动（下架即不可见）。
 * 商品与用户无关联，测试数据直接经 JdbcTemplate 写入并在 @BeforeEach 清空。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DealControllerIntegrationTest {

    private static final String DEALS = "/api/v1/deals";
    private static final String ADMIN_DEALS = "/api/v1/admin/deals";
    private static final String REGISTER = "/api/v1/auth/register";
    private static final String LOGIN = "/api/v1/auth/login";
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
    void publicListOnlyReturnsVisibleDeals() throws Exception {
        insertDeal(title("在售"), "toys", null, null, "active", null, null, true);
        insertDeal(title("未开始"), "toys", null, null, "active", LocalDateTime.now(ZoneOffset.UTC).plusDays(3), null, false);
        insertDeal(title("已结束"), "toys", null, null, "active", null, LocalDateTime.now(ZoneOffset.UTC).minusDays(3), false);
        insertDeal(title("已下架"), "toys", null, null, "inactive", null, null, false);

        mockMvc.perform(get(DEALS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(1))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].title").value(title("在售")))
                // 100 → 60：折扣 40%
                .andExpect(jsonPath("$.items[0].discountPercent").value(40))
                .andExpect(jsonPath("$.items[0].categoryLabel").value("玩具"))
                .andExpect(jsonPath("$.items[0].currency").value("USD"));
    }

    @Test
    void publicListFiltersByCategoryAgeAndKeyword() throws Exception {
        insertDeal(title("积木"), "toys", 0, 12, "active", null, null, false);
        insertDeal(title("绘本"), "books", 24, 60, "active", null, null, false);
        insertDeal(title("推车"), "gear", null, null, "active", null, null, false);

        // 24 月龄：绘本（24-60）+ 推车（无月龄限制）；积木上限 12 被排除
        mockMvc.perform(get(DEALS).param("ageMonths", "24"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(2));

        mockMvc.perform(get(DEALS).param("category", "books"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(1))
                .andExpect(jsonPath("$.items[0].category").value("books"));

        mockMvc.perform(get(DEALS).param("q", "推"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(1))
                .andExpect(jsonPath("$.items[0].title").value(title("推车")));
    }

    @Test
    void publicListRejectsUnknownCategoryAndOutOfRangeAge() throws Exception {
        mockMvc.perform(get(DEALS).param("category", "spaceship"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.category").exists());

        mockMvc.perform(get(DEALS).param("ageMonths", "999"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.ageMonths").exists());
    }

    @Test
    void publicDetailHidesInvisibleDeal() throws Exception {
        long hidden = insertDeal(title("隐藏"), "care", null, null, "inactive", null, null, false);
        long visible = insertDeal(title("可见"), "care", null, null, "active", null, null, false);

        mockMvc.perform(get(DEALS + "/" + hidden))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEAL_NOT_FOUND"));

        mockMvc.perform(get(DEALS + "/" + visible))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(visible))
                .andExpect(jsonPath("$.purchaseUrl").value("https://example.com"));
    }

    @Test
    void publicCategoriesReturnAllOptions() throws Exception {
        mockMvc.perform(get(DEALS + "/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(DealCategory.values().length)))
                .andExpect(jsonPath("$[0].code").value("toys"))
                .andExpect(jsonPath("$[0].label").value("玩具"));
    }

    @Test
    void adminEndpointsRequireAdmin() throws Exception {
        mockMvc.perform(get(ADMIN_DEALS)).andExpect(status().isUnauthorized());

        String user = registerAndGetToken(email("plain"));
        mockMvc.perform(get(ADMIN_DEALS).header(HttpHeaders.AUTHORIZATION, bearer(user)))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreateUpdateDeleteFlow() throws Exception {
        String admin = adminToken("crud");

        MvcResult created =
                mockMvc.perform(
                                post(ADMIN_DEALS)
                                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(dealPayload(title("新品"), "clothing", 100.0, 60.0))))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("active"))
                        .andExpect(jsonPath("$.currency").value("USD"))
                        .andExpect(jsonPath("$.featured").value(false))
                        .andExpect(jsonPath("$.discountPercent").value(40))
                        .andExpect(jsonPath("$.categoryLabel").value("童装"))
                        .andReturn();
        long dealId = objectMapper.readTree(created.getResponse().getContentAsString()).get("id").asLong();

        // 公开侧立即可见
        mockMvc.perform(get(DEALS + "/" + dealId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(title("新品")));

        // PATCH 局部更新：只改价格与标题，分类保持不变
        mockMvc.perform(
                        patch(ADMIN_DEALS + "/" + dealId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("title", title("降价"), "dealPrice", 45.0))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value(title("降价")))
                .andExpect(jsonPath("$.category").value("clothing"))
                // (100-45)/100 → 55%
                .andExpect(jsonPath("$.discountPercent").value(55));

        // 下架后公开不可见，后台仍可见
        mockMvc.perform(
                        patch(ADMIN_DEALS + "/" + dealId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(Map.of("status", "inactive"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("inactive"));
        mockMvc.perform(get(DEALS + "/" + dealId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("DEAL_NOT_FOUND"));
        mockMvc.perform(get(ADMIN_DEALS).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(1))
                .andExpect(jsonPath("$.items[0].id").value(dealId));

        // 删除后彻底消失
        mockMvc.perform(
                        delete(ADMIN_DEALS + "/" + dealId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isNoContent());
        mockMvc.perform(get(ADMIN_DEALS).header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(0));
        mockMvc.perform(get(DEALS)).andExpect(status().isOk())
                .andExpect(jsonPath("$.pagination.total").value(0));
    }

    @Test
    void adminRejectsInvalidPayload() throws Exception {
        String admin = adminToken("invalid");

        Map<String, Object> badCategory = dealPayload(title("分类错"), "spaceship", 100.0, 60.0);
        mockMvc.perform(
                        post(ADMIN_DEALS)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(badCategory)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.category").exists());

        // 原价低于优惠价：Bean Validation 通过，由服务层整行校验拦截
        mockMvc.perform(
                        post(ADMIN_DEALS)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(dealPayload(title("价格倒挂"), "toys", 50.0, 60.0))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DEAL_INVALID_DATA"))
                .andExpect(jsonPath("$.error.details.originalPrice").exists());

        mockMvc.perform(
                        post(ADMIN_DEALS)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(dealPayload(title("月龄倒挂"), "toys", 100.0, 60.0, 60, 12))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("DEAL_INVALID_DATA"))
                .andExpect(jsonPath("$.error.details.maxAgeMonths").exists());

        Map<String, Object> badUrl = dealPayload(title("链接错"), "toys", 100.0, 60.0);
        badUrl.put("purchaseUrl", "ftp://example.com/deal");
        mockMvc.perform(
                        post(ADMIN_DEALS)
                                .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(json(badUrl)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.purchaseUrl").exists());
    }

    // ---- helpers ----

    private long insertDeal(
            String title,
            String category,
            Integer minAgeMonths,
            Integer maxAgeMonths,
            String status,
            LocalDateTime startsAt,
            LocalDateTime endsAt,
            boolean featured) {
        jdbcTemplate.update(
                "INSERT INTO deals (title, category, min_age_months, max_age_months,"
                        + " original_price, deal_price, currency, purchase_url, status, featured,"
                        + " starts_at, ends_at) VALUES (?, ?, ?, ?, ?, ?, 'USD', ?, ?, ?, ?, ?)",
                title,
                category,
                minAgeMonths,
                maxAgeMonths,
                100.00,
                60.00,
                "https://example.com",
                status,
                featured,
                startsAt == null ? null : Timestamp.valueOf(startsAt),
                endsAt == null ? null : Timestamp.valueOf(endsAt));
        return jdbcTemplate.queryForObject(
                "SELECT id FROM deals WHERE title = ?", Long.class, title);
    }

    private Map<String, Object> dealPayload(
            String title, String category, double originalPrice, double dealPrice) {
        return dealPayload(title, category, originalPrice, dealPrice, 0, 36);
    }

    private Map<String, Object> dealPayload(
            String title,
            String category,
            double originalPrice,
            double dealPrice,
            int minAgeMonths,
            int maxAgeMonths) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("brand", "TestBrand");
        payload.put("category", category);
        payload.put("minAgeMonths", minAgeMonths);
        payload.put("maxAgeMonths", maxAgeMonths);
        payload.put("originalPrice", originalPrice);
        payload.put("dealPrice", dealPrice);
        payload.put("merchant", "TestMart");
        payload.put("purchaseUrl", "https://example.com/deal/" + runId);
        payload.put("description", "集成测试商品");
        return payload;
    }

    private String adminToken(String tag) throws Exception {
        String email = email("admin-" + tag);
        registerAndGetToken(email);
        jdbcTemplate.update("UPDATE users SET role = 'admin' WHERE email = ?", email);
        // 重新登录，新 token 才携带 admin role
        return loginAndGetToken(email);
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

    private String loginAndGetToken(String email) throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(LOGIN)
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(json(Map.of("email", email, "password", PASSWORD))))
                        .andExpect(status().isOk())
                        .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String title(String tag) {
        return runId + "-" + tag;
    }

    private String email(String tag) {
        return "it-" + runId + "-" + tag + "@test.dev";
    }
}
