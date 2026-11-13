package com.growthkids.api.timeline;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.timeline.dto.TimelineResponse;
import com.growthkids.api.timeline.service.TimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 成长时间线 API（TASK-014，规范 §52/§113）。 */
@RestController
@RequestMapping("/api/v1/children/{childId}/timeline")
@Tag(name = "Timeline", description = "成长时间线")
public class TimelineController {

    private final TimelineService timelineService;

    public TimelineController(TimelineService timelineService) {
        this.timelineService = timelineService;
    }

    @GetMapping
    @Operation(
            summary = "合并时间线",
            description = "成长记录 + 成长故事（含关联照片签名 URL），按日期倒序（§52）")
    public TimelineResponse timeline(@PathVariable long childId) {
        return timelineService.get(CurrentUser.id(), childId);
    }
}
