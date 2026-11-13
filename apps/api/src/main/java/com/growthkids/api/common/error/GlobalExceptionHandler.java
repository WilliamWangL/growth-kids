package com.growthkids.api.common.error;

import com.growthkids.api.common.web.RequestIdFilter;
import jakarta.validation.ConstraintViolationException;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 全局异常处理：所有错误统一转为规范 §68 响应体。 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ErrorResponse> handleApiException(ApiException ex) {
        log.debug("API exception: code={}, message={}", ex.code(), ex.getMessage());
        return build(ex.code(), ex.getMessage(), ex.details());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(AccessDeniedException ex) {
        return build(ApiErrorCode.AUTH_FORBIDDEN, null, Map.of());
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<ErrorResponse> handleBind(BindException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            details.putIfAbsent(fieldError.getField(), fieldError.getDefaultMessage());
        }
        return build(ApiErrorCode.VALIDATION_ERROR, null, details);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        ex.getConstraintViolations()
                .forEach(
                        violation ->
                                details.putIfAbsent(
                                        violation.getPropertyPath().toString(), violation.getMessage()));
        return build(ApiErrorCode.VALIDATION_ERROR, null, details);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleNotReadable(HttpMessageNotReadableException ex) {
        return build(ApiErrorCode.VALIDATION_ERROR, "Malformed request body", Map.of());
    }

    @ExceptionHandler({MissingServletRequestParameterException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErrorResponse> handleBadRequest(Exception ex) {
        return build(ApiErrorCode.VALIDATION_ERROR, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return build(ApiErrorCode.PHOTO_TOO_LARGE, null, Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> handleNoResource(NoResourceFoundException ex) {
        return build(ApiErrorCode.NOT_FOUND, null, Map.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        Map<String, Object> details = new LinkedHashMap<>();
        if (ex.getSupportedHttpMethods() != null) {
            details.put("supported", ex.getSupportedHttpMethods().stream().map(Object::toString).toList());
        }
        return build(ApiErrorCode.METHOD_NOT_ALLOWED, null, details);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unhandled exception [requestId={}]", RequestIdFilter.currentRequestId(), ex);
        return build(ApiErrorCode.INTERNAL_ERROR, null, Map.of());
    }

    private ResponseEntity<ErrorResponse> build(
            ApiErrorCode code, String message, Map<String, Object> details) {
        ErrorResponse body = ErrorResponse.of(code, message, details, RequestIdFilter.currentRequestId());
        return ResponseEntity.status(code.status()).body(body);
    }
}
