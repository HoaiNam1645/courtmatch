package com.courtly.service.ai;

/** AI service khong dung duoc cho yeu cau nay; {@code reason} la ma ngan ghi vao nhat ky (timeout, connection, http_503, invalid_result...). */
public class AiUnavailableException extends RuntimeException {

    private final String reason;

    public AiUnavailableException(String reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public AiUnavailableException(String reason, String message) {
        this(reason, message, null);
    }

    public String reason() {
        return reason;
    }
}
