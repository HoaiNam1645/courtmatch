package com.courtly.domain.notification;

/**
 * Cac loai thong bao he thong (2.3.53 - 2.3.60).
 *
 * <p>Dung String thay vi enum vi danh sach loai thong bao se con mo rong,
 * va nguoi dung co the bat/tat tung loai trong notification_preferences.
 */
public final class NotificationType {

    public static final String BOOKING_CONFIRMED = "booking_confirmed";
    public static final String BOOKING_CANCELLED = "booking_cancelled";
    public static final String BOOKING_REMINDER = "booking_reminder";
    public static final String PAYMENT_STATUS = "payment_status";
    public static final String PARTNER_REQUEST = "partner_request";
    public static final String PARTNER_REQUEST_RESPONSE = "partner_request_response";
    public static final String MATCH_SCHEDULED = "match_scheduled";
    public static final String MATCH_REMINDER = "match_reminder";

    /** Danh sach day du, dung khi tao notification_preferences mac dinh cho user moi. */
    public static final String[] ALL = {
            BOOKING_CONFIRMED,
            BOOKING_CANCELLED,
            BOOKING_REMINDER,
            PAYMENT_STATUS,
            PARTNER_REQUEST,
            PARTNER_REQUEST_RESPONSE,
            MATCH_SCHEDULED,
            MATCH_REMINDER
    };

    private NotificationType() {
    }
}
