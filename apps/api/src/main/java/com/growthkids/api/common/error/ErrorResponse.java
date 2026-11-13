package com.growthkids.api.common.error;

import java.util.Map;

/** 统一错误响应体（规范 §68）：{ success:false, error:{code,message,details}, requestId }。 */
public record ErrorResponse(boolean success, ErrorBody error, String requestId) {

    public record ErrorBody(String code, String message, Map<String, Object> details) {}

    public static ErrorResponse of(
            ApiErrorCode code, String message, Map<String, Object> details, String requestId) {
        String resolved =
                (message == null || message.isBlank()) ? code.defaultMessage() : message;
        return new ErrorResponse(
                false,
                new ErrorBody(code.name(), resolved, details == null ? Map.of() : details),
                requestId);
    }
}
