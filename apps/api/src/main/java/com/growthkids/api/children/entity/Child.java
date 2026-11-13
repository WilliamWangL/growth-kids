package com.growthkids.api.children.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * children 表（规范 §13）。
 * status 为软删除字段（§28：删除默认软删除），@TableLogic 使所有查询自动过滤已删除数据。
 */
@TableName("children")
public class Child {

    public static final String STATUS_ACTIVE = "active";
    public static final String STATUS_DELETED = "deleted";

    public static final String GENDER_MALE = "male";
    public static final String GENDER_FEMALE = "female";
    public static final String GENDER_UNKNOWN = "unknown";

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String name;

    private String gender;

    private LocalDate birthDate;

    private BigDecimal birthHeightCm;

    private BigDecimal birthWeightKg;

    private BigDecimal fatherHeightCm;

    private BigDecimal motherHeightCm;

    private String avatarUrl;

    private String notes;

    @TableLogic(value = "active", delval = "deleted")
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public BigDecimal getBirthHeightCm() {
        return birthHeightCm;
    }

    public void setBirthHeightCm(BigDecimal birthHeightCm) {
        this.birthHeightCm = birthHeightCm;
    }

    public BigDecimal getBirthWeightKg() {
        return birthWeightKg;
    }

    public void setBirthWeightKg(BigDecimal birthWeightKg) {
        this.birthWeightKg = birthWeightKg;
    }

    public BigDecimal getFatherHeightCm() {
        return fatherHeightCm;
    }

    public void setFatherHeightCm(BigDecimal fatherHeightCm) {
        this.fatherHeightCm = fatherHeightCm;
    }

    public BigDecimal getMotherHeightCm() {
        return motherHeightCm;
    }

    public void setMotherHeightCm(BigDecimal motherHeightCm) {
        this.motherHeightCm = motherHeightCm;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
