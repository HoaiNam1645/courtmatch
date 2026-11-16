package com.courtly.service.notification;

import com.courtly.domain.notification.NotificationType;
import com.courtly.domain.payment.Payment;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thong bao ve thanh toan (2.3.55): nguoi choi bao da thanh toan, chu san tu choi, Courtly xu ly cong no hoa hong,
 * va han che cong no (B2). Goi ben trong transaction cua nghiep vu.
 */
@Component
@RequiredArgsConstructor
public class PaymentNotifier {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final NotificationService notifications;

    /** Nguoi choi bao da thanh toan: chu san can doi chieu va xac nhan. */
    public void playerReported(Payment payment) {
        var booking = payment.getBooking();
        notifications.send(payment.getRecipientOwner().getId(), NotificationType.PAYMENT_STATUS, "Người chơi báo đã thanh toán",
                "%s báo đã thanh toán %s cho booking %s tại %s. Hãy đối chiếu và xác nhận đã nhận tiền."
                        .formatted(booking.getUser().getFullName(), money(payment.getAmount()), booking.getBookingCode(),
                                booking.getVenue().getName()),
                "/owner/payments", paymentData(payment));
    }

    /** Chu san tu choi bao cao thanh toan: nguoi choi can bao lai hoac lien he chu san. */
    public void ownerRejected(Payment payment, String reason) {
        var booking = payment.getBooking();
        notifications.send(booking.getUser().getId(), NotificationType.PAYMENT_STATUS, "Chủ sân chưa nhận được tiền",
                "Chủ sân từ chối báo cáo thanh toán %s của booking %s. Lý do: %s. Bạn có thể báo lại trước khi hết hạn thanh toán."
                        .formatted(money(payment.getAmount()), booking.getBookingCode(), reason),
                "/payments/" + payment.getId(), paymentData(payment));
    }

    /**
     * Courtly xac nhan chu san da thanh toan cong no hoa hong (2.3.47, 2.3.49).
     *
     * @param settlementPeriod mo ta ky doi soat, null neu tra chung
     * @param offset           phan Courtly no chu san da bu tru, 0 neu khong co
     */
    public void commissionPaymentConfirmed(UUID ownerId, UUID reportId, BigDecimal amount, String reference, String settlementPeriod,
                                           BigDecimal offset) {
        String target = settlementPeriod == null ? "vào công nợ chung" : "cho kỳ " + settlementPeriod;
        String bonus = offset.signum() > 0 ? " Courtly đã bù trừ thêm %s vào công nợ của bạn.".formatted(money(offset)) : "";
        notifications.send(ownerId, NotificationType.PAYMENT_STATUS, "Courtly đã xác nhận thanh toán công nợ",
                "Khoản thanh toán %s (mã %s) %s đã được xác nhận.%s".formatted(money(amount), reference, target, bonus),
                "/owner/finance?tab=payment-history", Map.of("reportId", reportId.toString(), "referenceCode", reference));
    }

    /** Courtly tu choi bao cao thanh toan cong no (2.3.48): chu san can bo sung bang chung. */
    public void commissionPaymentRejected(UUID ownerId, UUID reportId, BigDecimal amount, String reference, String reason) {
        notifications.send(ownerId, NotificationType.PAYMENT_STATUS, "Courtly từ chối báo cáo thanh toán công nợ",
                "Báo cáo thanh toán %s (mã %s) bị từ chối. Lý do: %s. Công nợ chưa thay đổi, bạn có thể gửi lại kèm bằng chứng đầy đủ."
                        .formatted(money(amount), reference, reason),
                "/owner/finance?tab=payment-history", Map.of("reportId", reportId.toString(), "referenceCode", reference));
    }

    /** Han che do cong no qua han doi muc (B2): bat buoc, luon co thong bao trong ung dung. */
    public void restrictionChanged(UUID ownerId, int from, int to, BigDecimal overdueAmount) {
        String title;
        String content;
        if (to == 0) {
            title = "Đã gỡ hạn chế do công nợ";
            content = "Công nợ quá hạn của bạn đã xuống dưới ngưỡng, sân hoạt động và nhận booking bình thường trở lại.";
        } else if (to == 1) {
            title = to > from ? "Bị hạn chế nhận booking mới do công nợ" : "Hạn chế công nợ giảm xuống mức 1";
            content = "Công nợ hoa hồng quá hạn %s. Bạn tạm thời không nhận được booking mới; booking đã nhận vẫn xử lý bình thường. Hãy thanh toán sớm để được gỡ hạn chế."
                    .formatted(money(overdueAmount));
        } else {
            title = "Sân bị tạm ẩn do công nợ quá hạn";
            content = "Công nợ hoa hồng quá hạn %s. Sân của bạn tạm thời bị ẩn khỏi danh sách công khai và không nhận booking mới. Hãy thanh toán để được gỡ hạn chế."
                    .formatted(money(overdueAmount));
        }
        notifications.send(ownerId, NotificationType.DEBT_RESTRICTION, title, content, "/owner/finance?tab=debt",
                Map.of("level", to, "previousLevel", from));
    }

    private static Map<String, Object> paymentData(Payment payment) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("paymentId", payment.getId().toString());
        data.put("bookingId", payment.getBooking().getId().toString());
        data.put("bookingCode", payment.getBooking().getBookingCode());
        return data;
    }

    /** 30000 -> "30.000đ". */
    static String money(BigDecimal amount) {
        return String.format(Locale.of("vi", "VN"), "%,.0f", amount) + "đ";
    }

    /** Khoang ngay cua ky doi soat dang dd/MM/yyyy – dd/MM/yyyy. */
    public static String period(LocalDate from, LocalDate to) {
        return DAY.format(from) + " – " + DAY.format(to);
    }
}
