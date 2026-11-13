package com.courtly.service.owner;

import com.courtly.api.owner.dto.SlotConfigResponse;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueOperatingHoursRepository;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.service.audit.AuditService;
import com.courtly.service.booking.AvailabilityService;
import com.courtly.service.booking.PricingCalculator;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.9 - chu san chon buoc sinh khung gio dat san.
 *
 * <p>Khung gio khong luu thanh tung ban ghi: chung duoc sinh tu gio mo cua (2.3.7) va bang gia
 * (2.3.5) theo buoc nay, nen khong the sinh ra khung ngoai gio mo cua hay khung chua co gia ma
 * van dat duoc. Booking da tao khong bi anh huong khi doi buoc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerSlotConfigService {

    public static final List<Integer> ALLOWED_STEPS = List.of(30, 60);

    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueRepository venueRepository;
    private final CourtRepository courtRepository;
    private final VenueOperatingHoursRepository hoursRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public SlotConfigResponse get(UUID ownerId, UUID venueId) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        return preview(venue, venue.getSlotStepMinutes());
    }

    /** Dat buoc moi va tra ve ban xem truoc khung gio theo buoc do. */
    @Transactional
    public SlotConfigResponse update(UUID ownerId, UUID venueId, int stepMinutes) {
        Venue venue = ownedVenueLookup.requireVenue(ownerId, venueId);
        if (!ALLOWED_STEPS.contains(stepMinutes)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Buoc khung gio khong hop le.",
                    Map.of("stepMinutes", "Chi nhan 30 hoac 60 phut"));
        }
        int before = venue.getSlotStepMinutes();
        if (before != stepMinutes) {
            venue.setSlotStepMinutes((short) stepMinutes);
            venueRepository.saveAndFlush(venue);
            auditService.record(venue.getOwner(), "slot_step.update", "venues", venueId,
                    Map.of("stepMinutes", before), Map.of("stepMinutes", stepMinutes));
            log.info("Chu san {} doi buoc khung gio san {}: {} -> {}", ownerId, venueId, before, stepMinutes);
        }
        return preview(venue, stepMinutes);
    }

    private SlotConfigResponse preview(Venue venue, int step) {
        List<Court> courts = courtRepository.findAllWithPriceRules(venue.getId()).stream()
                .filter(court -> court.getStatus() == CourtStatus.ACTIVE)
                .toList();
        Map<Short, VenueOperatingHours> hoursByDay = hoursRepository.findAllByVenueIdOrderByDayOfWeekAsc(venue.getId()).stream()
                .collect(Collectors.toMap(VenueOperatingHours::getDayOfWeek, Function.identity()));

        List<SlotConfigResponse.DayPreview> days = new ArrayList<>();
        for (int day = 1; day <= 7; day++) {
            VenueOperatingHours hours = hoursByDay.get((short) day);
            boolean closed = hours == null || hours.isClosed() || hours.getOpenTime() == null || hours.getCloseTime() == null;
            if (closed) {
                days.add(new SlotConfigResponse.DayPreview(day, true, null, null, 0, Map.of()));
                continue;
            }
            // Gia phu thuoc ngay hieu luc: lay ngay gan nhat sap toi roi vao dung thu nay.
            LocalDate date = nextOccurrence(day);
            Map<String, Integer> unpriced = new LinkedHashMap<>();
            int slotCount = 0;
            for (Court court : courts) {
                slotCount = 0;
                int missing = 0;
                for (LocalTime start = hours.getOpenTime(); start.isBefore(hours.getCloseTime()); ) {
                    LocalTime next = start.plusMinutes(step);
                    LocalTime end = next.isBefore(start) || next.isAfter(hours.getCloseTime()) ? hours.getCloseTime() : next;
                    slotCount++;
                    if (PricingCalculator.calculate(court.getPriceRules(), date, start, end).isEmpty()) {
                        missing++;
                    }
                    if (!end.isAfter(start) || end.equals(hours.getCloseTime())) {
                        break;
                    }
                    start = end;
                }
                if (missing > 0) {
                    unpriced.put(court.getCourtCode(), missing);
                }
            }
            if (courts.isEmpty()) {
                slotCount = countSlots(hours, step);
            }
            days.add(new SlotConfigResponse.DayPreview(day, false, hours.getOpenTime(), hours.getCloseTime(), slotCount, unpriced));
        }
        return new SlotConfigResponse(step, ALLOWED_STEPS, days);
    }

    private static int countSlots(VenueOperatingHours hours, int step) {
        long minutes = java.time.Duration.between(hours.getOpenTime(), hours.getCloseTime()).toMinutes();
        return (int) Math.ceil(minutes / (double) step);
    }

    private static LocalDate nextOccurrence(int dayOfWeek) {
        LocalDate date = LocalDate.now(AvailabilityService.ZONE);
        while (date.getDayOfWeek().getValue() != dayOfWeek) {
            date = date.plusDays(1);
        }
        return date;
    }
}
