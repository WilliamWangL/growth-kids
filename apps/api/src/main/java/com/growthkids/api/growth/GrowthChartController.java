package com.growthkids.api.growth;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.growth.dto.GrowthChartResponse;
import com.growthkids.api.growth.service.GrowthChartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 成长曲线 API（TASK-012，规范 §33/§58）。 */
@RestController
@RequestMapping("/api/v1/children/{childId}/growth-chart")
@Tag(name = "Growth Chart", description = "成长曲线")
public class GrowthChartController {

    private final GrowthChartService growthChartService;

    public GrowthChartController(GrowthChartService growthChartService) {
        this.growthChartService = growthChartService;
    }

    @GetMapping
    @Operation(
            summary = "成长曲线数据",
            description = "身高/体重/BMI/头围四组记录序列（date/ageMonths/value/percentile），按日期升序")
    public GrowthChartResponse chart(@PathVariable long childId) {
        return growthChartService.get(CurrentUser.id(), childId);
    }
}

