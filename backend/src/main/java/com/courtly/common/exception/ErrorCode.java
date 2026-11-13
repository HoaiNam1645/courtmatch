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
    public static final String METHOD_NOT_ALLOWED = "METHOD_NOT_ALLOWED";

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

    // Dat san va thanh toan truc tiep cho chu san
    public static final String PAYMENT_STATUS_INVALID = "PAYMENT_STATUS_INVALID";
    public static final String BOOKING_REVIEW_INVALID = "BOOKING_REVIEW_INVALID";

    // Du lieu tran dau Sprint 2
    public static final String MATCH_PLAYERS_INVALID = "MATCH_PLAYERS_INVALID";
    public static final String MATCH_SCORE_INVALID = "MATCH_SCORE_INVALID";
    public static final String MATCH_STATE_INVALID = "MATCH_STATE_INVALID";
    public static final String MATCH_ALREADY_RECORDED = "MATCH_ALREADY_RECORDED";

    // Tim kiem doi tac Sprint 2
    public static final String PARTNER_LOCATION_REQUIRED = "PARTNER_LOCATION_REQUIRED";

    // Yeu cau ghep cap doi tac Sprint 2
    public static final String PARTNER_REQUEST_DUPLICATE = "PARTNER_REQUEST_DUPLICATE";
    public static final String PARTNER_ALREADY_CONNECTED = "PARTNER_ALREADY_CONNECTED";
    public static final String PARTNER_REQUEST_STATE_INVALID = "PARTNER_REQUEST_STATE_INVALID";

    // Danh gia doi tac sau tran dau Sprint 2
    public static final String PARTNER_REVIEW_DUPLICATE = "PARTNER_REVIEW_DUPLICATE";
    public static final String PARTNER_REVIEW_NOT_ALLOWED = "PARTNER_REVIEW_NOT_ALLOWED";

    // Chu san Sprint 3
    public static final String PASSWORD_CHANGE_REQUIRED = "PASSWORD_CHANGE_REQUIRED";
    public static final String CURRENT_PASSWORD_INVALID = "CURRENT_PASSWORD_INVALID";
    public static final String COURT_HAS_UPCOMING_BOOKINGS = "COURT_HAS_UPCOMING_BOOKINGS";
    public static final String PRICE_RULE_OVERLAP = "PRICE_RULE_OVERLAP";
    public static final String PRICE_RULE_STATE_INVALID = "PRICE_RULE_STATE_INVALID";
    public static final String BLOCK_HAS_BOOKINGS = "BLOCK_HAS_BOOKINGS";
    public static final String BLOCK_OVERLAP = "BLOCK_OVERLAP";
    public static final String IMAGE_INVALID = "IMAGE_INVALID";
    public static final String IMAGE_LIMIT_REACHED = "IMAGE_LIMIT_REACHED";

    private ErrorCode() {
    }
}
