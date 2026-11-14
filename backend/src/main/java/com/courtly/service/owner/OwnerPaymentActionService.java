package com.courtly.service.owner;

import com.courtly.api.owner.dto.BookingRequestResponse;
import com.courtly.api.owner.dto.OwnerPaymentDetailResponse;
import com.courtly.api.owner.dto.RejectOwnerPaymentRequest;
import com.courtly.api.payment.dto.RejectPaymentRequest;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.payment.PaymentStatusHistory;
import com.courtly.domain.payment.PaymentStatusHistoryRepository;
import com.courtly.service.audit.AuditService;
import com.courtly.service.payment.PaymentService;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.27 - chu san xem chi tiet va xu ly bao thanh toan truc tiep cua nguoi choi.
 *
 * <p>Xac nhan / tu choi dung chung logic voi {@link PaymentService} de khong co hai luong khac nhau.
 */
@Service
@RequiredArgsConstructor
public class OwnerPaymentActionService {

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final PaymentService paymentService;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public OwnerPaymentDetailResponse get(UUID ownerId, UUID paymentId) {
        return toDetail(requireOwned(ownerId, paymentId), Instant.now());
    }

    /** Xac nhan da thu tien: booking chuyen sang da xac nhan. */
    @Transactional
    public OwnerPaymentDetailResponse confirm(UUID ownerId, UUID paymentId) {
        paymentService.confirmReceived(ownerId, paymentId);
        Payment payment = requireOwned(ownerId, paymentId);
        auditService.record(payment.getRecipientOwner(), "payment.confirm", "payments", paymentId, null,
                Map.of("bookingCode", payment.getBooking().getBookingCode(), "amount", payment.getAmount().toPlainString(),
                        "status", payment.getStatus().getValue()));
        return toDetail(payment, Instant.now());
    }

    /** Tu choi bao da thanh toan kem ly do: nguoi choi co the bao lai. */
    @Transactional
    public OwnerPaymentDetailResponse reject(UUID ownerId, UUID paymentId, RejectOwnerPaymentRequest request) {
        paymentService.rejectReport(ownerId, paymentId, new RejectPaymentRequest(request.reason()));
        Payment payment = requireOwned(ownerId, paymentId);
        auditService.record(payment.getRecipientOwner(), "payment.reject", "payments", paymentId, null,
                Map.of("bookingCode", payment.getBooking().getBookingCode(), "reason", request.reason(),
                        "status", payment.getStatus().getValue()));
        return toDetail(payment, Instant.now());
    }

    private Payment requireOwned(UUID ownerId, UUID paymentId) {
        return paymentRepository.findDetailById(paymentId)
                .filter(payment -> payment.getRecipientOwner().getId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay giao dich thanh toan."));
    }

    private OwnerPaymentDetailResponse toDetail(Payment payment, Instant now) {
        Booking booking = payment.getBooking();
        boolean reported = payment.getStatus() == PaymentStatus.PAYER_REPORTED;
        boolean expired = payment.getExpiredAt() != null && !payment.getExpiredAt().isAfter(now);
        boolean activelyExpired = expired && (payment.getStatus() == PaymentStatus.PAYER_REPORTED
                || payment.getStatus() == PaymentStatus.AWAITING_PAYMENT || payment.getStatus() == PaymentStatus.EXPIRED);
        List<OwnerPaymentDetailResponse.Step> timeline = historyRepository.findTimeline(payment.getId()).stream()
                .map(OwnerPaymentActionService::toStep).toList();
        return new OwnerPaymentDetailResponse(
                payment.getId(), payment.getStatus().getValue(), payment.getMethod(), payment.getAmount(),
                payment.getBankName(), payment.getBankAccountNo(), payment.getBankAccountName(),
                payment.getReferenceContent(), payment.getProofUrl(), payment.getPayerReportedAt(),
                payment.getConfirmedAt(), payment.getRejectedAt(), payment.getRejectionReason(),
                payment.getExpiredAt(), activelyExpired, reported && !expired, reported,
                new OwnerPaymentDetailResponse.Booking(booking.getId(), booking.getBookingCode(),
                        booking.getStatus().getValue(), booking.getVenue().getId(), booking.getVenue().getName(),
                        booking.getUser().getFullName(), booking.getUser().getPhone(), booking.getTotalAmount(),
                        booking.getNote(), booking.getItems().stream()
                        .sorted(Comparator.comparing(BookingItem::getStartTime))
                        .map(item -> new BookingRequestResponse.CourtSlot(item.getCourt().getName(), item.getStartTime(), item.getEndTime()))
                        .toList()),
                timeline);
    }

    private static OwnerPaymentDetailResponse.Step toStep(PaymentStatusHistory history) {
        return new OwnerPaymentDetailResponse.Step(
                history.getOldStatus() == null ? null : history.getOldStatus().getValue(),
                history.getNewStatus().getValue(), history.getReason(),
                history.getChangedBy() == null ? null : history.getChangedBy().getFullName(),
                history.getChangedBy() == null, history.getCreatedAt());
    }
}
