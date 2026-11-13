package com.courtly.service.owner;

import com.courtly.api.owner.dto.OwnerPaymentListResponse;
import com.courtly.common.enums.PaymentStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.venue.VenueRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.16 - chu san theo doi cac thanh toan truc tiep cua cac san minh.
 *
 * <p>Chi doc. Xac nhan da thu tien / tu choi bao thanh toan la 2.3.27.
 */
@Service
@RequiredArgsConstructor
public class OwnerPaymentService {

    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueRepository venueRepository;
    private final PaymentRepository paymentRepository;

    /**
     * @param status all hoac mot trang thai thanh toan
     * @param venueId null = moi san cua chu san
     */
    @Transactional(readOnly = true)
    public OwnerPaymentListResponse list(UUID ownerId, UUID venueId, String status, int page, int size) {
        List<PaymentStatus> statuses = parseStatuses(status);
        List<UUID> venueIds;
        if (venueId == null) {
            venueIds = venueRepository.findIdsByOwnerId(ownerId);
        } else {
            ownedVenueLookup.requireVenue(ownerId, venueId);
            venueIds = List.of(venueId);
        }
        if (venueIds.isEmpty()) {
            return new OwnerPaymentListResponse(List.of(), page, size, 0, 0, emptyCounts());
        }

        Page<Payment> payments = paymentRepository.findForVenues(venueIds, statuses, PageRequest.of(page, size));
        Map<String, Long> counts = emptyCounts();
        paymentRepository.countByStatusForVenues(venueIds)
                .forEach(row -> counts.put(row.getStatus().getValue(), row.getTotal()));

        return new OwnerPaymentListResponse(
                payments.getContent().stream().map(OwnerPaymentService::toItem).toList(),
                payments.getNumber(), payments.getSize(), payments.getTotalElements(), payments.getTotalPages(), counts);
    }

    private static List<PaymentStatus> parseStatuses(String status) {
        if (status == null || status.isBlank() || "all".equals(status)) {
            return List.of(PaymentStatus.values());
        }
        try {
            return List.of(PaymentStatus.from(status));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai thanh toan khong hop le.",
                    Map.of("status", "Chi nhan all hoac mot trang thai thanh toan hop le"));
        }
    }

    /** Du moi trang thai (0 neu chua co) de giao dien hien du cac tab. */
    private static Map<String, Long> emptyCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (PaymentStatus value : PaymentStatus.values()) {
            counts.put(value.getValue(), 0L);
        }
        return counts;
    }

    private static OwnerPaymentListResponse.Item toItem(Payment payment) {
        Booking booking = payment.getBooking();
        return new OwnerPaymentListResponse.Item(
                payment.getId(), booking.getId(), booking.getBookingCode(), booking.getStatus().getValue(),
                booking.getVenue().getId(), booking.getVenue().getName(),
                booking.getUser().getFullName(), booking.getUser().getPhone(),
                payment.getAmount(), payment.getMethod(), payment.getStatus().getValue(),
                payment.getBankName(), payment.getBankAccountNo(), payment.getBankAccountName(), payment.getReferenceContent(),
                payment.getProofUrl(), payment.getPayerReportedAt(), payment.getConfirmedAt(),
                payment.getExpiredAt(), payment.getRejectionReason(), payment.getCreatedAt());
    }
}
