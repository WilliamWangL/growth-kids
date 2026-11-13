package com.growthkids.api.growth.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.growthkids.api.children.dto.ChildResponse;
import com.growthkids.api.children.service.ChildService;
import com.growthkids.api.growth.dto.GrowthChartResponse;
import com.growthkids.api.growth.dto.GrowthChartResponse.ChartPoint;
import com.growthkids.api.growth.dto.GrowthChartResponse.MetricSeries;
import com.growthkids.api.growth.entity.GrowthRecord;
import com.growthkids.api.growth.mapper.GrowthRecordMapper;
import com.growthkids.engine.age.AgeCalculator;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import org.springframework.stereotype.Service;

/**
 * 成长曲线服务（TASK-012，规范 §33/§58）：
 * 返回孩子全部成长记录按指标分组的序列（图表场景一次全量，不分页）；
 * ownership 经 ChildService（§70），月龄由 AgeCalculator 按实际日期计算（§37）。
 */
@Service
public class GrowthChartService {

    private final ChildService childService;
    private final GrowthRecordMapper growthRecordMapper;

    public GrowthChartService(ChildService childService, GrowthRecordMapper growthRecordMapper) {
        this.childService = childService;
        this.growthRecordMapper = growthRecordMapper;
    }

    public GrowthChartResponse get(long userId, long childId) {
        ChildResponse child = childService.get(userId, childId);
        List<GrowthRecord> records =
                growthRecordMapper.selectList(
                        new LambdaQueryWrapper<GrowthRecord>()
                                .eq(GrowthRecord::getChildId, childId)
                                .orderByAsc(GrowthRecord::getRecordDate)
                                .orderByAsc(GrowthRecord::getId));
        return new GrowthChartResponse(
                series(records, child, GrowthRecord::getHeightCm, GrowthRecord::getHeightPercentile),
                series(records, child, GrowthRecord::getWeightKg, GrowthRecord::getWeightPercentile),
                series(records, child, GrowthRecord::getBmi, GrowthRecord::getBmiPercentile),
                series(
                        records,
                        child,
                        GrowthRecord::getHeadCircumferenceCm,
                        GrowthRecord::getHeadPercentile));
    }

    /** 单指标序列：跳过未填写该测量的记录；percentile 缺失（unknown 性别）时保留 null。 */
    private static MetricSeries series(
            List<GrowthRecord> records,
            ChildResponse child,
            Function<GrowthRecord, BigDecimal> value,
            Function<GrowthRecord, BigDecimal> percentile) {
        List<ChartPoint> points =
                records.stream()
                        .filter(record -> value.apply(record) != null)
                        .map(
                                record ->
                                        new ChartPoint(
                                                record.getRecordDate(),
                                                ageMonths(child.birthDate(), record.getRecordDate()),
                                                value.apply(record),
                                                percentile.apply(record)))
                        .toList();
        return new MetricSeries(points);
    }

    private static int ageMonths(LocalDate birthDate, LocalDate recordDate) {
        return AgeCalculator.calculate(birthDate, recordDate).totalMonths();
    }
}

