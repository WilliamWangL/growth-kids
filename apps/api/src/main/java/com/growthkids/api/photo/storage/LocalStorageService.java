package com.growthkids.api.photo.storage;

import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Service;

/**
 * 本地磁盘存储（v1 决策，TASK-013）：
 * storage key 为相对路径，不暴露给客户端；解析时防目录穿越；
 * 对象存储（S3 兼容）留作后续替换（同接口语义）。
 */
@Service
public class LocalStorageService {

    private final StorageProperties properties;

    public LocalStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    /** 解析 storage key 为绝对路径；含 .. 或越出根目录一律拒绝。 */
    public Path resolve(String storageKey) {
        if (storageKey == null || storageKey.isBlank() || storageKey.contains("..")) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST);
        }
        Path root = Paths.get(properties.localPath()).toAbsolutePath().normalize();
        Path target = root.resolve(storageKey).normalize();
        if (!target.startsWith(root)) {
            throw new ApiException(ApiErrorCode.BAD_REQUEST);
        }
        return target;
    }

    public void write(String storageKey, InputStream in) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(target.getParent());
            Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to store object: " + storageKey, e);
        }
    }

    public boolean exists(String storageKey) {
        return Files.exists(resolve(storageKey));
    }

    public long size(String storageKey) {
        try {
            return Files.size(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to stat object: " + storageKey, e);
        }
    }

    public InputStream open(String storageKey) {
        try {
            return Files.newInputStream(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to open object: " + storageKey, e);
        }
    }

    /** 删除对象；不存在时静默忽略（幂等）。 */
    public void delete(String storageKey) {
        if (storageKey == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to delete object: " + storageKey, e);
        }
    }
}

