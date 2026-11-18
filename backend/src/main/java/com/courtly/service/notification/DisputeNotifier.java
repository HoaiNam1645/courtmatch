package com.courtly.service.notification;

import com.courtly.domain.notification.NotificationType;
import com.courtly.domain.payment.BookingDispute;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.ZoneId;
import java.util.Locale;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Thong bao ve bao cao don dat san (2.3.85). Goi ben trong transaction cua nghiep vu. */
@Component
@RequiredArgsConstructor
public class DisputeNotifier {

    private static final DateTimeFormatter DEADLINE = DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private static final NumberFormat MONEY = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));

    private final NotificationService notifications;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    /** Chu san giai trinh: bao nguoi choi la bao cao dang duoc quan tri vien xem xet. */
    public void responded(BookingDispute dispute) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeId", dispute.getId().toString());
        data.put("bookingId", dispute.getBooking().getId().toString());
        notifications.send(dispute.getReporter().getId(), NotificationType.BOOKING_DISPUTE_RESPONDED,
                "Chủ sân đã giải trình báo cáo của bạn",
                "Chủ sân đã giải trình về báo cáo đơn %s. Quản trị viên sẽ xem xét và thông báo kết quả cho bạn."
                        .formatted(dispute.getBooking().getBookingCode()),
                "/bao-cao/" + dispute.getId(), data);
    }

    /**
     * Quan tri vien quyet dinh hoan tien (2.3.100-101): bao chu san phai hoan bao nhieu va bao nguoi choi se duoc hoan.
     * Chu san hoan truc tiep (Courtly khong giu tien) nen khong co han cung, chi nhac hoan som va bao lai tren he thong.
     */
    public void refundDecided(BookingDispute dispute, BigDecimal amount, boolean full, String note) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeId", dispute.getId().toString());
        data.put("bookingId", dispute.getBooking().getId().toString());
        data.put("resolution", dispute.getResolution());
        data.put("amount", amount);
        String money = MONEY.format(amount) + "đ";
        String kind = full ? "hoàn tiền toàn phần" : "hoàn tiền một nửa";
        String code = dispute.getBooking().getBookingCode();
        notifications.send(dispute.getBooking().getVenue().getOwner().getId(), NotificationType.BOOKING_DISPUTE_RESOLVED,
                "Bạn cần hoàn tiền cho người chơi",
                "Quản trị viên quyết định %s %s cho đơn %s. Hãy hoàn trực tiếp cho người chơi và báo lại trên hệ thống. Ghi chú: %s"
                        .formatted(kind, money, code, note),
                "/owner/disputes/" + dispute.getId(), data);
        notifications.send(dispute.getReporter().getId(), NotificationType.BOOKING_DISPUTE_RESOLVED,
                "Báo cáo của bạn đã có kết luận",
                "Quản trị viên quyết định %s %s cho đơn %s. Chủ sân sẽ hoàn trực tiếp cho bạn. Ghi chú: %s"
                        .formatted(kind, money, code, note),
                "/bao-cao/" + dispute.getId(), data);
    }

    /** Quan tri vien bac bo / ket luan chi phan anh (2.3.102): bao ca hai ben. */
    public void rejected(BookingDispute dispute, boolean refundWasRequested, String note) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeId", dispute.getId().toString());
        data.put("bookingId", dispute.getBooking().getId().toString());
        data.put("resolution", dispute.getResolution());
        String code = dispute.getBooking().getBookingCode();
        String what = refundWasRequested ? "bác bỏ yêu cầu hoàn tiền" : "đã xem xét báo cáo, không có khoản hoàn tiền";
        notifications.send(dispute.getBooking().getVenue().getOwner().getId(), NotificationType.BOOKING_DISPUTE_RESOLVED,
                "Báo cáo về đơn đã được kết luận",
                "Quản trị viên %s cho đơn %s, bạn không phải hoàn tiền. Ghi chú: %s".formatted(what, code, note),
                "/owner/disputes/" + dispute.getId(), data);
        notifications.send(dispute.getReporter().getId(), NotificationType.BOOKING_DISPUTE_RESOLVED,
                "Báo cáo của bạn đã có kết luận",
                "Quản trị viên %s cho đơn %s. Ghi chú: %s".formatted(what, code, note),
                "/bao-cao/" + dispute.getId(), data);
    }

    private void sendRefund(java.util.UUID userId, java.util.UUID disputeId, java.util.UUID bookingId, String link, String title,
                            String content, BigDecimal amount) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeId", disputeId.toString());
        data.put("bookingId", bookingId.toString());
        data.put("amount", amount);
        notifications.send(userId, NotificationType.BOOKING_REFUND_UPDATED, title, content, link, data);
    }

    private java.util.List<java.util.UUID> admins() {
        return jdbc.queryForList("SELECT u.id FROM users u JOIN user_roles ur ON ur.user_id = u.id JOIN roles r ON r.id = ur.role_id WHERE r.code = 'admin' AND u.status = 'active'",
                java.util.UUID.class);
    }

    /** Chu san bao da hoan tien (2.3.108): nguoi choi xac nhan hoac khieu nai, quan tri vien theo doi. */
    public void refundReported(java.util.UUID disputeId, java.util.UUID bookingId, String code, java.util.UUID playerId, BigDecimal amount, int autoDays) {
        String money = MONEY.format(amount) + "đ";
        sendRefund(playerId, disputeId, bookingId, "/bao-cao/" + disputeId, "Chủ sân báo đã hoàn tiền cho bạn",
                "Chủ sân báo đã hoàn %s cho đơn %s. Hãy xác nhận nếu đã nhận hoặc khiếu nại nếu chưa; sau %d ngày không phản hồi hệ thống sẽ tự xác nhận."
                        .formatted(money, code, autoDays), amount);
        for (java.util.UUID admin : admins()) {
            sendRefund(admin, disputeId, bookingId, "/admin/disputes/" + disputeId, "Chủ sân báo đã hoàn tiền",
                    "Chủ sân báo đã hoàn %s cho đơn %s, chờ người chơi xác nhận.".formatted(money, code), amount);
        }
    }

    /** Nguoi choi khieu nai chua nhan duoc tien (2.3.108). */
    public void refundComplained(java.util.UUID disputeId, java.util.UUID bookingId, String code, java.util.UUID ownerId, BigDecimal amount, String reason) {
        String money = MONEY.format(amount) + "đ";
        sendRefund(ownerId, disputeId, bookingId, "/owner/disputes/" + disputeId, "Người chơi chưa nhận được tiền hoàn",
                "Người chơi báo chưa nhận được %s của đơn %s. Lý do: %s. Hãy kiểm tra và báo lại khi đã hoàn.".formatted(money, code, reason), amount);
        for (java.util.UUID admin : admins()) {
            sendRefund(admin, disputeId, bookingId, "/admin/disputes/" + disputeId, "Người chơi khiếu nại khoản hoàn tiền",
                    "Người chơi chưa nhận được %s của đơn %s. Lý do: %s".formatted(money, code, reason), amount);
        }
    }

    /** Khoan hoan duoc xac nhan (nguoi choi, quan tri vien hoac tu dong): bao chu san va nguoi choi (2.3.108-109). */
    public void refundConfirmed(java.util.UUID disputeId, java.util.UUID bookingId, String code, java.util.UUID ownerId,
                                java.util.UUID playerId, BigDecimal amount, boolean auto, boolean commissionAdjusted) {
        String money = MONEY.format(amount) + "đ";
        String how = auto ? "Hệ thống tự xác nhận vì người chơi không phản hồi" : "Khoản hoàn đã được xác nhận";
        sendRefund(ownerId, disputeId, bookingId, "/owner/disputes/" + disputeId, "Khoản hoàn tiền đã được xác nhận",
                "%s: %s của đơn %s.%s".formatted(how, money, code,
                        commissionAdjusted ? " Phí nền tảng của đơn đã được điều chỉnh theo số tiền hoàn." : ""), amount);
        sendRefund(playerId, disputeId, bookingId, "/bao-cao/" + disputeId, "Khoản hoàn tiền đã hoàn tất",
                "%s: %s của đơn %s.".formatted(how, money, code), amount);
    }

    /** Hoa hong tam hoan quay lai cong no (2.3.110). */
    public void commissionRestored(java.util.UUID ownerId, java.util.UUID disputeId, java.util.UUID bookingId, BigDecimal amount, java.time.Instant dueAt) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeId", disputeId.toString());
        data.put("bookingId", bookingId.toString());
        data.put("amount", amount);
        notifications.send(ownerId, NotificationType.COMMISSION_RESTORED, "Hoa hồng quay lại công nợ",
                "Báo cáo về một đơn của bạn đã kết thúc không phải hoàn tiền nên khoản hoa hồng %sđ được tính lại vào công nợ, hạn %s."
                        .formatted(MONEY.format(amount), DEADLINE.format(dueAt)),
                "/owner/disputes/" + disputeId, data);
    }

    /** Nguoi choi huy bao cao khi chu san chua giai trinh: bao cho chu san. */
    public void cancelled(BookingDispute dispute) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeId", dispute.getId().toString());
        data.put("bookingId", dispute.getBooking().getId().toString());
        notifications.send(dispute.getBooking().getVenue().getOwner().getId(), NotificationType.BOOKING_DISPUTE_CANCELLED,
                "Người chơi đã huỷ báo cáo",
                "Người chơi đã huỷ báo cáo về đơn %s, bạn không cần giải trình nữa."
                        .formatted(dispute.getBooking().getBookingCode()),
                "/owner/bookings", data);
    }

    /** Nguoi choi bo sung thong tin khi bao cao con cho chu san giai trinh: bao cho chu san. */
    public void updated(BookingDispute dispute) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeId", dispute.getId().toString());
        data.put("bookingId", dispute.getBooking().getId().toString());
        notifications.send(dispute.getBooking().getVenue().getOwner().getId(), NotificationType.BOOKING_DISPUTE_UPDATED,
                "Người chơi bổ sung thông tin báo cáo",
                "Người chơi vừa bổ sung thông tin cho báo cáo về đơn %s. Hãy xem trước hạn giải trình %s."
                        .formatted(dispute.getBooking().getBookingCode(), DEADLINE.format(dispute.getOwnerResponseDeadline())),
                "/owner/bookings", data);
    }

    /** Nguoi choi tao bao cao: bao cho chu san cua san va han giai trinh. */
    public void opened(BookingDispute dispute, String categoryLabel) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("disputeId", dispute.getId().toString());
        data.put("bookingId", dispute.getBooking().getId().toString());
        data.put("category", dispute.getCategory());
        notifications.send(dispute.getBooking().getVenue().getOwner().getId(), NotificationType.BOOKING_DISPUTE_OPENED,
                "Có báo cáo mới về đơn đặt sân",
                "Người chơi báo cáo đơn %s tại %s (%s). Hãy giải trình trước %s."
                        .formatted(dispute.getBooking().getBookingCode(), dispute.getBooking().getVenue().getName(), categoryLabel,
                                DEADLINE.format(dispute.getOwnerResponseDeadline())),
                "/owner/bookings", data);
    }
}
