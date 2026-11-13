package com.courtly.service.owner;

import com.courtly.api.owner.dto.BookingRequestResponse;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.service.audit.AuditService;
import com.courtly.service.booking.AvailabilityService;
import com.courtly.service.booking.BookingService;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.12 - chu san xem cac yeu cau dat san.
 *
 * <p>Chi doc: xac nhan / tu choi la 2.3.13 va 2.3.14.
 */
@Service
@RequiredArgsConstructor
public class OwnerBookingRequestService {

    private static final Instant FAR_PAST = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueRepository venueRepository;
    private final BookingRepository bookingRepository;
    private final BookingService bookingService;
    private final AuditService auditService;

    /**
     * @param venueId null = moi san cua chu san
     * @param status  pending_owner_confirmation (mac dinh), mot trang thai khac, hoac "all"
     * @param date    ngay choi theo gio Viet Nam; null = moi ngay
     */
    @Transactional(readOnly = true)
    public BookingRequestResponse list(UUID ownerId, UUID venueId, String status, LocalDate date, int page, int size) {
        List<UUID> ownedVenueIds = venueRepository.findIdsByOwnerId(ownerId);
        List<UUID> venueIds;
        if (venueId == null) {
            venueIds = ownedVenueIds;
        } else {
            ownedVenueLookup.requireVenue(ownerId, venueId);
            venueIds = List.of(venueId);
        }
        List<BookingStatus> statuses = parseStatuses(status);

        Instant now = Instant.now();
        long awaiting = ownedVenueIds.isEmpty() ? 0 : bookingRepository.countAwaitingOwner(ownedVenueIds);
        long expiringSoon = ownedVenueIds.isEmpty() ? 0 : bookingRepository.countExpiringSoon(
                ownedVenueIds, now, now.plus(Duration.ofMinutes(BookingRequestResponse.EXPIRING_SOON_MINUTES)));
        if (venueIds.isEmpty()) {
            return new BookingRequestResponse(List.of(), page, size, 0, 0, awaiting, expiringSoon);
        }

        Instant from = date == null ? FAR_PAST : date.atStartOfDay(AvailabilityService.ZONE).toInstant();
        Instant to = date == null ? FAR_FUTURE : date.plusDays(1).atStartOfDay(AvailabilityService.ZONE).toInstant();
        Page<Booking> bookings = bookingRepository.findRequestsForOwner(venueIds, statuses, from, to, PageRequest.of(page, size));

        return new BookingRequestResponse(
                bookings.getContent().stream().map(booking -> toItem(booking, now)).toList(),
                bookings.getNumber(), bookings.getSize(), bookings.getTotalElements(), bookings.getTotalPages(),
                awaiting, expiringSoon);
    }

    /**
     * Xac nhan yeu cau (2.3.13): booking sang awaiting_payment, ghi nguoi xac nhan, thoi diem va
     * han thanh toan, tao huong dan thanh toan truc tiep. Logic chuyen trang thai dung chung voi
     * BookingService de khong co hai luong duyet khac nhau.
     */
    @Transactional
    public BookingRequestResponse.Item accept(UUID ownerId, UUID bookingId) {
        bookingService.accept(ownerId, bookingId);
        Booking booking = reload(bookingId);
        auditService.record(booking.getVenue().getOwner(), "booking.accept", "bookings", bookingId, null,
                Map.of("venueId", booking.getVenue().getId().toString(), "bookingCode", booking.getBookingCode(),
                        "status", booking.getStatus().getValue()));
        return toItem(booking, Instant.now());
    }

    private Booking reload(UUID bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay don dat san."));
    }

    private static List<BookingStatus> parseStatuses(String status) {
        if (status == null || status.isBlank()) {
            return List.of(BookingStatus.PENDING_OWNER_CONFIRMATION);
        }
        if ("all".equals(status)) {
            return List.of(BookingStatus.values());
        }
        try {
            return List.of(BookingStatus.from(status));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai booking khong hop le.",
                    Map.of("status", "Chi nhan all hoac mot trang thai booking hop le"));
        }
    }

    private static BookingRequestResponse.Item toItem(Booking booking, Instant now) {
        Instant respondBy = booking.getStatus() == BookingStatus.PENDING_OWNER_CONFIRMATION
                ? booking.getOwnerResponseExpiresAt() : null;
        long remaining = respondBy == null ? 0 : Math.max(0, Duration.between(now, respondBy).getSeconds());
        boolean expiringSoon = respondBy != null && remaining > 0
                && remaining <= BookingRequestResponse.EXPIRING_SOON_MINUTES * 60L;
        return new BookingRequestResponse.Item(
                booking.getId(), booking.getBookingCode(), booking.getStatus().getValue(),
                booking.getVenue().getId(), booking.getVenue().getName(),
                booking.getUser().getFullName(), booking.getUser().getPhone(),
                booking.getItems().stream()
                        .sorted(Comparator.comparing(BookingItem::getStartTime))
                        .map(item -> new BookingRequestResponse.CourtSlot(
                                item.getCourt().getName(), item.getStartTime(), item.getEndTime()))
                        .toList(),
                booking.getTotalAmount(), booking.getNote(), respondBy, remaining, expiringSoon, booking.getCreatedAt(),
                booking.getOwnerRespondedAt(),
                booking.getOwnerRespondedBy() == null ? null : booking.getOwnerRespondedBy().getFullName(),
                booking.getStatus() == BookingStatus.AWAITING_PAYMENT ? booking.getPaymentExpiresAt() : null,
                booking.getRejectionReason());
    }
}
