package com.growthkids.api.children.dto;

import com.growthkids.api.children.entity.Child;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

/** 孩子信息响应（id 为字符串；birthDate 为 DATE 不做 UTC 转换，规范 §128）。 */
public record ChildResponse(
        String id,
        String name,
        String gender,
        LocalDate birthDate,
        BigDecimal birthHeightCm,
        BigDecimal birthWeightKg,
        BigDecimal fatherHeightCm,
        BigDecimal motherHeightCm,
        String avatarUrl,
        String notes,
        Instant createdAt,
        Instant updatedAt) {

    public static ChildResponse from(Child child) {
        return new ChildResponse(
                String.valueOf(child.getId()),
                child.getName(),
                child.getGender(),
                child.getBirthDate(),
                child.getBirthHeightCm(),
                child.getBirthWeightKg(),
                child.getFatherHeightCm(),
                child.getMotherHeightCm(),
                child.getAvatarUrl(),
                child.getNotes(),
                child.getCreatedAt() == null ? null : child.getCreatedAt().toInstant(ZoneOffset.UTC),
                child.getUpdatedAt() == null ? null : child.getUpdatedAt().toInstant(ZoneOffset.UTC));
    }
}
