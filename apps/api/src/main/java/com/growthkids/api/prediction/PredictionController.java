package com.growthkids.api.prediction;

import com.growthkids.api.prediction.dto.PredictionRequest;
import com.growthkids.api.prediction.dto.PredictionResponse;
import com.growthkids.api.prediction.service.PredictionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 公开身高计算器 API（TASK-021，规范 §75/§76）：无需登录，用于 SEO 漏斗首环。 */
@RestController
@RequestMapping("/api/v1/prediction")
@Tag(name = "Prediction", description = "身高预测")
public class PredictionController {

    private final PredictionService predictionService;

    public PredictionController(PredictionService predictionService) {
        this.predictionService = predictionService;
    }

    @PostMapping("/estimate")
    @Operation(
            summary = "免费身高预测（无需登录）",
            description =
                    "输入性别/出生日期/当前身高/父母身高，返回预计成年身高、区间、当前百分位与逐年预测（§75）；"
                            + "结果仅为估算（§45）")
    public PredictionResponse estimate(@Valid @RequestBody PredictionRequest request) {
        return predictionService.estimate(request);
    }
}
