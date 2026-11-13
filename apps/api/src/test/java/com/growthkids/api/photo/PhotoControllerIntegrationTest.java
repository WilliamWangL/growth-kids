package com.growthkids.api.photo;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.photo.storage.LocalStorageService;
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
 * 照片 API 集成测试（TASK-013，规范 §46-§49/§120 规则 29）：
 * 全流程 presign → 直传 → finalize → 异步缩略图 → 签名访问/删除。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PhotoControllerIntegrationTest {

    private static final String REGISTER = "/api/v1/auth/register";
    private static final String CHILDREN = "/api/v1/children";
    private static final String PHOTOS = "/api/v1/photos";
    private static final String PASSWORD = "StrongPassword123!";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private LocalStorageService storageService;

    private final String runId = UUID.randomUUID().toString().substring(0, 8);

    @BeforeEach
    void cleanUp() {
        IntegrationTestCleaner.cleanUp(jdbcTemplate);
    }

    @Test
    void presignCreatesPendingRecordAndSignedUploadUrl() throws Exception {
        String token = registerAndGetToken(email("photo-presign"));
        long childId = createChild(token, "Tom", "2025-01-01");

        MvcResult result =
                mockMvc.perform(
                                post(CHILDREN + "/" + childId + "/photos/presign")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json(Map.of(
                                                        "filename", "holiday.jpg",
                                                        "mimeType", "image/jpeg",
                                                        "size", 1024))))
                        .andExpect(status().isCreated())
                        .andExpect(jsonPath("$.photoId").isNumber())
                        .andExpect(jsonPath("$.uploadUrl")
                                .value(startsWith("/api/v1/media/upload?token=")))
                        .andExpect(jsonPath("$.storageKey")
                                .value(startsWith("children/" + childId + "/")))
                        .andExpect(jsonPath("$.expiresIn").value(600))
                        .andReturn();
        long photoId =
                objectMapper
                        .readTree(result.getResponse().getContentAsString())
                        .get("photoId")
                        .asLong();
        assertEquals(
                "pending",
                jdbcTemplate.queryForObject(
                        "SELECT processing_status FROM growth_photos WHERE id = ?",
                        String.class,
                        photoId));
    }

    @Test
    void presignRejectsUnsupportedType() throws Exception {
        String token = registerAndGetToken(email("photo-gif"));
        long childId = createChild(token, "Tom", "2025-01-01");

        mockMvc.perform(
                        post(CHILDREN + "/" + childId + "/photos/presign")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(Map.of(
                                                "filename", "animation.gif",
                                                "mimeType", "image/gif",
                                                "size", 1024))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PHOTO_INVALID_TYPE"));
    }

    @Test
    void presignRejectsOversizedDeclaration() throws Exception {
        String token = registerAndGetToken(email("photo-big"));
        long childId = createChild(token, "Tom", "2025-01-01");

        mockMvc.perform(
                        post(CHILDREN + "/" + childId + "/photos/presign")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        json(Map.of(
                                                "filename", "huge.jpg",
                                                "mimeType", "image/jpeg",
                                                "size", 11L * 1024 * 1024))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void finalizeFullFlowGeneratesThumbnail() throws Exception {
        String token = registerAndGetToken(email("photo-full"));
        long childId = createChild(token, "Tom", "2025-01-01");
        byte[] jpeg = sampleJpeg(100, 80);

        JsonNode presigned = presign(token, childId, "photo.jpg", "image/jpeg", jpeg.length);
        long photoId = presigned.get("photoId").asLong();

        // 客户端直传（无 JWT，凭签名 URL）
        mockMvc.perform(
                        put(presigned.get("uploadUrl").asText())
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content(jpeg))
                .andExpect(status().isCreated());

        mockMvc.perform(
                        post(PHOTOS + "/" + photoId + "/finalize")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk());

        JsonNode photo = waitForReady(token, childId);
        assertEquals(photoId, photo.get("id").asLong());
        assertEquals(100, photo.get("width").asInt());
        assertEquals(80, photo.get("height").asInt());
        assertEquals(jpeg.length, photo.get("fileSize").asLong());
        String thumbnailUrl = photo.get("thumbnailUrl").asText();
        String viewUrl = photo.get("viewUrl").asText();

        // 签名 URL 可无 JWT 访问；缩略图重新编码为 JPEG（§50）
        mockMvc.perform(get(thumbnailUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
        mockMvc.perform(get(viewUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_JPEG));
    }

    @Test
    void finalizeWithoutUploadReturnsBadRequest() throws Exception {
        String token = registerAndGetToken(email("photo-noupload"));
        long childId = createChild(token, "Tom", "2025-01-01");
        long photoId = presign(token, childId, "photo.jpg", "image/jpeg", 1024).get("photoId").asLong();

        mockMvc.perform(
                        post(PHOTOS + "/" + photoId + "/finalize")
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("PHOTO_NOT_UPLOADED"));
    }

    @Test
    void uploadRejectsInvalidToken() throws Exception {
        mockMvc.perform(
                        put("/api/v1/media/upload?token=bogus-token")
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content("x"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void viewRejectsForgedToken() throws Exception {
        mockMvc.perform(get("/api/v1/media/1/original?token=bogus-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteRemovesPhotoAndObject() throws Exception {
        String token = registerAndGetToken(email("photo-del"));
        long childId = createChild(token, "Tom", "2025-01-01");
        byte[] jpeg = sampleJpeg(20, 10);
        JsonNode presigned = presign(token, childId, "photo.jpg", "image/jpeg", jpeg.length);
        long photoId = presigned.get("photoId").asLong();
        String storageKey = presigned.get("storageKey").asText();
        mockMvc.perform(
                        put(presigned.get("uploadUrl").asText())
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .content(jpeg))
                .andExpect(status().isCreated());
        assertTrue(storageService.exists(storageKey));

        mockMvc.perform(
                        delete(PHOTOS + "/" + photoId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isNoContent());

        assertEquals(
                0L,
                jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM growth_photos WHERE id = ?", Long.class, photoId));
        assertFalse(storageService.exists(storageKey));
    }

    @Test
    void otherUserPhotoReturnsNotFound() throws Exception {
        String owner = registerAndGetToken(email("photo-owner"));
        long childId = createChild(owner, "Tom", "2025-01-01");
        long photoId =
                presign(owner, childId, "photo.jpg", "image/jpeg", 1024).get("photoId").asLong();
        String other = registerAndGetToken(email("photo-other"));

        mockMvc.perform(
                        post(PHOTOS + "/" + photoId + "/finalize")
                                .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound())
                // ownership 经 child 归属判断（§70），与 GrowthChart 等资源一致返回 CHILD_NOT_FOUND
                .andExpect(jsonPath("$.error.code").value("CHILD_NOT_FOUND"));

        mockMvc.perform(
                        delete(PHOTOS + "/" + photoId)
                                .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    void listAndEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get(CHILDREN + "/1/photos"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post(PHOTOS + "/1/finalize"))
                .andExpect(status().isUnauthorized());
    }

    /** 轮询列表直到异步处理完成（@Async，最多 ~5s）。 */
    private JsonNode waitForReady(String token, long childId) throws Exception {
        for (int i = 0; i < 25; i++) {
            MvcResult result =
                    mockMvc.perform(
                                    get(CHILDREN + "/" + childId + "/photos")
                                            .header(HttpHeaders.AUTHORIZATION, bearer(token)))
                            .andExpect(status().isOk())
                            .andReturn();
            JsonNode items =
                    objectMapper
                            .readTree(result.getResponse().getContentAsString())
                            .get("items");
            if (items.size() > 0 && "ready".equals(items.get(0).get("processingStatus").asText())) {
                return items.get(0);
            }
            Thread.sleep(200);
        }
        throw new AssertionError("Photo processing did not reach ready state in time");
    }

    /** 生成纯色测试 JPEG（无 EXIF）。 */
    private static byte[] sampleJpeg(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", out);
        return out.toByteArray();
    }

    private JsonNode presign(String token, long childId, String filename, String mimeType, long size)
            throws Exception {
        MvcResult result =
                mockMvc.perform(
                                post(CHILDREN + "/" + childId + "/photos/presign")
                                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                json(Map.of(
                                                        "filename", filename,
                                                        "mimeType", mimeType,
                                                        "size", size))))
                        .andExpect(status().isCreated())
                        .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString());
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
        return objectMapper
                .readTree(result.getResponse().getContentAsString())
                .get("id")
                .asLong();
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
