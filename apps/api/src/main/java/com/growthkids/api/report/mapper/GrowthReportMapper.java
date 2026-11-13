package com.growthkids.api.report.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.growthkids.api.report.entity.GrowthReport;
import org.apache.ibatis.annotations.Mapper;

/** growth_reports 读写（TASK-018 年度报告）。 */
@Mapper
public interface GrowthReportMapper extends BaseMapper<GrowthReport> {}
