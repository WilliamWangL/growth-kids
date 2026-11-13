package com.growthkids.api.common.error;

import org.springframework.http.HttpStatus;

/** 统一 API 错误码（规范 §69 + 后续任务所需的扩展码）。 */
public enum ApiErrorCode {
    // ---- Auth（§24-§27、§71） ----
    AUTH_INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
    AUTH_TOKEN_EXPIRED(HttpStatus.UNAUTHORIZED, "Token expired"),
    AUTH_UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "Authentication required"),
    AUTH_INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "Invalid token"),
    AUTH_EMAIL_EXISTS(HttpStatus.CONFLICT, "Email already registered"),
    AUTH_ACCOUNT_DISABLED(HttpStatus.FORBIDDEN, "Account is disabled"),
    AUTH_FORBIDDEN(HttpStatus.FORBIDDEN, "Access denied"),
    AUTH_TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts, please try again later"),

    // ---- User（TASK-019 admin 目标用户） ----
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "User not found"),

    // ---- Child（TASK-006） ----
    CHILD_NOT_FOUND(HttpStatus.NOT_FOUND, "Child not found"),
    CHILD_ACCESS_DENIED(HttpStatus.FORBIDDEN, "Access to this child is denied"),
    CHILD_INVALID_DATA(HttpStatus.BAD_REQUEST, "Invalid child data"),
    CHILD_LIMIT_REACHED(HttpStatus.FORBIDDEN, "Child limit reached for current plan"),

    // ---- Growth Record（TASK-007） ----
    GROWTH_RECORD_NOT_FOUND(HttpStatus.NOT_FOUND, "Growth record not found"),
    GROWTH_RECORD_DUPLICATE_DATE(HttpStatus.CONFLICT, "A growth record already exists for this date"),
    GROWTH_RECORD_INVALID_HEIGHT(HttpStatus.BAD_REQUEST, "Invalid height value"),
    GROWTH_RECORD_INVALID_WEIGHT(HttpStatus.BAD_REQUEST, "Invalid weight value"),
    GROWTH_RECORD_INVALID_DATA(HttpStatus.BAD_REQUEST, "Invalid growth record data"),

    // ---- Photo（TASK-013） ----
    PHOTO_NOT_FOUND(HttpStatus.NOT_FOUND, "Photo not found"),
    PHOTO_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "Photo file too large"),
    PHOTO_INVALID_TYPE(HttpStatus.BAD_REQUEST, "Unsupported photo type"),
    PHOTO_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "Photo object has not been uploaded"),
    PHOTO_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "Photo upload failed"),

    // ---- Memory（TASK-015） ----
    MEMORY_NOT_FOUND(HttpStatus.NOT_FOUND, "Memory not found"),
    MEMORY_INVALID_DATA(HttpStatus.BAD_REQUEST, "Invalid memory data"),

    // ---- WHO / Prediction（TASK-008/010） ----
    WHO_DATA_NOT_FOUND(HttpStatus.NOT_FOUND, "WHO growth standard not found"),
    PREDICTION_INSUFFICIENT_DATA(HttpStatus.BAD_REQUEST, "Insufficient data for prediction"),

    // ---- Membership / Payment（TASK-016/017） ----
    MEMBERSHIP_REQUIRED(HttpStatus.FORBIDDEN, "Membership required"),
    MEMBERSHIP_PLAN_NOT_FOUND(HttpStatus.NOT_FOUND, "Membership plan not found"),
    MEMBERSHIP_ALREADY_ACTIVE(HttpStatus.CONFLICT, "An active subscription already exists"),
    MEMBERSHIP_NOT_FOUND(HttpStatus.NOT_FOUND, "No active subscription to cancel"),
    PAYMENT_NOT_FOUND(HttpStatus.NOT_FOUND, "Payment not found"),
    PAYMENT_FAILED(HttpStatus.BAD_REQUEST, "Payment failed"),
    PAYMENT_ALREADY_PROCESSED(HttpStatus.CONFLICT, "Payment already processed"),
    WEBHOOK_INVALID_SIGNATURE(HttpStatus.BAD_REQUEST, "Invalid webhook signature"),

    // ---- Donation（TASK-017，§65） ----
    DONATION_INVALID_AMOUNT(HttpStatus.BAD_REQUEST, "Invalid donation amount"),
    DONATION_DISABLED(HttpStatus.NOT_FOUND, "Donation is currently disabled"),

    // ---- Report（TASK-018，§98/§99） ----
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "Report not found"),
    REPORT_NO_DATA(HttpStatus.BAD_REQUEST, "No growth records in the requested year"),
    REPORT_NOT_READY(HttpStatus.CONFLICT, "Report is not ready yet"),

    // ---- Generic ----
    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Validation failed"),
    BAD_REQUEST(HttpStatus.BAD_REQUEST, "Bad request"),
    NOT_FOUND(HttpStatus.NOT_FOUND, "Resource not found"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed"),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error");

    private final HttpStatus status;
    private final String defaultMessage;

    ApiErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
