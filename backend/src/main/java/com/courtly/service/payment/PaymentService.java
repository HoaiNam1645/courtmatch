package com.courtly.service.payment;

import com.courtly.api.payment.dto.PaymentDetailResponse;
import com.courtly.api.payment.dto.RejectPaymentRequest;
import com.courtly.api.payment.dto.ReportPaymentRequest;
import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingStatusHistory;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.payment.PaymentStatusHistory;
import com.courtly.domain.payment.PaymentStatusHistoryRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Records payments made directly to the venue owner; Courtly never receives the funds. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentService {

    public static final int PAYMENT_WINDOW_MINUTES = 30;

    private final PaymentRepository paymentRepository;
    private final PaymentStatusHistoryRepository historyRepository;
    private final CourtOwnerProfileRepository ownerProfileRepository;
    private final UserRepository userRepository;

    /** Creates payment instructions only after the owner accepts the booking. */
    public Payment createFor(Booking booking, Instant now) {
        User owner = booking.getVenue().getOwner();
        CourtOwnerProfile profile = ownerProfileRepository.findById(owner.getId()).orElse(null);

        Payment payment = new Payment();
        payment.setId(UUID.randomUUID());
        payment.setBooking(booking);
        payment.setRecipientOwner(owner);
        payment.setMethod("owner_bank_transfer");
        payment.setAmount(booking.getTotalAmount());
        payment.setStatus(PaymentStatus.AWAITING_PAYMENT);
        payment.setBankName(profile == null ? null : profile.getBankName());
        payment.setBankAccountNo(profile == null ? null : profile.getBankAccountNo());
        payment.setBankAccountName(profile == null ? null : profile.getBankAccountName());
        payment.setReferenceContent(referenceContentOf(booking.getBookingCode()));
        payment.setExpiredAt(booking.getPaymentExpiresAt());
        payment.setCreatedAt(now);
        payment = paymentRepository.save(payment);
        addHistory(payment, null, PaymentStatus.AWAITING_PAYMENT, "Chu san da chap nhan booking", owner, now);
        return payment;
    }

    @Transactional(readOnly = true)
    public PaymentDetailResponse getDetail(UUID userId, UUID paymentId) {
        return toResponse(requireVisiblePayment(userId, paymentId), Instant.now());
    }

    @Transactional(readOnly = true)
    public PaymentDetailResponse getByBooking(UUID userId, UUID bookingId) {
        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId)
                .orElseThrow(() -> ApiException.notFound("Don nay chua co huong dan thanh toan."));
        requireParticipant(userId, payment);
        return toResponse(payment, Instant.now());
    }

    @Transactional(readOnly = true)
    public List<PaymentDetailResponse> listForOwner(UUID ownerId, String statusValue) {
        PaymentStatus status;
        try {
            status = PaymentStatus.from(statusValue);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Trang thai thanh toan khong hop le.", Map.of("status", exception.getMessage()));
        }
        Instant now = Instant.now();
        return paymentRepository.findForOwnerByStatus(ownerId, status).stream()
                .map(payment -> toResponse(payment, now))
                .toList();
    }

    /** Player reports a direct payment; this alone does not confirm the booking. */
    @Transactional
    public PaymentDetailResponse reportPaid(UUID userId, UUID paymentId, ReportPaymentRequest request) {
        Payment payment = requireVisiblePayment(userId, paymentId);
        if (!payment.getBooking().getUser().getId().equals(userId)) {
            throw ApiException.notFound("Khong tim thay giao dich thanh toan.");
        }
        if (payment.getStatus() != PaymentStatus.AWAITING_PAYMENT
                && payment.getStatus() != PaymentStatus.REJECTED) {
            throw ApiException.conflict(ErrorCode.PAYMENT_STATUS_INVALID,
                    "Giao dich khong o trang thai co the bao da thanh toan.", Map.of());
        }

        Instant now = Instant.now();
        if (payment.getExpiredAt() != null && !payment.getExpiredAt().isAfter(now)) {
            throw ApiException.conflict(ErrorCode.PAYMENT_STATUS_INVALID,
                    "Giao dich da qua han thanh toan.", Map.of());
        }
        PaymentStatus previous = payment.getStatus();
        payment.setMethod(request.method());
        payment.setProofUrl(blankToNull(request.proofUrl()));
        payment.setPayerReportedAt(now);
        payment.setRejectedBy(null);
        payment.setRejectedAt(null);
        payment.setRejectionReason(null);
        payment.setStatus(PaymentStatus.PAYER_REPORTED);
        addHistory(payment, previous, PaymentStatus.PAYER_REPORTED,
                "Nguoi choi bao da thanh toan", payment.getBooking().getUser(), now);
        return toResponse(payment, now);
    }

    /** Owner confirms receipt; this is the only payment action that confirms the booking. */
    @Transactional
    public PaymentDetailResponse confirmReceived(UUID ownerId, UUID paymentId) {
        Payment payment = requireOwnerPayment(ownerId, paymentId);
        if (payment.getStatus() != PaymentStatus.PAYER_REPORTED) {
            throw ApiException.conflict(ErrorCode.PAYMENT_STATUS_INVALID,
                    "Chi xac nhan duoc khi nguoi choi da bao thanh toan.", Map.of());
        }

        User owner = requireUser(ownerId);
        Instant now = Instant.now();
        if (payment.getExpiredAt() != null && !payment.getExpiredAt().isAfter(now)) {
            throw ApiException.conflict(ErrorCode.PAYMENT_STATUS_INVALID,
                    "Giao dich da qua han; can xu ly thu cong neu tien den muon.", Map.of());
        }
        payment.setStatus(PaymentStatus.CONFIRMED);
        payment.setConfirmedBy(owner);
        payment.setConfirmedAt(now);
        addHistory(payment, PaymentStatus.PAYER_REPORTED, PaymentStatus.CONFIRMED,
                "Chu san xac nhan da thu tien", owner, now);

        Booking booking = payment.getBooking();
        booking.setStatus(BookingStatus.CONFIRMED);
        booking.setConfirmedAt(now);
        booking.setPaymentExpiresAt(null);
        booking.getItems().forEach(item -> item.setStatus(BookingItemStatus.CONFIRMED));
        addBookingHistory(booking, BookingStatus.AWAITING_PAYMENT, BookingStatus.CONFIRMED,
                "Chu san xac nhan da thu tien", owner, now);
        return toResponse(payment, now);
    }

    @Transactional
    public PaymentDetailResponse rejectReport(UUID ownerId, UUID paymentId, RejectPaymentRequest request) {
        Payment payment = requireOwnerPayment(ownerId, paymentId);
        if (payment.getStatus() != PaymentStatus.PAYER_REPORTED) {
            throw ApiException.conflict(ErrorCode.PAYMENT_STATUS_INVALID,
                    "Chi tu choi duoc bao cao thanh toan dang cho xac nhan.", Map.of());
        }

        User owner = requireUser(ownerId);
        Instant now = Instant.now();
        payment.setStatus(PaymentStatus.REJECTED);
        payment.setRejectedBy(owner);
        payment.setRejectedAt(now);
        payment.setRejectionReason(request.reason().trim());
        addHistory(payment, PaymentStatus.PAYER_REPORTED, PaymentStatus.REJECTED,
                request.reason().trim(), owner, now);
        return toResponse(payment, now);
    }

    @Transactional
    public int expireOverduePayments() {
        Instant now = Instant.now();
        List<Payment> overdue = paymentRepository.findExpiredAwaiting(now);
        for (Payment payment : overdue) {
            PaymentStatus previous = payment.getStatus();
            payment.setStatus(PaymentStatus.EXPIRED);
            addHistory(payment, previous, PaymentStatus.EXPIRED, "Qua han thanh toan", null, now);
        }
        if (!overdue.isEmpty()) {
            log.info("Dong {} giao dich qua han", overdue.size());
        }
        return overdue.size();
    }

    static String referenceContentOf(String bookingCode) {
        return bookingCode.replaceAll("[^A-Za-z0-9]", "");
    }

    private Payment requireVisiblePayment(UUID userId, UUID paymentId) {
        Payment payment = paymentRepository.findDetailById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay giao dich thanh toan."));
        requireParticipant(userId, payment);
        return payment;
    }

    private Payment requireOwnerPayment(UUID ownerId, UUID paymentId) {
        Payment payment = paymentRepository.findDetailById(paymentId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay giao dich thanh toan."));
        if (!payment.getRecipientOwner().getId().equals(ownerId)) {
            throw ApiException.notFound("Khong tim thay giao dich thanh toan.");
        }
        return payment;
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
    }

    private static void requireParticipant(UUID userId, Payment payment) {
        if (!payment.getBooking().getUser().getId().equals(userId)
                && !payment.getRecipientOwner().getId().equals(userId)) {
            throw ApiException.notFound("Khong tim thay giao dich thanh toan.");
        }
    }

    private void addHistory(Payment payment, PaymentStatus from, PaymentStatus to,
                            String reason, User changedBy, Instant at) {
        PaymentStatusHistory history = new PaymentStatusHistory();
        history.setId(UUID.randomUUID());
        history.setPayment(payment);
        history.setOldStatus(from);
        history.setNewStatus(to);
        history.setReason(reason);
        history.setChangedBy(changedBy);
        history.setCreatedAt(at);
        historyRepository.save(history);
    }

    private static void addBookingHistory(Booking booking, BookingStatus from, BookingStatus to,
                                          String reason, User changedBy, Instant at) {
        BookingStatusHistory history = new BookingStatusHistory();
        history.setId(UUID.randomUUID());
        history.setBooking(booking);
        history.setOldStatus(from);
        history.setNewStatus(to);
        history.setReason(reason);
        history.setChangedBy(changedBy);
        history.setActorRole("owner");
        history.setCreatedAt(at);
        booking.getStatusHistory().add(history);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private static PaymentDetailResponse toResponse(Payment payment, Instant now) {
        long secondsRemaining = 0;
        if ((payment.getStatus() == PaymentStatus.AWAITING_PAYMENT
                || payment.getStatus() == PaymentStatus.REJECTED)
                && payment.getExpiredAt() != null) {
            secondsRemaining = Math.max(0, ChronoUnit.SECONDS.between(now, payment.getExpiredAt()));
        }
        Booking booking = payment.getBooking();
        return new PaymentDetailResponse(
                payment.getId(), booking.getId(), booking.getBookingCode(), payment.getMethod(),
                payment.getAmount(), payment.getStatus(), payment.getBankName(),
                payment.getBankAccountNo(), payment.getBankAccountName(), payment.getReferenceContent(),
                payment.getProofUrl(), payment.getPayerReportedAt(), payment.getConfirmedAt(),
                payment.getRejectionReason(), payment.getExpiredAt(), secondsRemaining,
                payment.getCreatedAt(), payment.getUpdatedAt());
    }
}
