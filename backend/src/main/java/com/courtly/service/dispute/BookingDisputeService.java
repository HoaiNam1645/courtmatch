package com.courtly.service.dispute;

import com.courtly.api.dispute.dto.CreateDisputeRequest;
import com.courtly.api.dispute.dto.DisputeCategoryResponse;
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

    private final BookingRepository bookingRepository;
    private final BookingDisputeRepository disputeRepository;
    private final PaymentRepository paymentRepository;
    private final DisputeNotifier disputeNotifier;
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

        BookingDispute dispute = new BookingDispute();
        dispute.setId(UUID.randomUUID());
        dispute.setBooking(booking);
        dispute.setReporter(booking.getUser());
        dispute.setPayment(latestPayment(bookingId));
        dispute.setCategory(category.code());
        dispute.setDescription(description);
        dispute.setStatus("awaiting_owner_response");
        dispute.setOwnerResponseDeadline(now.plus(Duration.ofHours(ownerResponseHours)));
        disputeRepository.saveAndFlush(dispute);
        disputeNotifier.opened(dispute, category.label());
        return toResponse(dispute, category.label());
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
                dispute.getOwnerResponseDeadline(), dispute.getCreatedAt());
    }
}
