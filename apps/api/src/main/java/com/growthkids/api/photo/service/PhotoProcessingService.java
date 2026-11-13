package com.growthkids.api.photo.service;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.growthkids.api.photo.entity.GrowthPhoto;
import com.growthkids.api.photo.mapper.GrowthPhotoMapper;
import com.growthkids.api.photo.storage.LocalStorageService;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import javax.imageio.ImageIO;
import net.coobird.thumbnailator.Thumbnails;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 照片异步处理（TASK-013，规范 §50/§51）：
 * pending → processing → ready / failed（数据库状态机 + @Async 替代 BullMQ，架构决策 7）；
 * 处理内容：EXIF orientation 修正、缩略图（≤400px JPEG，新编码天然去除敏感 EXIF §50.5）、
 * 原图尺寸回填；不可解码格式（HEIC）按 Known Issues 策略标记 ready 且原图直出。
 */
@Service
public class PhotoProcessingService {

    private static final Logger log = LoggerFactory.getLogger(PhotoProcessingService.class);

    private static final int THUMBNAIL_MAX_SIZE = 400;

    private final GrowthPhotoMapper photoMapper;
    private final LocalStorageService storageService;

    public PhotoProcessingService(GrowthPhotoMapper photoMapper, LocalStorageService storageService) {
        this.photoMapper = photoMapper;
        this.storageService = storageService;
    }

    /** 异步处理单张照片；失败仅记录日志并标记 failed，不抛出（调用方已返回）。 */
    @Async
    public void process(long photoId) {
        GrowthPhoto photo = photoMapper.selectById(photoId);
        if (photo == null) {
            return;
        }
        // CAS：仅 pending → processing，防止并发重复处理
        int claimed =
                photoMapper.update(
                        null,
                        new LambdaUpdateWrapper<GrowthPhoto>()
                                .eq(GrowthPhoto::getId, photoId)
                                .eq(GrowthPhoto::getProcessingStatus, "pending")
                                .set(GrowthPhoto::getProcessingStatus, "processing"));
        if (claimed == 0) {
            return;
        }
        try {
            BufferedImage image = decode(photo.getStorageKey());
            if (image == null) {
                // 不可解码（HEIC 等）：保留原图，无缩略图，直接 ready（Known Issues）
                markReady(photo, null, null, null);
                return;
            }
            BufferedImage oriented = applyExifOrientation(photo, image);
            String thumbnailKey = buildThumbnailKey(photo.getStorageKey());
            writeThumbnail(oriented, thumbnailKey);
            markReady(photo, thumbnailKey, oriented.getWidth(), oriented.getHeight());
        } catch (Exception e) {
            log.warn("Photo processing failed: photoId={}", photoId, e);
            photoMapper.update(
                    null,
                    new LambdaUpdateWrapper<GrowthPhoto>()
                            .eq(GrowthPhoto::getId, photoId)
                            .set(GrowthPhoto::getProcessingStatus, "failed"));
        }
    }

    private BufferedImage decode(String storageKey) throws Exception {
        try (InputStream in = storageService.open(storageKey)) {
            return ImageIO.read(in);
        }
    }

    /** EXIF orientation 修正（§50.1）：仅处理常见的 3/6/8 三种旋转。 */
    private BufferedImage applyExifOrientation(GrowthPhoto photo, BufferedImage image) {
        try {
            Metadata metadata =
                    ImageMetadataReader.readMetadata(storageService.resolve(photo.getStorageKey()).toFile());
            ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (directory == null || !directory.containsTag(ExifIFD0Directory.TAG_ORIENTATION)) {
                return image;
            }
            int orientation = directory.getInt(ExifIFD0Directory.TAG_ORIENTATION);
            return switch (orientation) {
                case 3 -> Thumbnails.of(image).rotate(180).asBufferedImage();
                case 6 -> Thumbnails.of(image).rotate(90).asBufferedImage();
                case 8 -> Thumbnails.of(image).rotate(-90).asBufferedImage();
                default -> image;
            };
        } catch (Exception e) {
            // EXIF 读取失败不影响主流程
            return image;
        }
    }

    private void writeThumbnail(BufferedImage image, String thumbnailKey) throws Exception {
        Thumbnails.of(image)
                .size(THUMBNAIL_MAX_SIZE, THUMBNAIL_MAX_SIZE)
                .outputFormat("jpeg")
                .toFile(storageService.resolve(thumbnailKey).toFile());
    }

    private static String buildThumbnailKey(String storageKey) {
        int dot = storageKey.lastIndexOf('.');
        String base = dot > 0 ? storageKey.substring(0, dot) : storageKey;
        return base + "_thumb.jpg";
    }

    private void markReady(GrowthPhoto photo, String thumbnailKey, Integer width, Integer height) {
        LambdaUpdateWrapper<GrowthPhoto> update =
                new LambdaUpdateWrapper<GrowthPhoto>()
                        .eq(GrowthPhoto::getId, photo.getId())
                        .set(GrowthPhoto::getProcessingStatus, "ready")
                        .set(GrowthPhoto::getThumbnailKey, thumbnailKey)
                        .set(GrowthPhoto::getWidth, width)
                        .set(GrowthPhoto::getHeight, height);
        photoMapper.update(null, update);
    }
}

