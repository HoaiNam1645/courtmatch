package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminDisputeResponses.Detail;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.payment.BookingDispute;
import com.courtly.domain.payment.BookingDisputeRepository;
import com.courtly.service.audit.AuditService;
import com.courtly.service.dispute.DisputeRefundAmounts;
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
 * 2.3.100 - 2.3.101: quan tri vien quyet dinh hoan tien cho nguoi choi (toan phan hoac mot nua).
 *
 * <p>Courtly khong giu tien nen khong chuyen tien: tao mot khoan hoan {@code awaiting_owner_refund} de chu san hoan truc tiep
 * (viec chu san bao da hoan va xac nhan la 2.3.108). So tien tinh tren so nguoi choi da yeu cau (xem {@link DisputeRefundAmounts}),
 * khong vuot so da thu con co the hoan. Chua dieu chinh hoa hong o buoc nay (2.3.105, 2.3.109, 2.3.110).
 */
@Service
@RequiredArgsConstructor
public class AdminDisputeDecisionService {

    static final String FULL = "refund_full";
    static final String HALF = "refund_half";

    private final AdminAccessService accessService;
    private final BookingDisputeRepository disputeRepository;
    private final AdminDisputeService viewService;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final DisputeNotifier disputeNotifier;
    private final com.courtly.service.dispute.DisputeCommissionService commissionService;
    private final com.courtly.service.dispute.DisputeVenueSuspensionService suspensionService;

    @Transactional
    public Detail refundFull(UUID adminId, UUID disputeId, String note) {
        return refund(adminId, disputeId, note, true);
    }

    @Transactional
    public Detail refundHalf(UUID adminId, UUID disputeId, String note) {
        return refund(adminId, disputeId, note, false);
    }

    /** 2.3.102 - bac bo yeu cau hoan tien (hoac ket luan bao cao chi phan anh khong co khoan hoan). */
    @Transactional
    public Detail reject(UUID adminId, UUID disputeId, String note) {
        User admin = accessService.requirePermission(adminId, AdminDisputeService.PERMISSION);
        BookingDispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay bao cao."));
        String cleanNote = requireNote(note);
        if (!"reviewing".equals(dispute.getStatus())) {
            throw invalid("Chi quyet dinh duoc bao cao dang cho quan tri vien xu ly.");
        }
        boolean refundRequested = dispute.isRefundRequested();
        dispute.setStatus("rejected");
        dispute.setResolution(refundRequested ? "rejected" : "dismissed");
        dispute.setResolutionNote(cleanNote);
        dispute.setHandledBy(admin);
        dispute.setHandledAt(Instant.now());
        disputeRepository.saveAndFlush(dispute);
        commissionService.restore(dispute.getBooking().getId(), disputeId, "Bao cao bi bac bo");
        auditService.record(admin, "dispute.reject", "booking_disputes", disputeId,
                Map.of("status", "reviewing"),
                Map.of("status", "rejected", "resolution", dispute.getResolution(), "note", cleanNote,
                        "bookingId", dispute.getBooking().getId().toString()));
        disputeNotifier.rejected(dispute, refundRequested, cleanNote);
        return viewService.toDetail(dispute);
    }

    private static String requireNote(String note) {
        String clean = note == null ? "" : note.trim();
        if (clean.length() < 5 || clean.length() > 1000) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ghi chu quyet dinh khong hop le.",
                    Map.of("note", "Ghi chu tu 5 den 1000 ky tu"));
        }
        return clean;
    }

    private Detail refund(UUID adminId, UUID disputeId, String note, boolean full) {
        User admin = accessService.requirePermission(adminId, AdminDisputeService.PERMISSION);
        BookingDispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay bao cao."));
        String cleanNote = note == null ? "" : note.trim();
        if (cleanNote.length() < 5 || cleanNote.length() > 1000) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ghi chu quyet dinh khong hop le.",
                    Map.of("note", "Ghi chu tu 5 den 1000 ky tu"));
        }
        if (!"reviewing".equals(dispute.getStatus())) {
            throw invalid("Chi quyet dinh duoc bao cao dang cho quan tri vien xu ly.");
        }
        if (!dispute.isRefundRequested() || dispute.getRequestedRefundAmount() == null) {
            throw invalid("Bao cao nay khong co yeu cau hoan tien.");
        }
        UUID bookingId = dispute.getBooking().getId();
        List<UUID> payments = jdbc.query("SELECT id FROM payments WHERE booking_id = ? AND status IN ('confirmed', 'partially_refunded') ORDER BY created_at DESC FOR UPDATE",
                (rs, row) -> rs.getObject("id", UUID.class), bookingId);
        if (payments.isEmpty()) {
            throw invalid("Don chua co khoan thanh toan duoc chu san xac nhan nen khong the hoan tien.");
        }
        BigDecimal paid = jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM payments WHERE booking_id = ? AND status IN ('confirmed', 'partially_refunded', 'refunded')",
                BigDecimal.class, bookingId);
        BigDecimal refunded = jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM refunds WHERE booking_id = ? AND status NOT IN ('rejected', 'failed')",
                BigDecimal.class, bookingId);
        BigDecimal refundable = paid.subtract(refunded);
        if (refundable.signum() <= 0) {
            throw invalid("So tien da thu cua don da duoc hoan het.");
        }
        BigDecimal requested = dispute.getRequestedRefundAmount();
        BigDecimal amount = full ? DisputeRefundAmounts.full(requested, refundable) : DisputeRefundAmounts.half(requested, refundable);
        String resolution = full ? FULL : HALF;

        dispute.setStatus("resolved");
        dispute.setResolution(resolution);
        dispute.setResolutionNote(cleanNote);
        dispute.setHandledBy(admin);
        dispute.setHandledAt(Instant.now());
        disputeRepository.saveAndFlush(dispute);

        jdbc.update("""
                INSERT INTO refunds (payment_id, booking_id, amount, reason, status, requested_by, dispute_id, approved_by)
                VALUES (?, ?, ?, ?, 'awaiting_owner_refund', ?, ?, ?)
                """, payments.getFirst(), bookingId, amount, cleanNote, dispute.getReporter().getId(), disputeId, adminId);
        auditService.record(admin, "dispute." + resolution, "booking_disputes", disputeId,
                Map.of("status", "reviewing"),
                Map.of("status", "resolved", "resolution", resolution, "amount", amount, "requested", requested, "note", cleanNote,
                        "bookingId", bookingId.toString()));
        disputeNotifier.refundDecided(dispute, amount, full, cleanNote);
        suspensionService.evaluate(dispute.getBooking().getVenue().getId());
        return viewService.toDetail(dispute);
    }

    private static ApiException invalid(String message) {
        return ApiException.conflict(ErrorCode.BOOKING_DISPUTE_INVALID, message, Map.of());
    }
}
