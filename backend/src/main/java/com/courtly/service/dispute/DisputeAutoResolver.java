package com.courtly.service.dispute;

import com.courtly.domain.payment.BookingDispute;
import com.courtly.domain.payment.BookingDisputeRepository;
import com.courtly.service.audit.AuditService;
import com.courtly.service.notification.DisputeNotifier;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.107 - xu ly mot bao cao ma chu san khong giai trinh truoc han (moi bao cao mot transaction, khoa dong, chi mot lan).
 *
 * <p>Co yeu cau hoan tien va don co khoan thanh toan da xac nhan con hoan duoc: tu quyet dinh hoan toan phan dung so nguoi choi yeu cau
 * (khong vuot so con hoan duoc) nhu 2.3.100, tao khoan hoan cho chu san hoan. Con lai (chi phan anh, hoac khong con gi de hoan): ket luan
 * {@code owner_no_response} va khoi phuc hoa hong. Ca hai deu tinh la mot lan xu thua cua san (2.3.104). {@code handled_by} de trong de
 * danh dau do he thong tu xu ly.
 */
@Service
@RequiredArgsConstructor
public class DisputeAutoResolver {

    static final String NOTE = "Chủ sân không giải trình trong 48 giờ, hệ thống tự xử lý.";

    private final BookingDisputeRepository disputeRepository;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final DisputeNotifier notifier;
    private final DisputeCommissionService commissionService;
    private final DisputeVenueSuspensionService suspensionService;

    /** @return true neu da xu ly bao cao nay */
    @Transactional
    public boolean resolve(UUID disputeId, Instant now) {
        BookingDispute dispute = disputeRepository.findByIdForUpdate(disputeId).orElse(null);
        if (dispute == null || !"awaiting_owner_response".equals(dispute.getStatus()) || dispute.getOwnerResponse() != null
                || dispute.getOwnerResponseDeadline() == null || dispute.getOwnerResponseDeadline().isAfter(now)) {
            return false;
        }
        UUID bookingId = dispute.getBooking().getId();
        BigDecimal amount = null;
        if (dispute.isRefundRequested() && dispute.getRequestedRefundAmount() != null) {
            List<UUID> payments = jdbc.query("SELECT id FROM payments WHERE booking_id = ? AND status IN ('confirmed', 'partially_refunded') ORDER BY created_at DESC FOR UPDATE",
                    (rs, row) -> rs.getObject("id", UUID.class), bookingId);
            if (!payments.isEmpty()) {
                BigDecimal paid = jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM payments WHERE booking_id = ? AND status IN ('confirmed', 'partially_refunded', 'refunded')", BigDecimal.class, bookingId);
                BigDecimal refunded = jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM refunds WHERE booking_id = ? AND status NOT IN ('rejected', 'failed')", BigDecimal.class, bookingId);
                BigDecimal refundable = paid.subtract(refunded);
                if (refundable.signum() > 0) {
                    amount = DisputeRefundAmounts.full(dispute.getRequestedRefundAmount(), refundable);
                    jdbc.update("INSERT INTO refunds (payment_id, booking_id, amount, reason, status, requested_by, dispute_id) VALUES (?, ?, ?, ?, 'awaiting_owner_refund', ?, ?)",
                            payments.getFirst(), bookingId, amount, NOTE, dispute.getReporter().getId(), disputeId);
                }
            }
        }
        dispute.setStatus("resolved");
        dispute.setResolution(amount != null ? "refund_full" : "owner_no_response");
        dispute.setResolutionNote(NOTE);
        dispute.setHandledBy(null);
        dispute.setHandledAt(now);
        disputeRepository.saveAndFlush(dispute);
        if (amount == null) {
            commissionService.restore(bookingId, disputeId, "Chu san khong giai trinh, bao cao ket thuc khong hoan tien");
        }
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("status", "resolved");
        data.put("resolution", dispute.getResolution());
        data.put("bookingId", bookingId.toString());
        if (amount != null) {
            data.put("amount", amount);
        }
        auditService.record(null, "dispute.auto_resolve", "booking_disputes", disputeId, Map.of("status", "awaiting_owner_response"), data);
        notifier.autoResolved(dispute, amount);
        suspensionService.evaluate(dispute.getBooking().getVenue().getId());
        return true;
    }
}
