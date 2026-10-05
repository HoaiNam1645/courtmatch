package com.courtly.common.exception;

/**
 * Ma loi tra ve cho frontend.
 *
 * <p>Frontend xu ly theo {@code code}, khong doi chieu chuoi {@code message},
 * nen gia tri o day la mot phan cua hop dong API - doi ten la breaking change.
 */
public final class ErrorCode {

    // Chung
    public static final String VALIDATION_FAILED = "VALIDATION_FAILED";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";
    public static final String INTERNAL_ERROR = "INTERNAL_ERROR";

    // Dang ky / dang nhap
    public static final String EMAIL_ALREADY_EXISTS = "EMAIL_ALREADY_EXISTS";
    public static final String PHONE_ALREADY_EXISTS = "PHONE_ALREADY_EXISTS";
    public static final String IDENTITY_REQUIRED = "IDENTITY_REQUIRED";
    public static final String PASSWORD_MISMATCH = "PASSWORD_MISMATCH";
    public static final String INVALID_CREDENTIALS = "INVALID_CREDENTIALS";
    public static final String ACCOUNT_DISABLED = "ACCOUNT_DISABLED";

    // Xac minh email khi dang ky (2.1.1)
    public static final String EMAIL_NOT_VERIFIED = "EMAIL_NOT_VERIFIED";
    public static final String VERIFICATION_CODE_INVALID = "VERIFICATION_CODE_INVALID";
    public static final String VERIFICATION_TOO_MANY_ATTEMPTS = "VERIFICATION_TOO_MANY_ATTEMPTS";

    // Quen & dat lai mat khau (2.1.5)
    public static final String RESET_CHANNEL_UNSUPPORTED = "RESET_CHANNEL_UNSUPPORTED";
    public static final String RESET_CODE_INVALID = "RESET_CODE_INVALID";
    public static final String RESET_TOO_MANY_ATTEMPTS = "RESET_TOO_MANY_ATTEMPTS";
    public static final String RESET_TOO_MANY_REQUESTS = "RESET_TOO_MANY_REQUESTS";
    public static final String RESET_TOKEN_INVALID = "RESET_TOKEN_INVALID";
    public static final String PASSWORD_SAME_AS_OLD = "PASSWORD_SAME_AS_OLD";

    // Dat san
    public static final String BOOKING_SLOT_TAKEN = "BOOKING_SLOT_TAKEN";
    public static final String BOOKING_NOT_CANCELLABLE = "BOOKING_NOT_CANCELLABLE";

    // Thanh toan SePay (2.1.33 - 2.1.42)
    public static final String PAYMENT_NOT_RETRYABLE = "PAYMENT_NOT_RETRYABLE";
    public static final String PAYMENT_GATEWAY_NOT_CONFIGURED = "PAYMENT_GATEWAY_NOT_CONFIGURED";

    private ErrorCode() {
    }
}
