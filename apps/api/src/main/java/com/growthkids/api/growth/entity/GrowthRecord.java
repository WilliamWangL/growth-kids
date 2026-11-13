package com.growthkids.api.growth.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * growth_records 表（规范 §14）。
 *
 * <p>age_days / bmi 在写入时由 growth-engine 计算（§29/§36/§37）；
 * z-score / percentile / who_version 在 TASK-009 接入（§34/§35）。
 */
@TableName("growth_records")
public class GrowthRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long childId;

    private LocalDate recordDate;

    /** 记录时点年龄总天数（规范 §37 的 totalDays，用于 WHO 数据匹配）。 */
    private Integer ageDays;

    private BigDecimal heightCm;

    private BigDecimal weightKg;

    private BigDecimal headCircumferenceCm;

    private BigDecimal bmi;

    private BigDecimal heightZ;

    private BigDecimal heightPercentile;

    private BigDecimal weightZ;

    private BigDecimal weightPercentile;

    private BigDecimal bmiZ;

    private BigDecimal bmiPercentile;

    private BigDecimal headZ;

    private BigDecimal headPercentile;

    private String whoVersion;

    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getChildId() {
        return childId;
    }

    public void setChildId(Long childId) {
        this.childId = childId;
    }

    public LocalDate getRecordDate() {
        return recordDate;
    }

    public void setRecordDate(LocalDate recordDate) {
        this.recordDate = recordDate;
    }

    public Integer getAgeDays() {
        return ageDays;
    }

    public void setAgeDays(Integer ageDays) {
        this.ageDays = ageDays;
    }

    public BigDecimal getHeightCm() {
        return heightCm;
    }

    public void setHeightCm(BigDecimal heightCm) {
        this.heightCm = heightCm;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public void setWeightKg(BigDecimal weightKg) {
        this.weightKg = weightKg;
    }

    public BigDecimal getHeadCircumferenceCm() {
        return headCircumferenceCm;
    }

    public void setHeadCircumferenceCm(BigDecimal headCircumferenceCm) {
        this.headCircumferenceCm = headCircumferenceCm;
    }

    public BigDecimal getBmi() {
        return bmi;
    }

    public void setBmi(BigDecimal bmi) {
        this.bmi = bmi;
    }

    public BigDecimal getHeightZ() {
        return heightZ;
    }

    public void setHeightZ(BigDecimal heightZ) {
        this.heightZ = heightZ;
    }

    public BigDecimal getHeightPercentile() {
        return heightPercentile;
    }

    public void setHeightPercentile(BigDecimal heightPercentile) {
        this.heightPercentile = heightPercentile;
    }

    public BigDecimal getWeightZ() {
        return weightZ;
    }

    public void setWeightZ(BigDecimal weightZ) {
        this.weightZ = weightZ;
    }

    public BigDecimal getWeightPercentile() {
        return weightPercentile;
    }

    public void setWeightPercentile(BigDecimal weightPercentile) {
        this.weightPercentile = weightPercentile;
    }

    public BigDecimal getBmiZ() {
        return bmiZ;
    }

    public void setBmiZ(BigDecimal bmiZ) {
        this.bmiZ = bmiZ;
    }

    public BigDecimal getBmiPercentile() {
        return bmiPercentile;
    }

    public void setBmiPercentile(BigDecimal bmiPercentile) {
        this.bmiPercentile = bmiPercentile;
    }

    public BigDecimal getHeadZ() {
        return headZ;
    }

    public void setHeadZ(BigDecimal headZ) {
        this.headZ = headZ;
    }

    public BigDecimal getHeadPercentile() {
        return headPercentile;
    }

    public void setHeadPercentile(BigDecimal headPercentile) {
        this.headPercentile = headPercentile;
    }

    public String getWhoVersion() {
        return whoVersion;
    }

    public void setWhoVersion(String whoVersion) {
        this.whoVersion = whoVersion;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
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
