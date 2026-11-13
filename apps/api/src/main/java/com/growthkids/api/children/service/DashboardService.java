package com.growthkids.api.children.service;

import com.growthkids.api.children.dto.ChildResponse;
import com.growthkids.api.children.dto.DashboardResponse;
import com.growthkids.api.growth.dto.GrowthRecordResponse;
import com.growthkids.api.growth.service.GrowthRecordService;
import com.growthkids.engine.age.AgeCalculator;
import com.growthkids.engine.age.AgeResult;
import java.time.LocalDate;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;

/**
 * 孩子主页摘要服务（TASK-011，规范 §54/§112）。
 *
 * <p>ownership 校验走 {@link ChildService#get}（§70：非本人孩子一律 404）；
 * 年龄按服务器 UTC 当天计算（§37 实际日期口径）。
 */
@Service
public class DashboardService {

    private final ChildService childService;
    private final GrowthRecordService growthRecordService;

    public DashboardService(ChildService childService, GrowthRecordService growthRecordService) {
        this.childService = childService;
        this.growthRecordService = growthRecordService;
    }

    public DashboardResponse get(long userId, long childId) {
        ChildResponse child = childService.get(userId, childId);
        GrowthRecordResponse latestRecord = growthRecordService.getLatest(userId, childId);
        AgeResult age = AgeCalculator.calculate(child.birthDate(), LocalDate.now(ZoneOffset.UTC));
        return new DashboardResponse(
                child,
                new DashboardResponse.Age(age.years(), age.months(), age.days(), age.totalMonths()),
                latestRecord);
    }
}
