package com.courtly.service.owner;

import com.courtly.api.owner.dto.VenueScheduleResponse;
import com.courtly.common.enums.BookingItemStatus;
import com.courtly.common.enums.CourtStatus;
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
import com.courtly.service.booking.AvailabilityService;
import com.courtly.service.booking.PricingCalculator;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.8 - lich san cua chu san.
 *
 * <p>Tinh tu cung nguon voi lich trong cua nguoi choi (2.1.26): gio mo cua, bang gia,
 * court_blocks dang hieu luc va booking_items pending/confirmed. Khac biet: chu san thay
 * moi san con (ke ca bao tri), thay ai dang giu o va ly do khoa.
 */
@Service
@RequiredArgsConstructor
public class OwnerScheduleService {

    private static final int MAX_DAYS_BACK = 30;
    private static final int MAX_DAYS_AHEAD = 60;

    private final OwnedVenueLookup ownedVenueLookup;
    private final CourtRepository courtRepository;
    private final VenueOperatingHoursRepository hoursRepository;
    private final BookingItemRepository bookingItemRepository;
    private final CourtBlockRepository courtBlockRepository;

    @Transactional(readOnly = true)
    public VenueScheduleResponse getSchedule(UUID ownerId, UUID venueId, LocalDate startDate, int days) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        validateRange(startDate, days);

        List<Court> courts = courtRepository.findAllWithPriceRules(venue.getId());
        Map<Short, VenueOperatingHours> hoursByDay = hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venueId).stream()
                .collect(Collectors.toMap(VenueOperatingHours::getDayOfWeek, Function.identity()));

        Instant rangeStart = startDate.atStartOfDay(AvailabilityService.ZONE).toInstant();
        Instant rangeEnd = startDate.plusDays(days).atStartOfDay(AvailabilityService.ZONE).toInstant();
        List<UUID> courtIds = courts.stream().map(Court::getId).toList();
        Map<UUID, List<BookingItem>> itemsByCourt = courtIds.isEmpty() ? Map.of()
                : bookingItemRepository.findOccupiedWithBookingForCourts(courtIds, rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(item -> item.getCourt().getId()));
        Map<UUID, List<CourtBlock>> blocksByCourt = courtIds.isEmpty() ? Map.of()
                : courtBlockRepository.findActiveOverlapping(courtIds, rangeStart, rangeEnd).stream()
                .collect(Collectors.groupingBy(block -> block.getCourt().getId()));

        Instant now = Instant.now();
        int step = venue.getSlotStepMinutes();
        List<VenueScheduleResponse.Day> result = new ArrayList<>();
        for (int offset = 0; offset < days; offset++) {
            LocalDate date = startDate.plusDays(offset);
            VenueOperatingHours hours = hoursByDay.get((short) date.getDayOfWeek().getValue());
            boolean closed = hours == null || hours.isClosed() || hours.getOpenTime() == null || hours.getCloseTime() == null;
            result.add(new VenueScheduleResponse.Day(
                    date, date.getDayOfWeek().getValue(), closed,
                    closed ? null : hours.getOpenTime(), closed ? null : hours.getCloseTime(),
                    courts.stream()
                            .map(court -> new VenueScheduleResponse.CourtSchedule(
                                    court.getId(), court.getName(), court.getCourtCode(), court.getStatus().getValue(),
                                    closed ? List.of() : slotsFor(court, date, hours, step, now,
                                            itemsByCourt.getOrDefault(court.getId(), List.of()),
                                            blocksByCourt.getOrDefault(court.getId(), List.of()))))
                            .toList()));
        }
        return new VenueScheduleResponse(venueId, startDate, step, result);
    }

    private static void validateRange(LocalDate startDate, int days) {
        if (days != 1 && days != 7) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Chi xem lich theo ngay hoac theo tuan.",
                    Map.of("days", "Chi nhan 1 hoac 7"));
        }
        LocalDate today = LocalDate.now(AvailabilityService.ZONE);
        if (startDate.isBefore(today.minusDays(MAX_DAYS_BACK)) || startDate.isAfter(today.plusDays(MAX_DAYS_AHEAD))) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Chi xem duoc lich trong khoang " + MAX_DAYS_BACK + " ngay truoc den " + MAX_DAYS_AHEAD + " ngay toi.",
                    Map.of("date", "Ngay nam ngoai pham vi xem lich"));
        }
    }

    private static List<VenueScheduleResponse.Slot> slotsFor(Court court, LocalDate date, VenueOperatingHours hours,
                                                             int step, Instant now, List<BookingItem> items, List<CourtBlock> blocks) {
        List<VenueScheduleResponse.Slot> slots = new ArrayList<>();
        LocalTime open = hours.getOpenTime();
        LocalTime close = hours.getCloseTime();
        for (LocalTime start = open; start.isBefore(close); ) {
            LocalTime next = start.plusMinutes(step);
            // Gio dong cua le (vd 22:30) hoac 23:59: o cuoi ngan hon, va khong tran sang hom sau.
            LocalTime end = next.isBefore(start) || next.isAfter(close) ? close : next;
            Instant startAt = date.atTime(start).atZone(AvailabilityService.ZONE).toInstant();
            Instant endAt = date.atTime(end).atZone(AvailabilityService.ZONE).toInstant();

            Optional<BookingItem> item = items.stream()
                    .filter(candidate -> overlaps(startAt, endAt, candidate.getStartTime(), candidate.getEndTime()))
                    .findFirst();
            Optional<CourtBlock> block = blocks.stream()
                    .filter(candidate -> overlaps(startAt, endAt, candidate.getStartTime(), candidate.getEndTime()))
                    .findFirst();

            String status;
            if (item.isPresent()) {
                status = item.get().getStatus() == BookingItemStatus.CONFIRMED ? "confirmed" : "pending";
            } else if (block.isPresent()) {
                status = "blocked";
            } else if (!startAt.isAfter(now)) {
                status = "past";
            } else if (court.getStatus() != CourtStatus.ACTIVE) {
                status = "unavailable";
            } else {
                status = "available";
            }

            slots.add(new VenueScheduleResponse.Slot(
                    start, end, startAt, endAt, status,
                    "available".equals(status)
                            ? PricingCalculator.calculate(court.getPriceRules(), date, start, end).orElse(null)
                            : null,
                    item.map(OwnerScheduleService::toBookingRef).orElse(null),
                    block.map(value -> new VenueScheduleResponse.BlockRef(
                            value.getId(), value.getReason(), value.getStartTime(), value.getEndTime())).orElse(null)));

            if (!end.isAfter(start) || end.equals(close)) {
                break;
            }
            start = end;
        }
        return slots;
    }

    private static VenueScheduleResponse.BookingRef toBookingRef(BookingItem item) {
        return new VenueScheduleResponse.BookingRef(
                item.getBooking().getId(), item.getBooking().getBookingCode(), item.getBooking().getStatus().getValue(),
                item.getBooking().getUser().getFullName(), item.getBooking().getUser().getPhone(),
                item.getStartTime(), item.getEndTime());
    }

    /** Hai khoang nua mo [start, end) giao nhau. */
    private static boolean overlaps(Instant startA, Instant endA, Instant startB, Instant endB) {
        return startA.isBefore(endB) && startB.isBefore(endA);
    }
}
