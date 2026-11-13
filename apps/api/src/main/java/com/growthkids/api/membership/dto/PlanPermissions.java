package com.growthkids.api.membership.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.growthkids.api.membership.MembershipProperties;
import java.util.Map;

/**
 * 套餐权益（TASK-016，§64）：由 membership_plans.features JSON 解析（配置化 §78，
 * 不硬编码）；解析失败或缺失字段时回退 app.membership 默认值（Free）。
 */
public record PlanPermissions(
        long childLimit,
        long photoLimit,
        long timelineLimit,
        boolean unlimitedRecords,
        boolean advancedPrediction,
        boolean annualReport,
        boolean pdfExport,
        boolean poster,
        boolean dataExport,
        boolean aiSummary) {

    /** -1 表示不限（Pro 语义，§64）。 */
    public static final long UNLIMITED = -1L;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static PlanPermissions fromFeatures(String featuresJson, MembershipProperties fallback) {
        if (featuresJson == null || featuresJson.isBlank()) {
            return freeDefaults(fallback);
        }
        try {
            Map<?, ?> features = MAPPER.readValue(featuresJson, Map.class);
            return new PlanPermissions(
                    longValue(features, "childLimit", fallback.getFreeChildLimit()),
                    longValue(features, "photoLimit", fallback.getFreePhotoLimit()),
                    longValue(features, "timelineLimit", fallback.getFreeTimelineLimit()),
                    boolValue(features, "unlimitedRecords"),
                    boolValue(features, "advancedPrediction"),
                    boolValue(features, "annualReport"),
                    boolValue(features, "pdfExport"),
                    boolValue(features, "poster"),
                    boolValue(features, "dataExport"),
                    boolValue(features, "aiSummary"));
        } catch (Exception e) {
            return freeDefaults(fallback);
        }
    }

    public static PlanPermissions freeDefaults(MembershipProperties properties) {
        return new PlanPermissions(
                properties.getFreeChildLimit(),
                properties.getFreePhotoLimit(),
                properties.getFreeTimelineLimit(),
                false,
                false,
                false,
                false,
                false,
                false,
                false);
    }

    private static long longValue(Map<?, ?> features, String key, long fallback) {
        Object value = features.get(key);
        return value instanceof Number number ? number.longValue() : fallback;
    }

    private static boolean boolValue(Map<?, ?> features, String key) {
        return Boolean.TRUE.equals(features.get(key));
    }
}

