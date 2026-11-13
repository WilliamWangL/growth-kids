package com.growthkids.api.photo.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.growthkids.api.children.entity.Child;
import com.growthkids.api.children.mapper.ChildMapper;
import com.growthkids.api.children.service.ChildService;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.growth.entity.GrowthRecord;
import com.growthkids.api.growth.mapper.GrowthRecordMapper;
import com.growthkids.api.membership.dto.PlanPermissions;
import com.growthkids.api.membership.service.PlanResolver;
import com.growthkids.api.memory.entity.GrowthMemory;
import com.growthkids.api.memory.mapper.GrowthMemoryMapper;
import com.growthkids.api.photo.dto.PhotoResponse;
import com.growthkids.api.photo.dto.PresignPhotoRequest;
import com.growthkids.api.photo.dto.PresignPhotoResponse;
import com.growthkids.api.photo.entity.GrowthPhoto;
import com.growthkids.api.photo.mapper.GrowthPhotoMapper;
import com.growthkids.api.photo.storage.LocalStorageService;
import com.growthkids.api.photo.storage.StorageProperties;
import com.growthkids.api.photo.storage.StorageSigner;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * 照片服务（TASK-013，规范 §46-§49/§120 规则 29）：
 * presign 创建 pending 记录并签发短期上传 URL（客户端直传）；
 * finalize 验证对象后进入异步处理（缩略图）；删除同步清理文件与记录；
 * 列表/访问均通过短期签名 URL，storage key 不对外暴露。
 */
@Service
public class PhotoService {

    /** 支持格式（§49）：JPEG / PNG / WEBP / HEIC；扩展名映射兼作白名单。 */
    private static final Map<String, String> MIME_EXTENSIONS =
            Map.of(
                    "image/jpeg", "jpg",
                    "image/png", "png",
                    "image/webp", "webp",
                    "image/heic", "heic");

    private static final DateTimeFormatter KEY_DATE = DateTimeFormatter.ofPattern("yyyy/MM")
            .withZone(ZoneOffset.UTC);

    private final ChildService childService;
    private final ChildMapper childMapper;
    private final PlanResolver planResolver;
    private final GrowthPhotoMapper photoMapper;
    private final GrowthRecordMapper growthRecordMapper;
    private final GrowthMemoryMapper memoryMapper;
    private final LocalStorageService storageService;
    private final StorageProperties storageProperties;
    private final PhotoProcessingService photoProcessingService;

    public PhotoService(
            ChildService childService,
            ChildMapper childMapper,
            PlanResolver planResolver,
            GrowthPhotoMapper photoMapper,
            GrowthRecordMapper growthRecordMapper,
            GrowthMemoryMapper memoryMapper,
            LocalStorageService storageService,
            StorageProperties storageProperties,
            PhotoProcessingService photoProcessingService) {
        this.childService = childService;
        this.childMapper = childMapper;
        this.planResolver = planResolver;
        this.photoMapper = photoMapper;
        this.growthRecordMapper = growthRecordMapper;
        this.memoryMapper = memoryMapper;
        this.storageService = storageService;
        this.storageProperties = storageProperties;
        this.photoProcessingService = photoProcessingService;
    }

    public PresignPhotoResponse presign(long userId, long childId, PresignPhotoRequest request) {
        childService.get(userId, childId);
        String extension = MIME_EXTENSIONS.get(request.mimeType().toLowerCase());
        if (extension == null) {
            throw new ApiException(ApiErrorCode.PHOTO_INVALID_TYPE);
        }
        // 照片限额按套餐解析（TASK-016，§64：Free 最多 100 张，-1 不限）
        PlanPermissions permissions = planResolver.resolve(userId).permissions();
        if (permissions.photoLimit() != PlanPermissions.UNLIMITED) {
            List<Long> childIds = childMapper
                    .selectList(new LambdaQueryWrapper<Child>().eq(Child::getUserId, userId))
                    .stream()
                    .map(Child::getId)
                    .toList();
            long existing = childIds.isEmpty()
                    ? 0
                    : photoMapper.selectCount(
                            new LambdaQueryWrapper<GrowthPhoto>().in(GrowthPhoto::getChildId, childIds));
            if (existing >= permissions.photoLimit()) {
                throw new ApiException(ApiErrorCode.MEMBERSHIP_REQUIRED);
            }
        }

        GrowthPhoto photo = new GrowthPhoto();
        photo.setChildId(childId);
        photo.setStorageKey(buildStorageKey(childId, extension));
        photo.setOriginalFilename(truncate(request.filename()));
        photo.setMimeType(request.mimeType().toLowerCase());
        photo.setFileSize(request.size());
        photo.setProcessingStatus("pending");
        // 可选关联目标（§114 故事照片等）：必须归属同一 child
        if (request.growthRecordId() != null) {
            GrowthRecord record = growthRecordMapper.selectById(request.growthRecordId());
            if (record == null || !record.getChildId().equals(childId)) {
                throw new ApiException(ApiErrorCode.GROWTH_RECORD_NOT_FOUND);
            }
            photo.setGrowthRecordId(request.growthRecordId());
        }
        if (request.memoryId() != null) {
            GrowthMemory memory = memoryMapper.selectById(request.memoryId());
            if (memory == null || !memory.getChildId().equals(childId)) {
                throw new ApiException(ApiErrorCode.MEMORY_NOT_FOUND);
            }
            photo.setMemoryId(request.memoryId());
        }
        photoMapper.insert(photo);

        String token =
                StorageSigner.sign(
                        storageProperties.signingSecret(),
                        "upload:" + photo.getStorageKey(),
                        Instant.now().plusSeconds(storageProperties.signedUrlTtlSeconds()));
        String uploadUrl = "/api/v1/media/upload?token=" + token;
        return new PresignPhotoResponse(
                photo.getId(), uploadUrl, photo.getStorageKey(), storageProperties.signedUrlTtlSeconds());
    }

    /**
     * finalize（§48）：验证对象存在/大小后进入异步处理队列。
     * 重复 finalize 幂等：仅 pending 状态触发处理，其余返回当前状态。
     */
    public PhotoResponse finalize(long userId, long photoId) {
        GrowthPhoto photo = requireOwned(userId, photoId);
        if (photo.getProcessingStatus().equals("pending")) {
            if (!storageService.exists(photo.getStorageKey())) {
                throw new ApiException(ApiErrorCode.PHOTO_NOT_UPLOADED);
            }
            long actualSize = storageService.size(photo.getStorageKey());
            if (actualSize == 0) {
                throw new ApiException(ApiErrorCode.PHOTO_NOT_UPLOADED);
            }
            if (actualSize > storageProperties.photoMaxSizeBytes()) {
                throw new ApiException(ApiErrorCode.PHOTO_TOO_LARGE);
            }
            photo.setFileSize(actualSize);
            photoMapper.updateById(photo);
            photoProcessingService.process(photo.getId());
        }
        return toResponse(photo);
    }

    public void delete(long userId, long photoId) {
        GrowthPhoto photo = requireOwned(userId, photoId);
        storageService.delete(photo.getStorageKey());
        storageService.delete(photo.getThumbnailKey());
        photoMapper.deleteById(photoId);
    }

    public PageResponse<PhotoResponse> list(long userId, long childId, PageQuery query) {
        childService.get(userId, childId);
        Page<GrowthPhoto> page = new Page<>(query.page(), query.pageSize());
        photoMapper.selectPage(
                page,
                new LambdaQueryWrapper<GrowthPhoto>()
                        .eq(GrowthPhoto::getChildId, childId)
                        .orderByDesc(GrowthPhoto::getId));
        List<PhotoResponse> items =
                page.getRecords().stream().map(this::toResponse).toList();
        return new PageResponse<>(
                items,
                new PageResponse.Pagination(
                        page.getCurrent(), page.getSize(), page.getTotal(), page.getPages()));
    }

    private GrowthPhoto requireOwned(long userId, long photoId) {
        GrowthPhoto photo = photoMapper.selectById(photoId);
        if (photo == null) {
            throw new ApiException(ApiErrorCode.PHOTO_NOT_FOUND);
        }
        // ownership 经 child 归属判断（§70）：非本人一律 PHOTO_NOT_FOUND
        childService.get(userId, photo.getChildId());
        return photo;
    }

    private PhotoResponse toResponse(GrowthPhoto photo) {
        String viewUrl = mediaUrl(photo.getId(), "original");
        String thumbnailUrl =
                photo.getThumbnailKey() == null ? null : mediaUrl(photo.getId(), "thumb");
        return PhotoResponse.of(photo, viewUrl, thumbnailUrl);
    }

    private String mediaUrl(long photoId, String variant) {
        String token =
                StorageSigner.sign(
                        storageProperties.signingSecret(),
                        "view:" + photoId + ":" + variant,
                        Instant.now().plusSeconds(storageProperties.signedUrlTtlSeconds()));
        return "/api/v1/media/" + photoId + "/" + variant + "?token=" + token;
    }

    private static String buildStorageKey(long childId, String extension) {
        String date = KEY_DATE.format(Instant.now());
        String id = UUID.randomUUID().toString().replace("-", "");
        return "children/%d/%s/%s.%s".formatted(childId, date, id, extension);
    }

    private static String truncate(String filename) {
        String trimmed = filename.trim();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }
}

