package com.growthkids.api.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.support.IntegrationTestCleaner;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
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
 * 合并时间线集成测试（TASK-014，规范 §52/§113）。
 *
 * <p>基准：child male birthDate=2025-01-01；两条记录（2026-03-15 / 2026-09-15）
 * 与两条 memory（2026-08-01 / 2026-03-15），断言 date DESC + 同日记录在前；
 * 照片经 growth_record_id 关联后带签名 URL 展示。memory 写入侧（CRUD）属 TASK-015。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class TimelineControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String CHILDREN = "/api/v1/children";
    private static final String PHOTOS = "/api/v1/photos";
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
    void mergedTimelineSortedByDateDesc() throws Exception {
        String token = registerAndGetToken(email("tl-merge"));
        long childId = createChild(token, "Tom", "2025-01-01");
        createRecord(token, childId,
                Map.of("recordDate", "2026-03-15", "heightCm", 78, "weightKg", 10.2,
                        "note", "开始走路了"));
        createRecord(token, childId,
                Map.of("recordDate", "2026-09-15", "heightCm", 82, "weightKg", 11.7,
                        "headCircumferenceCm", 47.5));
        insertMemory(childId, "2026-08-01", "第一次去海边", "小脚丫踩在沙滩上，笑个不停");
        insertMemory(childId, "2026-03-15", "同日故事", "与记录同日的成长故事");

        mockMvc.perform(get(CHILDREN + "/" + childId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                // items[0]：最新日期的记录，月龄按实际日期计算（§37）
                .andExpect(jsonPath("$.items[0].type").value("growth_record"))
                .andExpect(jsonPath("$.items[0].date").value("2026-09-15"))
                .andExpect(jsonPath("$.items[0].ageMonths").value(20))
                .andExpect(jsonPath("$.items[0].heightCm").value(82.0))
                .andExpect(jsonPath("$.items[0].weightKg").value(11.7))
                .andExpect(jsonPath("$.items[0].headCircumferenceCm").value(47.5))
                .andExpect(jsonPath("$.items[0].photos").isArray())
                .andExpect(jsonPath("$.items[0].photos").isEmpty())
                // items[1]：memory 项（仅 title/content，无测量字段）
                .andExpect(jsonPath("$.items[1].type").value("memory"))
                .andExpect(jsonPath("$.items[1].date").value("2026-08-01"))
                .andExpect(jsonPath("$.items[1].ageMonths").value(19))
                .andExpect(jsonPath("$.items[1].title").value("第一次去海边"))
                .andExpect(jsonPath("$.items[1].content").value("小脚丫踩在沙滩上，笑个不停"))
                .andExpect(jsonPath("$.items[1].heightCm").doesNotExist())
                .andExpect(jsonPath("$.items[1].title").exists())
                // items[2]/items[3]：同日时 growth_record 在前（type 字典序）
                .andExpect(jsonPath("$.items[2].type").value("growth_record"))
                .andExpect(jsonPath("$.items[2].date").value("2026-03-15"))
                .andExpect(jsonPath("$.items[2].note").value("开始走路了"))
                .andExpect(jsonPath("$.items[3].type").value("memory"))
                .andExpect(jsonPath("$.items[3].date").value("2026-03-15"))
                .andExpect(jsonPath("$.items.length()").value(4));
    }

    @Test
    void photosAttachedToRecordAppearWithSignedUrls() throws Exception {
        String token = registerAndGetToken(email("tl-photo"));
        long childId = createChild(token, "Tom", "2025-01-01");
        long recordId = createRecord(token, childId,
                Map.of("recordDate", "2026-09-15", "heightCm", 82, "weightKg", 11.7))
                .get("id").asLong();
        byte[] jpeg = sampleJpeg(60, 40);
        JsonNode presigned = presign(token, childId, jpeg.length);
        long photoId = presigned.get("photoId").asLong();
        mockMvc.perform(put(presigned.get("uploadUrl").asText())
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(jpeg))
                .andExpect(status().isCreated());
        mockMvc.perform(post(PHOTOS + "/" + photoId + "/finalize")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());
        // 写入侧关联（记录表单上传属后续任务，此处直接建立外键）
        jdbcTemplate.update("UPDATE growth_photos SET growth_record_id = ? WHERE id = ?",
                recordId, photoId);
        waitForPhotoReady(token, childId);

        MvcResult result = mockMvc.perform(get(CHILDREN + "/" + childId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode photos = objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("items").get(0).get("photos");
        assertEquals(1, photos.size());
        mockMvc.perform(get(photos.get(0).get("thumbnailUrl").asText()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mockMvc.perform(get(photos.get(0).get("viewUrl").asText()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void returnsEmptyTimelineWhenNothingRecorded() throws Exception {
        String token = registerAndGetToken(email("tl-empty"));
        long childId = createChild(token, "Tom", "2025-01-01");

        mockMvc.perform(get(CHILDREN + "/" + childId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void otherUserChildReturnsNotFound() throws Exception {
        String owner = registerAndGetToken(email("tl-owner"));
        long childId = createChild(owner, "Tom", "2025-01-01");
        String other = registerAndGetToken(email("tl-other"));

        mockMvc.perform(get(CHILDREN + "/" + childId + "/timeline")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(get(CHILDREN + "/1/timeline"))
                .andExpect(status().isUnauthorized());
    }

    private void insertMemory(long childId, String date, String title, String content) {
        jdbcTemplate.update(
                "INSERT INTO growth_memories (child_id, memory_date, title, content) "
                        + "VALUES (?, ?, ?, ?)",
                childId, date, title, content);
    }

    /** 轮询照片列表直到异步处理 ready（@Async，最多 ~5s）。 */
    private void waitForPhotoReady(String token, long childId) throws Exception {
        for (int i = 0; i < 25; i++) {
            MvcResult result = mockMvc.perform(get(CHILDREN + "/" + childId + "/photos")
                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                    .andExpect(status().isOk())
                    .andReturn();
            JsonNode items = objectMapper
                    .readTree(result.getResponse().getContentAsString()).get("items");
            if (items.size() > 0 && "ready".equals(items.get(0).get("processingStatus").asText())) {
                return;
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Photo did not reach ready state in time");
    }

    private static byte[] sampleJpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.GREEN);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private JsonNode presign(String token, long childId, long size) throws Exception {
        MvcResult result = mockMvc.perform(post(CHILDREN + "/" + childId + "/photos/presign")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of(
                                "filename", "photo.jpg", "mimeType", "image/jpeg", "size", size))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private JsonNode createRecord(String token, long childId, Map<String, Object> body)
            throws Exception {
        MvcResult result = mockMvc.perform(post(CHILDREN + "/" + childId + "/growth-records")
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(body)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    private long createChild(String token, String name, String birthDate) throws Exception {
        MvcResult result = mockMvc.perform(post(CHILDREN)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("name", name, "gender", "male", "birthDate", birthDate))))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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
