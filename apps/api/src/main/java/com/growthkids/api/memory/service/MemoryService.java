package com.growthkids.api.memory.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.growthkids.api.children.dto.ChildResponse;
import com.growthkids.api.children.service.ChildService;
import com.growthkids.api.common.error.ApiErrorCode;
import com.growthkids.api.common.error.ApiException;
import com.growthkids.api.common.web.PageQuery;
import com.growthkids.api.common.web.PageResponse;
import com.growthkids.api.memory.dto.CreateMemoryRequest;
import com.growthkids.api.memory.dto.MemoryResponse;
import com.growthkids.api.memory.dto.UpdateMemoryRequest;
import com.growthkids.api.memory.entity.GrowthMemory;
import com.growthkids.api.memory.mapper.GrowthMemoryMapper;
import com.growthkids.api.photo.entity.GrowthPhoto;
import com.growthkids.api.photo.mapper.GrowthPhotoMapper;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 成长故事服务（TASK-015，规范 §16/§114）：CRUD + 照片解关联；
 * 日期校验对齐成长记录（不可未来/不早于出生 §31）；
 * PATCH 缺省不变、title 空串清空、content 不可清空；
 * ownership 经 ChildService（§70），非本人一律 MEMORY_NOT_FOUND / CHILD_NOT_FOUND。
 */
@Service
public class MemoryService {

    private final ChildService childService;
    private final GrowthMemoryMapper memoryMapper;
    private final GrowthPhotoMapper photoMapper;

    public MemoryService(
            ChildService childService,
            GrowthMemoryMapper memoryMapper,
            GrowthPhotoMapper photoMapper) {
        this.childService = childService;
        this.memoryMapper = memoryMapper;
        this.photoMapper = photoMapper;
    }

    public MemoryResponse create(long userId, long childId, CreateMemoryRequest request) {
        ChildResponse child = childService.get(userId, childId);
        validateMemoryDate(child.birthDate(), request.memoryDate());
        GrowthMemory memory = new GrowthMemory();
        memory.setChildId(childId);
        memory.setMemoryDate(request.memoryDate());
        memory.setTitle(normalizeTitle(request.title()));
        memory.setContent(request.content().trim());
        memoryMapper.insert(memory);
        // 重读回填 DB 默认时间戳（created_at/updated_at），与 GrowthRecordService 同模式
        return MemoryResponse.of(memoryMapper.selectById(memory.getId()));
    }

    public PageResponse<MemoryResponse> list(long userId, long childId, PageQuery query) {
        childService.get(userId, childId);
        Page<GrowthMemory> page = new Page<>(query.page(), query.pageSize());
        memoryMapper.selectPage(
                page,
                new LambdaQueryWrapper<GrowthMemory>()
                        .eq(GrowthMemory::getChildId, childId)
                        .orderByDesc(GrowthMemory::getMemoryDate)
                        .orderByDesc(GrowthMemory::getId));
        List<MemoryResponse> items =
                page.getRecords().stream().map(MemoryResponse::of).toList();
        return new PageResponse<>(
                items,
                new PageResponse.Pagination(
                        page.getCurrent(), page.getSize(), page.getTotal(), page.getPages()));
    }

    public MemoryResponse update(long userId, long memoryId, UpdateMemoryRequest request) {
        GrowthMemory memory = requireOwned(userId, memoryId);
        if (request.memoryDate() != null && !request.memoryDate().equals(memory.getMemoryDate())) {
            ChildResponse child = childService.get(userId, memory.getChildId());
            validateMemoryDate(child.birthDate(), request.memoryDate());
            memory.setMemoryDate(request.memoryDate());
        }
        if (request.title() != null) {
            memory.setTitle(normalizeTitle(request.title()));
        }
        if (request.content() != null) {
            String trimmed = request.content().trim();
            if (trimmed.isEmpty()) {
                throw new ApiException(
                        ApiErrorCode.MEMORY_INVALID_DATA,
                        "Content must not be blank",
                        Map.of("content", "must not be blank"));
            }
            memory.setContent(trimmed);
        }
        memoryMapper.updateById(memory);
        return MemoryResponse.of(memory);
    }

    /** 删除故事：关联照片解关联保留（照片仍是孩子的资产，在照片页/时间线记录项可见）。 */
    @Transactional
    public void delete(long userId, long memoryId) {
        requireOwned(userId, memoryId);
        photoMapper.update(
                null,
                new LambdaUpdateWrapper<GrowthPhoto>()
                        .eq(GrowthPhoto::getMemoryId, memoryId)
                        .set(GrowthPhoto::getMemoryId, null));
        memoryMapper.deleteById(memoryId);
    }

    private GrowthMemory requireOwned(long userId, long memoryId) {
        GrowthMemory memory = memoryMapper.selectById(memoryId);
        if (memory == null) {
            throw new ApiException(ApiErrorCode.MEMORY_NOT_FOUND);
        }
        // ownership 经 child 归属判断（§70）：非本人一律 CHILD_NOT_FOUND（与 Photo 一致）
        childService.get(userId, memory.getChildId());
        return memory;
    }

    /** 日期校验（§31 同语义）：不可未来、不可早于出生。 */
    private static void validateMemoryDate(LocalDate birthDate, LocalDate memoryDate) {
        if (memoryDate.isBefore(birthDate)) {
            throw new ApiException(
                    ApiErrorCode.MEMORY_INVALID_DATA,
                    "Memory date must not be before the child's birth date",
                    Map.of("memoryDate", "must not be before birth date"));
        }
        if (memoryDate.isAfter(LocalDate.now(ZoneOffset.UTC))) {
            throw new ApiException(
                    ApiErrorCode.MEMORY_INVALID_DATA,
                    "Memory date must not be in the future",
                    Map.of("memoryDate", "must not be in the future"));
        }
    }

    private static String normalizeTitle(String title) {
        String trimmed = title == null ? null : title.trim();
        return trimmed == null || trimmed.isEmpty() ? null : trimmed;
    }
}
