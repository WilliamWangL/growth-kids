package com.growthkids.api.common.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.Instant;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 健康检查（无需认证）。 */
@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "服务健康检查")
public class HealthController {

    @GetMapping
    @Operation(summary = "健康检查", description = "返回服务状态与当前时间（UTC）")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "growthkids-api",
                "time", Instant.now().toString());
    }
}
