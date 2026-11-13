package com.growthkids.api.memory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/** 创建成长故事请求（TASK-015，规范 §114）：标题可选 ≤200，内容必填。 */
public record CreateMemoryRequest(
        @NotNull(message = "memoryDate is required") LocalDate memoryDate,
        @Size(max = 200, message = "title must be at most 200 characters") String title,
        @NotBlank(message = "content is required") String content) {}
