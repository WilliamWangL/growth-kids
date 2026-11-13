package com.growthkids.api.common.error;

import java.util.Map;

/** 业务异常：携带统一错误码，可由 GlobalExceptionHandler 转为规范 §68 错误响应。 */
public class ApiException extends RuntimeException {

    private final ApiErrorCode code;
    private final transient Map<String, Object> details;

    public ApiException(ApiErrorCode code) {
        this(code, code.defaultMessage(), Map.of());
    }

    public ApiException(ApiErrorCode code, String message) {
        this(code, message, Map.of());
    }

    public ApiException(ApiErrorCode code, String message, Map<String, Object> details) {
        super(message);
        this.code = code;
        this.details = details == null ? Map.of() : Map.copyOf(details);
    }

    public ApiErrorCode code() {
        return code;
    }

    public Map<String, Object> details() {
        return details;
    }
}
