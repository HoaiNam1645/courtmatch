package com.courtly.common.exception;

import java.util.Map;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/** Loi nghiep vu co ma loi va HTTP status xac dinh truoc. */
@Getter
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Map<String, String> fieldErrors;

    public ApiException(HttpStatus status, String code, String message) {
        this(status, code, message, Map.of());
    }

    public ApiException(HttpStatus status, String code, String message, Map<String, String> fieldErrors) {
        super(message);
        this.status = status;
        this.code = code;
        this.fieldErrors = fieldErrors;
    }

    public static ApiException conflict(String code, String message, Map<String, String> fieldErrors) {
        return new ApiException(HttpStatus.CONFLICT, code, message, fieldErrors);
    }

    public static ApiException badRequest(String code, String message, Map<String, String> fieldErrors) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, fieldErrors);
    }

    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    public static ApiException forbidden(String code, String message) {
        return new ApiException(HttpStatus.FORBIDDEN, code, message);
    }

    /** Qua nhieu lan thu trong mot khoang thoi gian - dung cho gui lai ma, nhap sai ma. */
    public static ApiException tooManyRequests(String code, String message, Map<String, String> fieldErrors) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message, fieldErrors);
    }

    public static ApiException notFound(String message) {
        return new ApiException(HttpStatus.NOT_FOUND, ErrorCode.NOT_FOUND, message);
    }
}
