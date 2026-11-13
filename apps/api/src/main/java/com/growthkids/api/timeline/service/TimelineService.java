package com.growthkids.api.timeline.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.growthkids.api.children.dto.ChildResponse;
import com.growthkids.api.children.service.ChildService;
import com.growthkids.api.growth.entity.GrowthRecord;
import com.growthkids.api.growth.mapper.GrowthRecordMapper;
import com.growthkids.api.membership.dto.PlanPermissions;
import com.growthkids.api.membership.service.PlanResolver;
import com.growthkids.api.memory.entity.GrowthMemory;
import com.growthkids.api.memory.mapper.GrowthMemoryMapper;
import com.growthkids.api.photo.entity.GrowthPhoto;
import com.growthkids.api.photo.mapper.GrowthPhotoMapper;
import com.growthkids.api.photo.storage.StorageProperties;
import com.growthkids.api.photo.storage.StorageSigner;
import com.growthkids.api.timeline.dto.TimelineResponse;
import com.growthkids.api.timeline.dto.TimelineResponse.TimelineItem;
import com.growthkids.api.timeline.dto.TimelineResponse.TimelineItem.TimelinePhoto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 合并时间线服务（TASK-014，规范 §52/§113；TASK-016 接入 Free 条数限制 §64）：
 * 成长记录 + 成长故事合并，按日期倒序（同日记录在前）；
 * 照片按 growth_record_id / memory_id 挂到对应条目（§114），附短期签名 URL；
 * 时间线场景一次全量，不分页；ownership 经 ChildService（§70）。
 */
@Service
public class TimelineService {

    private final ChildService childService;
    private final PlanResolver planResolver;
    private final GrowthRecordMapper growthRecordMapper;
    private final GrowthMemoryMapper memoryMapper;
    private final GrowthPhotoMapper photoMapper;
    private final StorageProperties storageProperties;

    public TimelineService(
            ChildService childService,
            PlanResolver planResolver,
            GrowthRecordMapper growthRecordMapper,
            GrowthMemoryMapper memoryMapper,
            GrowthPhotoMapper photoMapper,
            StorageProperties storageProperties) {
        this.childService = childService;
        this.planResolver = planResolver;
        this.growthRecordMapper = growthRecordMapper;
        this.memoryMapper = memoryMapper;
        this.photoMapper = photoMapper;
        this.storageProperties = storageProperties;
    }

    public TimelineResponse get(long userId, long childId) {
        ChildResponse child = childService.get(userId, childId);
        // 时间线条数按套餐（TASK-016，§64：Free 最近 20 条，-1 不限）
        long timelineLimit = planResolver.resolve(userId).permissions().timelineLimit();
        boolean limited = timelineLimit != PlanPermissions.UNLIMITED;
        LambdaQueryWrapper<GrowthRecord> recordQuery =
                new LambdaQueryWrapper<GrowthRecord>()
                        .eq(GrowthRecord::getChildId, childId)
                        .orderByDesc(GrowthRecord::getRecordDate)
                        .orderByDesc(GrowthRecord::getId);
        LambdaQueryWrapper<GrowthMemory> memoryQuery =
                new LambdaQueryWrapper<GrowthMemory>()
                        .eq(GrowthMemory::getChildId, childId)
                        .orderByDesc(GrowthMemory::getMemoryDate)
                        .orderByDesc(GrowthMemory::getId);
        if (limited) {
            // 各源取前 N 即可覆盖全局前 N（两源均已按日期倒序）
            recordQuery.last("LIMIT " + timelineLimit);
            memoryQuery.last("LIMIT " + timelineLimit);
        }
        List<GrowthRecord> records = growthRecordMapper.selectList(recordQuery);
        List<GrowthMemory> memories = memoryMapper.selectList(memoryQuery);
        List<GrowthPhoto> photos =
                photoMapper.selectList(
                        new LambdaQueryWrapper<GrowthPhoto>().eq(GrowthPhoto::getChildId, childId));
        Map<Long, List<GrowthPhoto>> byRecord =
                groupByPhoto(photos, GrowthPhoto::getGrowthRecordId);
        Map<Long, List<GrowthPhoto>> byMemory = groupByPhoto(photos, GrowthPhoto::getMemoryId);

        List<TimelineItem> items = new ArrayList<>();
        for (GrowthRecord record : records) {
            items.add(
                    TimelineItem.fromRecord(
                            record,
                            child.birthDate(),
                            toTimelinePhotos(byRecord.get(record.getId()))));
        }
        for (GrowthMemory memory : memories) {
            items.add(
                    TimelineItem.fromMemory(
                            memory,
                            child.birthDate(),
                            toTimelinePhotos(byMemory.get(memory.getId()))));
        }
        // date DESC；同日 growth_record 在前（type 字典序）
        items.sort(
                Comparator.comparing(TimelineItem::date, Comparator.reverseOrder())
                        .thenComparing(TimelineItem::type));
        if (limited && items.size() > timelineLimit) {
            items = new ArrayList<>(items.subList(0, (int) timelineLimit));
        }
        return new TimelineResponse(items);
    }

    /** 按外键分组（忽略未关联照片；无关联条目的照片不单独出现在时间线）。 */
    private static Map<Long, List<GrowthPhoto>> groupByPhoto(
            List<GrowthPhoto> photos, Function<GrowthPhoto, Long> key) {
        return photos.stream()
                .filter(photo -> key.apply(photo) != null)
                .collect(Collectors.groupingBy(key));
    }

    private List<TimelinePhoto> toTimelinePhotos(List<GrowthPhoto> photos) {
        if (photos == null) {
            return List.of();
        }
        return photos.stream()
                .map(
                        photo ->
                                new TimelinePhoto(
                                        photo.getId(),
                                        photo.getThumbnailKey() == null
                                                ? null
                                                : mediaUrl(photo.getId(), "thumb"),
                                        mediaUrl(photo.getId(), "original")))
                .toList();
    }

    private String mediaUrl(long photoId, String variant) {
        String token =
                StorageSigner.sign(
                        storageProperties.signingSecret(),
                        "view:" + photoId + ":" + variant,
                        Instant.now().plusSeconds(storageProperties.signedUrlTtlSeconds()));
        return "/api/v1/media/" + photoId + "/" + variant + "?token=" + token;
    }
}
