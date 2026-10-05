package com.courtly.service.booking;

import com.courtly.api.booking.dto.QuoteRequest;
import com.courtly.api.booking.dto.QuoteResponse;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.BlockStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.BookingItemRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtBlock;
import com.courtly.domain.venue.CourtBlockRepository;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.CourtRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueOperatingHoursRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tam tinh tien cho lua chon dat san (2.1.27).
 *
 * <p>Giao dien hien so tien nay truoc khi nguoi dung sang man hinh thanh toan. Khi tao don
 * that, server van tinh lai - so tien o day chi de hien thi, khong duoc tin nguoc lai.
 */
@Service
@RequiredArgsConstructor
public class QuoteService {

    private static final List<Integer> ALLOWED_DURATIONS = List.of(60, 90, 120);

    private final CourtRepository courtRepository;
    private final CourtBlockRepository courtBlockRepository;
    private final BookingItemRepository bookingItemRepository;
    private final VenueOperatingHoursRepository operatingHoursRepository;

    @Transactional(readOnly = true)
    public QuoteResponse quote(QuoteRequest request) {
        if (!ALLOWED_DURATIONS.contains(request.durationMinutes())) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Thoi luong khong hop le.",
                    Map.of("durationMinutes", "Chi nhan 60, 90 hoac 120 phut"));
        }

        Court court = courtRepository.findById(request.courtId())
                .orElseThrow(() -> ApiException.notFound("Khong tim thay san con nay."));
        Venue venue = court.getVenue();
        if (venue.getStatus() != VenueStatus.ACTIVE || venue.getApprovalStatus() != ApprovalStatus.APPROVED) {
            throw ApiException.notFound("Khong tim thay san nay.");
        }

        Instant startAt = request.startTime();
        Instant endAt = startAt.plusSeconds(request.durationMinutes() * 60L);
        LocalDate date = startAt.atZone(AvailabilityService.ZONE).toLocalDate();
        LocalTime startTime = startAt.atZone(AvailabilityService.ZONE).toLocalTime();
        LocalTime endTime = endAt.atZone(AvailabilityService.ZONE).toLocalTime();

        String unavailableReason = checkAvailability(court, date, startTime, endTime, startAt, endAt);

        List<CourtPriceRule> rules = PricingCalculator.effectiveRules(court.getPriceRules(), date);
        BigDecimal total = PricingCalculator.calculate(court.getPriceRules(), date, startTime, endTime)
                .orElse(null);
        if (total == null && unavailableReason == null) {
            unavailableReason = "Khung gio nay chua co bang gia.";
        }

        return new QuoteResponse(
                venue.getId(), venue.getName(), court.getId(), court.getName(),
                date, startAt, endAt, startTime, endTime, request.durationMinutes(),
                total, unavailableReason == null, unavailableReason,
                buildSegments(rules, startTime, endTime));
    }

    /** @return ly do khong dat duoc, hoac null neu khung gio hop le */
    private String checkAvailability(Court court, LocalDate date, LocalTime startTime, LocalTime endTime,
                                     Instant startAt, Instant endAt) {
        // Khung gio ket thuc sang ngay hom sau thi khong nam tron trong mot ngay mo cua.
        if (!endTime.isAfter(startTime)) {
            return "Khung gio vuot qua nua dem, vui long chon gio khac.";
        }

        short dayOfWeek = (short) date.getDayOfWeek().getValue();
        Optional<VenueOperatingHours> hours = operatingHoursRepository
                .findAllByVenueIdOrderByDayOfWeekAsc(court.getVenue().getId()).stream()
                .filter(item -> item.getDayOfWeek() == dayOfWeek)
                .findFirst();

        if (hours.isEmpty() || hours.get().isClosed()) {
            return "San nghi vao ngay nay.";
        }
        if (startTime.isBefore(hours.get().getOpenTime()) || endTime.isAfter(hours.get().getCloseTime())) {
            return "Khung gio nam ngoai gio mo cua.";
        }

        List<CourtBlock> blocks = courtBlockRepository
                .findActiveOverlapping(List.of(court.getId()), startAt, endAt);
        if (blocks.stream().anyMatch(block -> block.getStatus() == BlockStatus.ACTIVE)) {
            return "San dang bi khoa trong khung gio nay.";
        }
        if (bookingItemRepository.existsOverlapping(court.getId(), startAt, endAt)) {
            return "Khung gio nay vua co nguoi dat.";
        }
        return null;
    }

    /** Tach thanh tung doan gia de giao dien giai thich duoc cach ra so tien. */
    private List<QuoteResponse.PriceSegment> buildSegments(List<CourtPriceRule> rules,
                                                           LocalTime startTime, LocalTime endTime) {
        List<QuoteResponse.PriceSegment> segments = new ArrayList<>();
        LocalTime cursor = startTime;

        while (cursor.isBefore(endTime)) {
            LocalTime segmentStart = cursor;
            Optional<CourtPriceRule> rule = rules.stream()
                    .filter(item -> !segmentStart.isBefore(item.getStartTime())
                            && segmentStart.isBefore(item.getEndTime()))
                    .findFirst();
            if (rule.isEmpty()) {
                return segments;
            }

            LocalTime segmentEnd = rule.get().getEndTime().isBefore(endTime)
                    ? rule.get().getEndTime() : endTime;
            long minutes = java.time.Duration.between(segmentStart, segmentEnd).toMinutes();
            BigDecimal amount = rule.get().getPricePerHour()
                    .multiply(BigDecimal.valueOf(minutes))
                    .divide(BigDecimal.valueOf(60), 0, java.math.RoundingMode.HALF_UP);

            segments.add(new QuoteResponse.PriceSegment(
                    segmentStart, segmentEnd, rule.get().getPricePerHour(), amount));
            cursor = segmentEnd;
        }
        return segments;
    }
}
