package com.growthkids.api.prediction;

import com.growthkids.api.common.security.CurrentUser;
import com.growthkids.api.prediction.dto.PredictionResponse;
import com.growthkids.api.prediction.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 孩子档案身高预测 API（TASK-021，规范 §44）：基于已记录的身高序列估算。 */
@RestController
@RequestMapping("/api/v1/children/{childId}/prediction")
@Tag(name = "Prediction", description = "身高预测")
public class ChildPredictionController {

    private final PredictionService predictionService;

    public ChildPredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @GetMapping
    @Operation(
            summary = "孩子身高预测",
            description =
                    "取该孩子全部身高记录（至少 1 条）估算成年身高、区间、当前百分位与逐年预测；"
                            + "记录不足返回 PREDICTION_INSUFFICIENT_DATA")
    public PredictionResponse predict(@PathVariable long childId) {
        return predictionService.estimateForChild(CurrentUser.id(), childId);
    }
}
