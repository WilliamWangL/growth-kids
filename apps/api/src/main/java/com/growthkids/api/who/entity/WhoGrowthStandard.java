package com.growthkids.api.who.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * who_growth_standards 表（规范 §17）。
 *
 * <p>原始 WHO 数据只读（§11/§31）：业务代码只允许查询，不允许更新/删除；
 * 数据仅通过导入管线写入（同 source+version 幂等）。
 */
@TableName("who_growth_standards")
public class WhoGrowthStandard {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String source;

    private String version;

    private String sex;

    private String indicator;

    private Integer ageDays;

    private BigDecimal lValue;

    private BigDecimal mValue;

    private BigDecimal sValue;

    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getIndicator() {
        return indicator;
    }

    public void setIndicator(String indicator) {
        this.indicator = indicator;
    }

    public Integer getAgeDays() {
        return ageDays;
    }

    public void setAgeDays(Integer ageDays) {
        this.ageDays = ageDays;
    }

    public BigDecimal getLValue() {
        return lValue;
    }

    public void setLValue(BigDecimal lValue) {
        this.lValue = lValue;
    }

    public BigDecimal getMValue() {
        return mValue;
    }

    public void setMValue(BigDecimal mValue) {
        this.mValue = mValue;
    }

    public BigDecimal getSValue() {
        return sValue;
    }

    public void setSValue(BigDecimal sValue) {
        this.sValue = sValue;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
