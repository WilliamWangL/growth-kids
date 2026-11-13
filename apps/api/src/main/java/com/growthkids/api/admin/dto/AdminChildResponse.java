package com.growthkids.api.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 孩子列表行（§77 children）：admin 视角含已软删除（status=deleted）的孩子。 */
public record AdminChildResponse(
        long id,
        long userId,
        String ownerEmail,
        String name,
        String gender,
        LocalDate birthDate,
        String status,
        LocalDateTime createdAt) {}
