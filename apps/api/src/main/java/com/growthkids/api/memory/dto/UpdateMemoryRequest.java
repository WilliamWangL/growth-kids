package com.growthkids.api.memory.dto;

import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * 编辑成长故事请求（TASK-015，对齐成长记录 PATCH 语义 §31）：缺省不变；
 * title 空串清空；content 不可清空（NOT NULL），传空串拒绝。
 */
public record UpdateMemoryRequest(
        LocalDate memoryDate,
        @Size(max = 200, message = "title must be at most 200 characters") String title,
        String content) {}
