package com.courtly.service.dispute;

import com.courtly.api.dispute.dto.CreateDisputeRequest;
import com.courtly.api.dispute.dto.DisputeCategoryResponse;
import com.courtly.api.dispute.dto.DisputeDetailResponse;
import com.courtly.api.dispute.dto.DisputeResponse;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.payment.BookingDispute;
import com.courtly.domain.payment.BookingDisputeRepository;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.service.notification.DisputeNotifier;
import com.courtly.common.enums.PaymentStatus;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nguoi choi bao cao van de ve don dat san (2.3.85, 2.3.86).
 *
 * <p>Chi nguoi dat, don da xac nhan hoac hoan thanh, trong {@code report-window-days} ngay ke tu gio ket thuc; moi don mot bao cao dang
 * xu ly. Tao xong cho chu san giai trinh trong {@code owner-response-hours} gio. Chua doi trang thai don hay hoa hong (thuoc 2.3.105-106).
 */
@Service
@RequiredArgsConstructor
public class BookingDisputeService {

    public static final Set<BookingStatus> REPORTABLE = Set.of(BookingStatus.CONFIRMED, BookingStatus.COMPLETED);
    static final int MAX_DESCRIPTION = 2000;
    static final BigDecimal MIN_REFUND = new BigDecimal("1000");

    private final BookingRepository bookingRepository;
    private final BookingDisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final DisputeNotifier disputeNotifier;
    private final com.courtly.service.audit.AuditService auditService;
    private final DisputeCommissionService commissionService;
    @Value("${courtly.disputes.report-window-days:7}")
    private int windowDays;
    @Value("${courtly.disputes.owner-response-hours:48}")
    private int ownerResponseHours;

    /** 2.3.86 - danh muc loai bao cao. */
    public List<DisputeCategoryResponse> categories() {
        return DisputeCategories.ALL;
    }

    /** 2.3.85 - tao bao cao ve mot don cua chinh minh. */
    @Transactional
    public DisputeResponse create(UUID userId, UUID bookingId, CreateDisputeRequest request) {
        Booking booking = bookingRepository.findById(bookingId)
                .filter(item -> item.getUser().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay don dat san nay."));

        DisputeCategoryResponse category = DisputeCategories.find(request.category());
        if (category == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Loai bao cao khong hop le.",
                    Map.of("category", "Hay chon mot loai trong danh muc"));
        }
        String description = request.description() == null ? "" : request.description().trim();
        if (description.length() < category.minDescriptionLength()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Vui long mo ta ro hon.",
                    Map.of("description", "Mo ta it nhat " + category.minDescriptionLength() + " ky tu"));
        }
        if (description.length() > MAX_DESCRIPTION) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Mo ta qua dai.",
                    Map.of("description", "Mo ta toi da " + MAX_DESCRIPTION + " ky tu"));
        }

        boolean wantsRefund = Boolean.TRUE.equals(request.refundRequested());
        if (!wantsRefund && request.refundAmount() != null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "So tien hoan chi nhap khi yeu cau hoan tien.",
                    Map.of("refundAmount", "Hay danh dau yeu cau hoan tien hoac bo trong so tien"));
        }

        Instant now = Instant.now();
        if (!REPORTABLE.contains(booking.getStatus())) {
            throw ApiException.conflict(ErrorCode.BOOKING_DISPUTE_INVALID,
                    "Chi bao cao duoc don da xac nhan hoac da hoan thanh.", Map.of());
        }
        if (!now.isBefore(reportDeadline(booking))) {
            throw ApiException.conflict(ErrorCode.BOOKING_DISPUTE_INVALID,
                    "Da qua " + windowDays + " ngay ke tu gio ket thuc, khong the bao cao don nay nua.", Map.of());
        }
        if (disputeRepository.findActiveByBooking(bookingId).isPresent()) {
            throw ApiException.conflict(ErrorCode.BOOKING_DISPUTE_INVALID,
                    "Don nay da co bao cao dang duoc xu ly.", Map.of());
        }

        Payment payment = latestPayment(bookingId);
        BigDecimal refundAmount = wantsRefund ? requireRefundAmount(booking, payment, request.refundAmount()) : null;

        BookingDispute dispute = new BookingDispute();
        dispute.setId(UUID.randomUUID());
        dispute.setBooking(booking);
        dispute.setReporter(booking.getUser());
        dispute.setPayment(payment);
        dispute.setRefundRequested(wantsRefund);
        dispute.setRequestedRefundAmount(refundAmount);
        dispute.setCategory(category.code());
        dispute.setDescription(description);
        dispute.setStatus("awaiting_owner_response");
        dispute.setOwnerResponseDeadline(now.plus(Duration.ofHours(ownerResponseHours)));
        disputeRepository.saveAndFlush(dispute);
        commissionService.hold(booking.getId(), dispute.getId());
        disputeNotifier.opened(dispute, category.label());
        return toResponse(dispute, category.label());
    }

    /**
     * 2.3.87 - chi yeu cau hoan duoc khi nguoi choi da tra tien (payment confirmed); so tien tu 1.000d toi so da tra
     * (sau voucher), de trong thi lay toan bo. Chi ghi nhan yeu cau, chua dong toi hoa hong hay tao ban ghi hoan tien.
     */
    private static BigDecimal requireRefundAmount(Booking booking, Payment payment, BigDecimal requested) {
        if (payment == null || payment.getStatus() != PaymentStatus.CONFIRMED) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Don chua co khoan thanh toan duoc xac nhan nen khong the yeu cau hoan tien.",
                    Map.of("refundRequested", "Chi yeu cau hoan tien khi chu san da xac nhan nhan tien"));
        }
        BigDecimal paid = booking.getPayableAmount();
        BigDecimal amount = requested == null ? paid : requested.setScale(2, RoundingMode.HALF_UP);
        if (amount.compareTo(MIN_REFUND) < 0 || amount.compareTo(paid) > 0) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "So tien hoan khong hop le.",
                    Map.of("refundAmount", "So tien tu " + MIN_REFUND.stripTrailingZeros().toPlainString() + "d toi "
                            + paid.stripTrailingZeros().toPlainString() + "d (so tien da tra)"));
        }
        return amount;
    }

    /**
     * 2.3.92 - nguoi bao cao huy bao cao khi chu san chua giai trinh. Giu nguyen noi dung, anh va cac lan bo sung lam lich su;
     * chi doi trang thai sang cancelled, ghi audit va bao chu san.
     */
    @Transactional
    public DisputeDetailResponse cancel(UUID userId, UUID disputeId) {
        BookingDispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .filter(item -> item.getReporter().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay bao cao."));
        if (!"awaiting_owner_response".equals(dispute.getStatus()) || dispute.getOwnerResponse() != null) {
            throw ApiException.conflict(ErrorCode.BOOKING_DISPUTE_INVALID,
                    "Chi huy duoc bao cao khi chu san chua giai trinh.", Map.of());
        }
        dispute.setStatus("cancelled");
        disputeRepository.saveAndFlush(dispute);
        auditService.record(dispute.getReporter(), "dispute.cancel", "booking_disputes", dispute.getId(),
                Map.of("status", "awaiting_owner_response"),
                Map.of("status", "cancelled", "bookingId", dispute.getBooking().getId().toString(),
                        "refundRequested", dispute.isRefundRequested()));
        commissionService.restore(dispute.getBooking().getId(), dispute.getId(), "Nguoi choi huy bao cao");
        disputeNotifier.cancelled(dispute);
        return DisputeQueryService.toDetail(dispute);
    }

    /** Han cuoi de bao cao don: gio ket thuc cuoi cung cong so ngay cho phep. */
    public Instant reportDeadline(Booking booking) {
        Instant end = booking.getItems().stream().map(BookingItem::getEndTime).max(Comparator.naturalOrder())
                .orElse(booking.getCreatedAt());
        return end.plus(Duration.ofDays(windowDays));
    }

    /** Don co the bao cao ngay luc nay (dung trang thai, con han, chua co bao cao dang xu ly). */
    public boolean canReport(Booking booking, boolean hasActiveDispute, Instant now) {
        return REPORTABLE.contains(booking.getStatus()) && !hasActiveDispute && now.isBefore(reportDeadline(booking));
    }

    /** Chu san da xac nhan nhan tien cua don: dieu kien de tick yeu cau hoan tien (2.3.87). */
    public boolean hasConfirmedPayment(UUID bookingId) {
        Payment payment = latestPayment(bookingId);
        return payment != null && payment.getStatus() == PaymentStatus.CONFIRMED;
    }

    public Optional<BookingDispute> findActive(UUID bookingId) {
        return disputeRepository.findActiveByBooking(bookingId);
    }

    private Payment latestPayment(UUID bookingId) {
        return paymentRepository.findByBookingIds(List.of(bookingId)).stream()
                .max(Comparator.comparing(Payment::getCreatedAt)).orElse(null);
    }

    static DisputeResponse toResponse(BookingDispute dispute, String categoryLabel) {
        return new DisputeResponse(dispute.getId(), dispute.getBooking().getId(), dispute.getBooking().getBookingCode(),
                dispute.getCategory(), categoryLabel, dispute.getDescription(), dispute.getStatus(),
                dispute.isRefundRequested(), dispute.getRequestedRefundAmount(),
                dispute.getOwnerResponseDeadline(), dispute.getCreatedAt(),
                DisputeEvidenceService.evidencesOf(dispute));
    }
}
