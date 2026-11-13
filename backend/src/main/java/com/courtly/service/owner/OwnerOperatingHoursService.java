package com.courtly.service.owner;

import com.courtly.api.owner.dto.OperatingHoursInput;
import com.courtly.api.owner.dto.OperatingHoursResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.booking.BookingItemRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueOperatingHoursRepository;
import com.courtly.service.audit.AuditService;
import com.courtly.service.booking.AvailabilityService;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.7 - chu san quan ly gio mo cua theo tung ngay trong tuan.
 *
 * <p>Lich trong (2.1.26) va bao gia (2.1.27) doc truc tiep bang venue_operating_hours, nen
 * gio moi co hieu luc ngay voi cac lan dat tiep theo. Booking da tao khong bi huy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerOperatingHoursService {

    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueOperatingHoursRepository hoursRepository;
    private final BookingItemRepository bookingItemRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public OperatingHoursResponse get(UUID ownerId, UUID venueId) {
        ownedVenueLookup.requireVenue(ownerId, venueId);
        List<VenueOperatingHours> hours = hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venueId);
        return new OperatingHoursResponse(toDays(hours), conflicts(venueId, hours));
    }

    @Transactional
    public OperatingHoursResponse update(UUID ownerId, UUID venueId, List<OperatingHoursInput> days) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        validate(days);

        List<VenueOperatingHours> existing = hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venueId);
        Map<String, Object> before = Map.of("days", snapshot(existing));
        Map<Short, VenueOperatingHours> byDay = existing.stream()
                .collect(Collectors.toMap(VenueOperatingHours::getDayOfWeek, Function.identity()));

        List<VenueOperatingHours> saved = new ArrayList<>();
        for (OperatingHoursInput input : days) {
            short day = input.dayOfWeek().shortValue();
            VenueOperatingHours hours = byDay.get(day);
            if (hours == null) {
                hours = new VenueOperatingHours();
                hours.setId(UUID.randomUUID());
                hours.setVenue(venue);
                hours.setDayOfWeek(day);
                venue.getOperatingHours().add(hours);
            }
            hours.setClosed(input.closed());
            hours.setOpenTime(input.closed() ? null : input.openTime());
            hours.setCloseTime(input.closed() ? null : input.closeTime());
            saved.add(hours);
        }
        hoursRepository.saveAllAndFlush(saved);
        saved.sort((left, right) -> Short.compare(left.getDayOfWeek(), right.getDayOfWeek()));

        auditService.record(venue.getOwner(), "operating_hours.update", "venues", venueId,
                before, Map.of("days", snapshot(saved)));
        log.info("Chu san {} cap nhat gio hoat dong san {}", ownerId, venueId);
        return new OperatingHoursResponse(toDays(saved), conflicts(venueId, saved));
    }

    private static void validate(List<OperatingHoursInput> days) {
        Map<String, String> errors = new LinkedHashMap<>();
        Set<Integer> seen = new HashSet<>();
        for (int index = 0; index < days.size(); index++) {
            OperatingHoursInput day = days.get(index);
            String prefix = "days[" + index + "].";
            if (!seen.add(day.dayOfWeek())) {
                errors.put(prefix + "dayOfWeek", "Ngay nay bi khai bao hai lan");
            }
            if (day.closed()) {
                continue;
            }
            if (day.openTime() == null) {
                errors.put(prefix + "openTime", "Vui long nhap gio mo cua");
            }
            if (day.closeTime() == null) {
                errors.put(prefix + "closeTime", "Vui long nhap gio dong cua");
            } else if (day.openTime() != null && !day.closeTime().isAfter(day.openTime())) {
                errors.put(prefix + "closeTime", "Gio dong cua phai sau gio mo cua");
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Gio hoat dong chua hop le, vui long kiem tra lai.", errors);
        }
    }

    /** Booking chua choi khong con nam tron trong gio mo cua cua ngay do. */
    private List<OperatingHoursResponse.Conflict> conflicts(UUID venueId, List<VenueOperatingHours> hours) {
        Map<Short, VenueOperatingHours> byDay = hours.stream()
                .collect(Collectors.toMap(VenueOperatingHours::getDayOfWeek, Function.identity()));
        return bookingItemRepository.findUpcomingForVenue(venueId, Instant.now()).stream()
                .filter(item -> !fitsOpeningHours(item, byDay))
                .map(item -> new OperatingHoursResponse.Conflict(
                        item.getBooking().getId(), item.getBooking().getBookingCode(), item.getCourt().getName(),
                        item.getStartTime(), item.getEndTime(), item.getBooking().getStatus().getValue()))
                .toList();
    }

    private static boolean fitsOpeningHours(BookingItem item, Map<Short, VenueOperatingHours> byDay) {
        ZonedDateTime start = item.getStartTime().atZone(AvailabilityService.ZONE);
        ZonedDateTime end = item.getEndTime().atZone(AvailabilityService.ZONE);
        VenueOperatingHours day = byDay.get((short) start.getDayOfWeek().getValue());
        if (day == null || day.isClosed() || !start.toLocalDate().equals(end.toLocalDate())) {
            return false;
        }
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();
        return !startTime.isBefore(day.getOpenTime()) && !endTime.isAfter(day.getCloseTime());
    }

    /** Luon tra du 7 ngay; ngay chua khai bao hien la nghi de chu san bo sung. */
    private static List<OperatingHoursResponse.DayHours> toDays(List<VenueOperatingHours> hours) {
        Map<Short, VenueOperatingHours> byDay = hours.stream()
                .collect(Collectors.toMap(VenueOperatingHours::getDayOfWeek, Function.identity()));
        List<OperatingHoursResponse.DayHours> days = new ArrayList<>();
        for (short day = 1; day <= 7; day++) {
            VenueOperatingHours item = byDay.get(day);
            days.add(item == null
                    ? new OperatingHoursResponse.DayHours(day, true, null, null, false)
                    : new OperatingHoursResponse.DayHours(day, item.isClosed(), item.getOpenTime(), item.getCloseTime(), true));
        }
        return days;
    }

    private static List<String> snapshot(List<VenueOperatingHours> hours) {
        return hours.stream()
                .map(item -> item.getDayOfWeek() + ":" + (item.isClosed() ? "closed" : item.getOpenTime() + "-" + item.getCloseTime()))
                .toList();
    }
}
