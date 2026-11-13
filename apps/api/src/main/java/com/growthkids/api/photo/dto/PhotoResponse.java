package com.growthkids.api.photo.dto;

import com.growthkids.api.photo.entity.GrowthPhoto;
import java.time.LocalDateTime;

/**
 * 照片响应（TASK-013）：viewUrl/thumbnailUrl 为短期签名访问 URL（§120 规则 29），
 * 随响应实时签发；缩略图未生成（processing 中或解码失败）时 thumbnailUrl 省略。
 */
public record PhotoResponse(
        long id,
        long childId,
        Long growthRecordId,
        String originalFilename,
        String mimeType,
        Integer width,
        Integer height,
        Long fileSize,
        String caption,
        String processingStatus,
        LocalDateTime createdAt,
        String viewUrl,
        String thumbnailUrl) {

    public static PhotoResponse of(GrowthPhoto photo, String viewUrl, String thumbnailUrl) {
        return new PhotoResponse(
                photo.getId(),
                photo.getChildId(),
                photo.getGrowthRecordId(),
                photo.getOriginalFilename(),
                photo.getMimeType(),
                photo.getWidth(),
                photo.getHeight(),
                photo.getFileSize(),
                photo.getCaption(),
                photo.getProcessingStatus(),
                photo.getCreatedAt(),
                viewUrl,
                thumbnailUrl);
    }
}

