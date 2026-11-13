package com.growthkids.api.photo.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 照片预签名请求（TASK-013，规范 §47）：限 JPEG/PNG/WEBP/HEIC，≤10MB（§49）；
 * growthRecordId / memoryId 为可选关联目标（§114 故事照片，TASK-015），必须归属同一 child。
 */
public record PresignPhotoRequest(
        @NotBlank(message = "filename is required")
        @Size(max = 255, message = "filename must be at most 255 characters")
        String filename,
        @NotBlank(message = "mimeType is required")
        String mimeType,
        @NotNull(message = "size is required")
        @Min(value = 1, message = "size must be positive")
        @Max(value = 10L * 1024 * 1024, message = "size must be at most 10MB")
        Long size,
        Long growthRecordId,
        Long memoryId) {}

