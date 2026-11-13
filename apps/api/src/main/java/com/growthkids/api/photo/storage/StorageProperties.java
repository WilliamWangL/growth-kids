package com.growthkids.api.photo.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 照片本地存储配置（v1 决策，TASK-013）。
 *
 * <p>local-path：本地磁盘根目录（相对 API 工作目录）；
 * signing-secret：上传/访问 URL 的 HMAC 签名密钥（生产必须替换）；
 * signed-url-ttl-seconds：短期签名 URL 有效期（§120 规则 29）；
 * photo-max-size-bytes：单张照片大小上限（§49）。
 */
@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
        String localPath,
        String signingSecret,
        long signedUrlTtlSeconds,
        long photoMaxSizeBytes) {}