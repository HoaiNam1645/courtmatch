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
    public static final String PARTNER_REQUEST_EXPIRED = "partner_request_expired";
    /** Chu san tra loi danh gia san cua nguoi choi (2.3.81). */
    public static final String VENUE_REVIEW_REPLIED = "venue_review_replied";
    /** Quan tri vien an danh gia san cua nguoi choi sau khi chu san bao cao (2.3.84). */
    public static final String VENUE_REVIEW_HIDDEN = "venue_review_hidden";
    /** Nguoi choi tao bao cao ve don dat san: bao chu san va han giai trinh (2.3.85). */
    public static final String BOOKING_DISPUTE_OPENED = "booking_dispute_opened";
    public static final String MATCH_SCHEDULED = "match_scheduled";
    public static final String MATCH_REMINDER = "match_reminder";
    /** Han che chu san do cong no hoa hong qua han (B2): bat buoc, khong tat duoc kenh trong ung dung. */
    public static final String DEBT_RESTRICTION = "debt_restriction";

    /** Danh sach day du, dung khi tao notification_preferences mac dinh cho user moi. */
    public static final String[] ALL = {
            BOOKING_CONFIRMED,
            BOOKING_CANCELLED,
            BOOKING_REMINDER,
            PAYMENT_STATUS,
            PARTNER_REQUEST,
            PARTNER_REQUEST_RESPONSE,
            PARTNER_REQUEST_EXPIRED,
            VENUE_REVIEW_REPLIED,
            VENUE_REVIEW_HIDDEN,
            BOOKING_DISPUTE_OPENED,
            MATCH_SCHEDULED,
            MATCH_REMINDER,
            DEBT_RESTRICTION
    };

    /** Loai bat buoc: luon co thong bao trong ung dung du nguoi dung tat tuy chon. */
    public static final java.util.Set<String> MANDATORY = java.util.Set.of(DEBT_RESTRICTION);

    private NotificationType() {
    }
}
