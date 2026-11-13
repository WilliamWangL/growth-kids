package com.growthkids.api.memory;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
 * 成长故事 CRUD 集成测试（TASK-015，规范 §16/§114）：
 * 创建/列表/编辑（缺省不变）/删除（照片解关联）+ presign 照片挂载 + ownership。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MemoryControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String CHILDREN = "/api/v1/children";
    private static final String MEMORIES = "/api/v1/memories";
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
    void createAndListReturnsDateDesc() throws Exception {
        String token = registerAndGetToken(email("mm-create"));
        long childId = createChild(token, "Tom", "2025-01-01");

        mockMvc.perform(post(CHILDREN + "/" + childId + "/memories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "memoryDate", "2026-08-01",
                                "title", "第一次去海边",
                                "content", "小脚丫踩在沙滩上，笑个不停"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.childId").value(childId))
                .andExpect(jsonPath("$.memoryDate").value("2026-08-01"))
                .andExpect(jsonPath("$.title").value("第一次去海边"))
                .andExpect(jsonPath("$.content").value("小脚丫踩在沙滩上，笑个不停"))
                .andExpect(jsonPath("$.createdAt").exists());

        mockMvc.perform(post(CHILDREN + "/" + childId + "/memories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("memoryDate", "2026-09-10", "content", "无标题的故事"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").doesNotExist());

        mockMvc.perform(get(CHILDREN + "/" + childId + "/memories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].memoryDate").value("2026-09-10"))
                .andExpect(jsonPath("$.items[1].memoryDate").value("2026-08-01"))
                .andExpect(jsonPath("$.pagination.total").value(2));
    }

    @Test
    void createValidatesDateAndContent() throws Exception {
        String token = registerAndGetToken(email("mm-valid"));
        long childId = createChild(token, "Tom", "2025-01-01");

        mockMvc.perform(post(CHILDREN + "/" + childId + "/memories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "memoryDate", "2099-01-01", "content", "未来"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEMORY_INVALID_DATA"));

        mockMvc.perform(post(CHILDREN + "/" + childId + "/memories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "memoryDate", "2024-01-01", "content", "早于出生"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEMORY_INVALID_DATA"));

        mockMvc.perform(post(CHILDREN + "/" + childId + "/memories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("memoryDate", "2026-08-01"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void updatePartialSemantics() throws Exception {
        String token = registerAndGetToken(email("mm-update"));
        long childId = createChild(token, "Tom", "2025-01-01");
        long memoryId = createMemory(token, childId, "2026-08-01", "原标题", "原内容");

        // 仅改 title：date/content 不变
        mockMvc.perform(patch(MEMORIES + "/" + memoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "新标题"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("新标题"))
                .andExpect(jsonPath("$.content").value("原内容"))
                .andExpect(jsonPath("$.memoryDate").value("2026-08-01"));

        // title 空串 → 清空；content 缺省不变
        mockMvc.perform(patch(MEMORIES + "/" + memoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").doesNotExist())
                .andExpect(jsonPath("$.content").value("原内容"));

        // 改日期生效
        mockMvc.perform(patch(MEMORIES + "/" + memoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("memoryDate", "2026-07-15"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memoryDate").value("2026-07-15"));

        // content 空串拒绝（NOT NULL，不可清空）
        mockMvc.perform(patch(MEMORIES + "/" + memoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("content", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MEMORY_INVALID_DATA"));
    }

    @Test
    void deleteMemoryDetachesPhotosButKeepsThem() throws Exception {
        String token = registerAndGetToken(email("mm-del"));
        long childId = createChild(token, "Tom", "2025-01-01");
        long memoryId = createMemory(token, childId, "2026-08-01", "海边", "踩沙滩");
        long photoId = uploadPhoto(token, childId, memoryId, "mm-del");

        mockMvc.perform(delete(MEMORIES + "/" + memoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        // 故事已删，照片仍在（解关联）且可通过照片列表访问
        Integer memoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_memories WHERE id = ?", Integer.class, memoryId);
        Integer photoMemoryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM growth_photos WHERE id = ? AND memory_id IS NULL",
                Integer.class, photoId);
        org.junit.jupiter.api.Assertions.assertEquals(0, memoryCount);
        org.junit.jupiter.api.Assertions.assertEquals(1, photoMemoryCount);
        mockMvc.perform(get(CHILDREN + "/" + childId + "/photos")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1));

        // 再删一次 → 404
        mockMvc.perform(delete(MEMORIES + "/" + memoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMORY_NOT_FOUND"));
    }

    @Test
    void presignAttachesPhotoToMemoryAndTimelineShowsIt() throws Exception {
        String token = registerAndGetToken(email("mm-photo"));
        long childId = createChild(token, "Tom", "2025-01-01");
        long memoryId = createMemory(token, childId, "2026-08-01", "海边", "踩沙滩");
        uploadPhoto(token, childId, memoryId, "mm-photo");

        mockMvc.perform(get(CHILDREN + "/" + childId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].type").value("memory"))
                .andExpect(jsonPath("$.items[0].photos.length()").value(1))
                .andExpect(jsonPath("$.items[0].photos[0].viewUrl",
                        org.hamcrest.Matchers.startsWith("/api/v1/media/")));
    }

    @Test
    void presignRejectsForeignMemory() throws Exception {
        String token = registerAndGetToken(email("mm-foreign"));
        long childId = createChild(token, "Tom", "2025-01-01");
        String other = registerAndGetToken(email("mm-other"));
        long otherChildId = createChild(other, "Ann", "2025-01-01");
        long foreignMemoryId = createMemory(other, otherChildId, "2026-08-01", null, "别人的故事");

        mockMvc.perform(post(CHILDREN + "/" + childId + "/photos/presign")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "filename", "x.jpg", "mimeType", "image/jpeg", "size", 1024,
                                "memoryId", foreignMemoryId))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("MEMORY_NOT_FOUND"));
    }

    @Test
    void otherUserMemoryReturnsNotFound() throws Exception {
        String owner = registerAndGetToken(email("mm-owner"));
        long childId = createChild(owner, "Tom", "2025-01-01");
        long memoryId = createMemory(owner, childId, "2026-08-01", null, "内容");
        String other = registerAndGetToken(email("mm-notowner"));

        mockMvc.perform(patch(MEMORIES + "/" + memoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("title", "偷改"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));

        mockMvc.perform(delete(MEMORIES + "/" + memoryId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post(CHILDREN + "/1/memories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get(CHILDREN + "/1/memories"))
                .andExpect(status().isUnauthorized());
    }

    /** 创建故事并直传一张挂载照片（1x1 场景不入库文件内容校验，仅链路）。 */
    private long uploadPhoto(String token, long childId, long memoryId, String tag)
            throws Exception {
        MvcResult result = mockMvc.perform(post(CHILDREN + "/" + childId + "/photos/presign")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "filename", tag + ".jpg",
                                "mimeType", "image/jpeg",
                                "size", 1024,
                                "memoryId", memoryId))))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode presigned = objectMapper.readTree(result.getResponse().getContentAsString());
        long photoId = presigned.get("photoId").asLong();
        // 未实际上传文件体：finalize 会 400，但 photo 行已带 memory_id，
        // 删除/时间线照片关联断言仅需 DB 关联成立；这里补一个空体上传使状态可推进。
        mockMvc.perform(put(presigned.get("uploadUrl").asText())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[] {1}))
                .andExpect(status().isCreated());
        return photoId;
    }

    private long createMemory(
            String token, long childId, String date, String title, String content)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("memoryDate", date);
        body.put("content", content);
        if (title != null) {
            body.put("title", title);
        }
        MvcResult result = mockMvc.perform(post(CHILDREN + "/" + childId + "/memories")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private long createChild(String token, String name, String birthDate) throws Exception {
        MvcResult result = mockMvc.perform(post(CHILDREN)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "gender", "male", "birthDate", birthDate))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("id").asLong();
    }

    private String registerAndGetToken(String email) throws Exception {
        MvcResult result = mockMvc.perform(post(REGISTER)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "email", email, "password", PASSWORD, "displayName", "Parent"))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    private String json(Object value) throws Exception {
        return objectMapper.writeValueAsString(value);
    }

    private String email(String tag) {
        return "it-" + runId + "-" + tag + "@test.dev";
    }
}
