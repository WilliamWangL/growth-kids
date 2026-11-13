package com.growthkids.api.membership.dto;

import jakarta.validation.constraints.NotBlank;

/** 订阅请求（TASK-016）：planCode 为付费套餐 code（如 pro，§64）。 */
public record SubscribeRequest(@NotBlank(message = "planCode is required") String planCode) {}

