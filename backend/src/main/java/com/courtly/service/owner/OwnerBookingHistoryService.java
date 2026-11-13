package com.courtly.service.owner;

import com.courtly.api.owner.dto.BookingRequestResponse;
import com.courtly.api.owner.dto.OwnerBookingDetailResponse;
import com.courtly.api.owner.dto.OwnerBookingHistoryItem;
import com.courtly.common.PageResponse;
import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.BookingStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingRepository;
import com.courtly.domain.booking.BookingStatusHistory;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.service.booking.AvailabilityService;
import com.courtly.service.finance.CommissionService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.15 - chu san xem lich su dat san cua cac san minh. */
@Service
@RequiredArgsConstructor
public class OwnerBookingHistoryService {

    private static final Instant FAR_PAST = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    /** Nhom trang thai o man hinh lich su. */
    static final Map<String, List<BookingStatus>> GROUPS = groups();

    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueRepository venueRepository;
    private final BookingRepository bookingRepository;
    private final PaymentRepository paymentRepository;
    private final BookingCommissionRepository commissionRepository;
    private final PlatformFeeConfigRepository feeConfigRepository;

    private static Map<String, List<BookingStatus>> groups() {
        Map<String, List<BookingStatus>> map = new LinkedHashMap<>();
        map.put("all", List.of(BookingStatus.values()));
        map.put("active", List.of(BookingStatus.PENDING_OWNER_CONFIRMATION, BookingStatus.AWAITING_PAYMENT,
                BookingStatus.CONFIRMED));
        map.put("completed", List.of(BookingStatus.COMPLETED));
        map.put("closed", List.of(BookingStatus.REJECTED, BookingStatus.CANCELLED, BookingStatus.EXPIRED,
                BookingStatus.REFUNDED));
        map.put("disputed", List.of(BookingStatus.DISPUTED));
        return map;
    }

    /**
     * @param group nhom trang thai: all, active, completed, closed, disputed
     * @param q     tu khoa tim theo ma booking hoac ten nguoi dat
     * @param from  loc theo ngay choi (gio Viet Nam), tinh ca ngay nay; null = khong loc
     * @param to    nhu from, tinh ca ngay nay
     */
    @Transactional(readOnly = true)
    public PageResponse<OwnerBookingHistoryItem> list(UUID ownerId, UUID venueId, String group, String q,
                                                      LocalDate from, LocalDate to, int page, int size) {
        List<BookingStatus> statuses = GROUPS.get(group == null ? "all" : group);
        if (statuses == null) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Nhom trang thai khong hop le.",
                    Map.of("group", "Chi nhan " + String.join(", ", GROUPS.keySet())));
        }
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khoang ngay khong hop le.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        List<UUID> venueIds;
        if (venueId == null) {
            venueIds = venueRepository.findIdsByOwnerId(ownerId);
        } else {
            ownedVenueLookup.requireVenue(ownerId, venueId);
            venueIds = List.of(venueId);
        }
        if (venueIds.isEmpty()) {
            return new PageResponse<>(List.of(), page, size, 0, 0);
        }

        String keyword = q == null || q.isBlank() ? "%" : "%" + q.trim() + "%";
        Instant start = from == null ? FAR_PAST : from.atStartOfDay(AvailabilityService.ZONE).toInstant();
        Instant end = to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay(AvailabilityService.ZONE).toInstant();
        Page<Booking> bookings = bookingRepository.searchHistoryForOwner(
                venueIds, statuses, keyword, start, end, PageRequest.of(page, size));

        Map<UUID, Payment> latestPayment = bookings.isEmpty() ? Map.of()
                : paymentRepository.findByBookingIds(bookings.getContent().stream().map(Booking::getId).toList()).stream()
                .collect(Collectors.toMap(payment -> payment.getBooking().getId(), Function.identity(), (older, newer) -> newer));

        return PageResponse.from(bookings, bookings.getContent().stream()
                .map(booking -> toItem(booking, latestPayment.get(booking.getId())))
                .toList());
    }

    @Transactional(readOnly = true)
    public OwnerBookingDetailResponse get(UUID ownerId, UUID bookingId) {
        Booking booking = bookingRepository.findById(bookingId)
                .filter(item -> item.getVenue().getOwner().getId().equals(ownerId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay don dat san."));
        Payment payment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(bookingId).orElse(null);

        return new OwnerBookingDetailResponse(
                booking.getId(), booking.getBookingCode(), booking.getStatus().getValue(),
                booking.getVenue().getId(), booking.getVenue().getName(),
                booking.getUser().getFullName(), booking.getUser().getPhone(), slotsOf(booking),
                booking.getTotalAmount(), booking.getNote(), booking.getRejectionReason(), booking.getCancellationReason(),
                payment == null ? null : new OwnerBookingDetailResponse.Payment(
                        payment.getId(), payment.getStatus().getValue(), payment.getMethod(), payment.getAmount(),
                        payment.getPayerReportedAt(), payment.getConfirmedAt(), payment.getRejectionReason()),
                feeOf(booking),
                booking.getStatusHistory().stream()
                        .sorted(Comparator.comparing(BookingStatusHistory::getCreatedAt))
                        .map(OwnerBookingHistoryService::toStep)
                        .toList(),
                booking.getCreatedAt());
    }

    /** Da ghi nhan thi hien snapshot; booking da xac nhan nhung chua choi xong thi hien uoc tinh. */
    private OwnerBookingDetailResponse.Fee feeOf(Booking booking) {
        return commissionRepository.findByBookingId(booking.getId())
                .map(commission -> new OwnerBookingDetailResponse.Fee(false,
                        commission.getFeeConfig() == null ? null : commission.getFeeConfig().getName(),
                        commission.getFeeType().getValue(), commission.getFeeValue(), commission.getGrossAmount(),
                        commission.getCommissionAmount(), commission.getStatus()))
                .orElseGet(() -> booking.getStatus() != BookingStatus.CONFIRMED ? null
                        : feeConfigRepository.findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus.ACTIVE)
                        .map(config -> new OwnerBookingDetailResponse.Fee(true, config.getName(),
                                config.getFeeType().getValue(), config.getFeeValue(), booking.getTotalAmount(),
                                CommissionService.commissionFor(config, booking.getTotalAmount()), null))
                        .orElse(null));
    }

    private static OwnerBookingHistoryItem toItem(Booking booking, Payment payment) {
        return new OwnerBookingHistoryItem(
                booking.getId(), booking.getBookingCode(), booking.getStatus().getValue(),
                booking.getVenue().getId(), booking.getVenue().getName(),
                booking.getUser().getFullName(), booking.getUser().getPhone(), slotsOf(booking),
                booking.getTotalAmount(), payment == null ? null : payment.getStatus().getValue(), booking.getCreatedAt());
    }

    private static List<BookingRequestResponse.CourtSlot> slotsOf(Booking booking) {
        return booking.getItems().stream()
                .sorted(Comparator.comparing(BookingItem::getStartTime))
                .map(item -> new BookingRequestResponse.CourtSlot(item.getCourt().getName(), item.getStartTime(), item.getEndTime()))
                .toList();
    }

    private static OwnerBookingDetailResponse.Step toStep(BookingStatusHistory history) {
        return new OwnerBookingDetailResponse.Step(
                history.getOldStatus() == null ? null : history.getOldStatus().getValue(),
                history.getNewStatus().getValue(), history.getReason(),
                history.getChangedBy() == null ? null : history.getChangedBy().getFullName(),
                history.getActorRole(), history.getChangedBy() == null, history.getCreatedAt());
    }
}
