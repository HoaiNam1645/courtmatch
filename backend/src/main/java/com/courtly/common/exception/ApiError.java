package com.courtly.common.exception;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.Map;

/**
 * Hinh dang loi thong nhat cho toan bo API.
 *
 * @param code        ma loi hang, frontend xu ly theo truong nay
 * @param message     thong bao tieng Viet cho nguoi dung cuoi doc
 * @param fieldErrors loi theo tung o nhap, de frontend hien ngay duoi o do
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(String code,
                       String message,
                       Map<String, String> fieldErrors,
                       Instant timestamp) {

    public static ApiError of(String code, String message, Map<String, String> fieldErrors) {
        return new ApiError(code, message, fieldErrors, Instant.now());
    }
}
