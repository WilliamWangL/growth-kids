package com.growthkids.api.memory.dto;

import com.growthkids.api.memory.entity.GrowthMemory;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 成长故事响应（TASK-015，对齐 growth_memories 表 §16）。 */
public record MemoryResponse(
        long id,
        long childId,
        LocalDate memoryDate,
        String title,
        String content,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static MemoryResponse of(GrowthMemory memory) {
        return new MemoryResponse(
                memory.getId(),
                memory.getChildId(),
                memory.getMemoryDate(),
                memory.getTitle(),
                memory.getContent(),
                memory.getCreatedAt(),
                memory.getUpdatedAt());
    }
}
