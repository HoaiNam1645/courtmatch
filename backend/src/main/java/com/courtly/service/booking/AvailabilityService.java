package com.courtly.service.booking;

import com.courtly.api.booking.dto.AvailabilityResponse;
import com.courtly.api.booking.dto.SlotStatus;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingItemRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtBlock;
import com.courtly.domain.venue.CourtBlockRepository;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueOperatingHoursRepository;
import com.courtly.domain.venue.VenueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dung lich trong cua san (2.1.26).
 *
 * <p>Khung gio trong = gio mo cua cua venue, tru cac khoang bi khoa trong court_blocks,
 * tru cac booking_items dang pending/confirmed. Dung dung tap trang thai ma exclusion
 * constraint {@code booking_items_no_overlap} bao ve, nen ket qua khop voi cai database
 * thuc su cho phep.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    public static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Giao dien hien khung gio theo gio tron. */
    private static final int STEP_MINUTES = 60;

    /** Chi cho xem lich trong pham vi nay de tranh truy van qua xa. */
    private static final int MAX_DAYS_AHEAD = 60;

    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final CourtBlockRepository courtBlockRepository;
    private final BookingItemRepository bookingItemRepository;
    private final VenueOperatingHoursRepository operatingHoursRepository;

    @Transactional(readOnly = true)
    public AvailabilityResponse getAvailability(UUID venueId, LocalDate date, int durationMinutes) {
        LocalDate today = LocalDate.now(ZONE);
        if (date.isBefore(today)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khong xem duoc lich cua ngay da qua.",
                    Map.of("date", "Ngay phai tu hom nay tro di"));
        }
        if (date.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Chi xem duoc lich trong vong " + MAX_DAYS_AHEAD + " ngay toi.",
                    Map.of("date", "Ngay qua xa"));
        }

        Venue venue = venueRepository.findById(venueId)
                .filter(item -> item.getStatus() == VenueStatus.ACTIVE
                        && item.getApprovalStatus() == ApprovalStatus.APPROVED)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay san nay."));

        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        Optional<VenueOperatingHours> hours = operatingHoursRepository
                .findAllByVenueIdOrderByDayOfWeekAsc(venueId).stream()
                .filter(item -> item.getDayOfWeek() == dayOfWeek)
                .findFirst();

        boolean closed = hours.isEmpty() || hours.get().isClosed()
                || hours.get().getOpenTime() == null || hours.get().getCloseTime() == null;
        if (closed) {
            return new AvailabilityResponse(venueId, date, true, null, null, durationMinutes, List.of());
        }

        LocalTime openTime = hours.get().getOpenTime();
        LocalTime closeTime = hours.get().getCloseTime();
        Instant windowStart = date.atTime(openTime).atZone(ZONE).toInstant();
        Instant windowEnd = date.atTime(closeTime).atZone(ZONE).toInstant();

        List<Court> courts = courtRepository.findActiveWithPriceRules(venueId);
        if (courts.isEmpty()) {
            return new AvailabilityResponse(venueId, date, false, openTime, closeTime, durationMinutes, List.of());
        }

        // Hai truy van cho ca venue, khong phai moi san con mot truy van.
        List<UUID> courtIds = courts.stream().map(Court::getId).toList();
        Map<UUID, List<CourtBlock>> blocksByCourt = courtBlockRepository
                .findActiveOverlapping(courtIds, windowStart, windowEnd).stream()
                .collect(Collectors.groupingBy(block -> block.getCourt().getId()));
        Map<UUID, List<BookingItem>> itemsByCourt = bookingItemRepository
                .findOccupiedForCourts(courtIds, windowStart, windowEnd).stream()
                .collect(Collectors.groupingBy(item -> item.getCourt().getId()));

        Instant now = Instant.now();
        List<AvailabilityResponse.CourtAvailability> result = courts.stream()
                .map(court -> buildCourtAvailability(
                        court, date, openTime, closeTime, durationMinutes, now,
                        blocksByCourt.getOrDefault(court.getId(), List.of()),
                        itemsByCourt.getOrDefault(court.getId(), List.of())))
                .toList();

        return new AvailabilityResponse(venueId, date, false, openTime, closeTime, durationMinutes, result);
    }

    private AvailabilityResponse.CourtAvailability buildCourtAvailability(
            Court court, LocalDate date, LocalTime openTime, LocalTime closeTime,
            int durationMinutes, Instant now, List<CourtBlock> blocks, List<BookingItem> items) {

        List<AvailabilityResponse.Slot> slots = new ArrayList<>();

        for (LocalTime start = openTime;
             !start.plusMinutes(durationMinutes).isAfter(closeTime);
             start = start.plusMinutes(STEP_MINUTES)) {

            LocalTime end = start.plusMinutes(durationMinutes);
            Instant startAt = LocalDateTime.of(date, start).atZone(ZONE).toInstant();
            Instant endAt = LocalDateTime.of(date, end).atZone(ZONE).toInstant();

            SlotStatus status = resolveStatus(startAt, endAt, now, blocks, items);
            BigDecimal price = status == SlotStatus.AVAILABLE
                    ? PricingCalculator.calculate(court.getPriceRules(), date, start, end).orElse(null)
                    : null;

            slots.add(new AvailabilityResponse.Slot(start, end, startAt, endAt, status, price));

            // closeTime co the la 23:59, cong them buoc nua se tran sang hom sau.
            if (start.plusMinutes(STEP_MINUTES).isBefore(start)) {
                break;
            }
        }

        long availableCount = slots.stream()
                .filter(slot -> slot.status() == SlotStatus.AVAILABLE)
                .count();

        return new AvailabilityResponse.CourtAvailability(
                court.getId(), court.getName(), court.getCourtCode(), slots, (int) availableCount);
    }

    /** Thu tu uu tien: da qua gio -> bi khoa -> da co nguoi dat -> con trong. */
    private SlotStatus resolveStatus(Instant startAt, Instant endAt, Instant now,
                                     List<CourtBlock> blocks, List<BookingItem> items) {
        if (!startAt.isAfter(now)) {
            return SlotStatus.PAST;
        }
        if (blocks.stream().anyMatch(block -> overlaps(startAt, endAt, block.getStartTime(), block.getEndTime()))) {
            return SlotStatus.BLOCKED;
        }
        if (items.stream().anyMatch(item -> overlaps(startAt, endAt, item.getStartTime(), item.getEndTime()))) {
            return SlotStatus.BOOKED;
        }
        return SlotStatus.AVAILABLE;
    }

    /** Hai khoang nua mo [start, end) giao nhau. */
    private static boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }
}
