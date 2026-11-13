package com.growthkids.api.photo.dto;

/** 照片预签名响应（TASK-013，规范 §47）：客户端据此直传文件后调 finalize。 */
public record PresignPhotoResponse(
        long photoId, String uploadUrl, String storageKey, long expiresIn) {}

