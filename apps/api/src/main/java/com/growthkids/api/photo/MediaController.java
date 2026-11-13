package com.growthkids.api.photo;

import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.photo.entity.GrowthPhoto;
import com.growthkids.api.photo.mapper.GrowthPhotoMapper;
import com.growthkids.api.photo.storage.LocalStorageService;
import com.growthkids.api.photo.storage.StorageProperties;
import com.growthkids.api.photo.storage.StorageSigner;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 媒体直传/访问端点（TASK-013，§46 + §120 规则 29）：
 * 无 JWT，凭 API 签发的短期签名 token 自校验（SecurityConfig 放行）。
 */
@RestController
public class MediaController {

    private final GrowthPhotoMapper photoMapper;
    private final LocalStorageService storageService;
    private final StorageProperties storageProperties;

    public MediaController(
            GrowthPhotoMapper photoMapper,
            LocalStorageService storageService,
            StorageProperties storageProperties) {
        this.photoMapper = photoMapper;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
    }

    /** 客户端直传（presign 返回的 uploadUrl）：超限直接拒绝。 */
    @PutMapping("/api/v1/media/upload")
    public ResponseEntity<Void> upload(@RequestParam String token, InputStream body) {
        String storageKey = requireUploadToken(token);
        storageService.write(storageKey, limited(body));
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    /** 签名访问：original 原图（存储 MIME）/ thumb 缩略图（JPEG）。 */
    @GetMapping("/api/v1/media/{photoId}/{variant}")
    public ResponseEntity<InputStreamResource> view(
            @PathVariable long photoId, @PathVariable String variant, @RequestParam String token) {
        if (!StorageSigner.verify(storageProperties.signingSecret(), token)
                .map(subject -> subject.equals("view:" + photoId + ":" + variant))
                .orElse(false)) {
            throw new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED);
        }
        GrowthPhoto photo = photoMapper.selectById(photoId);
        if (photo == null) {
            throw new ApiException(ApiErrorCode.PHOTO_NOT_FOUND);
        }
        String storageKey;
        String mimeType;
        if ("thumb".equals(variant)) {
            storageKey = photo.getThumbnailKey();
            if (storageKey == null) {
                // 缩略图未生成（处理中/HEIC）：视为不存在，客户端回落原图
                throw new ApiException(ApiErrorCode.PHOTO_NOT_FOUND);
            }
            mimeType = "image/jpeg";
        } else if ("original".equals(variant)) {
            storageKey = photo.getStorageKey();
            mimeType = photo.getMimeType();
        } else {
            throw new ApiException(ApiErrorCode.NOT_FOUND);
        }
        InputStreamResource resource = new InputStreamResource(storageService.open(storageKey));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mimeType))
                .contentLength(storageService.size(storageKey))
                .body(resource);
    }

    private String requireUploadToken(String token) {
        String subject =
                StorageSigner.verify(storageProperties.signingSecret(), token)
                        .orElseThrow(() -> new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED));
        if (!subject.startsWith("upload:")) {
            throw new ApiException(ApiErrorCode.AUTH_UNAUTHORIZED);
        }
        return subject.substring("upload:".length());
    }

    /** 限长读取：超过 photo-max-size-bytes 即拒绝（防止未声明大小的滥用上传）。 */
    private InputStream limited(InputStream in) {
        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            long total = 0;
            int read;
            while ((read = in.read(chunk)) != -1) {
                total += read;
                if (total > storageProperties.photoMaxSizeBytes()) {
                    throw new ApiException(ApiErrorCode.PHOTO_TOO_LARGE);
                }
                buffer.write(chunk, 0, read);
            }
            return new ByteArrayInputStream(buffer.toByteArray());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read upload body", e);
        }
    }
}
