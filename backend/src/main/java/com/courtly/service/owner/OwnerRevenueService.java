package com.courtly.service.owner;

import com.courtly.api.owner.dto.RevenueStatsResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.service.booking.AvailabilityService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.17 - chu san xem thong ke doanh thu gop cua cac san minh. */
@Service
@RequiredArgsConstructor
public class OwnerRevenueService {

    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueRepository venueRepository;
    private final PaymentRepository paymentRepository;

    /**
     * @param from    ngay bat dau (gio Viet Nam, tinh ca ngay nay); null = 29 ngay truoc ngay ket thuc
     * @param to      ngay ket thuc (tinh ca ngay nay); null = hom nay
     * @param venueId null = moi san cua chu san
     */
    @Transactional(readOnly = true)
    public RevenueStatsResponse stats(UUID ownerId, UUID venueId, LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(AvailabilityService.ZONE) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        validate(start, end);

        List<Venue> venues;
        if (venueId == null) {
            venues = venueRepository.findAllById(venueRepository.findIdsByOwnerId(ownerId));
        } else {
            venues = List.of(ownedVenueLookup.requireVenue(ownerId, venueId));
        }
        List<UUID> venueIds = venues.stream().map(Venue::getId).toList();
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        LocalDate previousEnd = start.minusDays(1);
        LocalDate previousStart = previousEnd.minusDays(days - 1);

        if (venueIds.isEmpty()) {
            return empty(start, end, previousStart, previousEnd);
        }

        List<PaymentRepository.RevenueRow> rows = paymentRepository.revenueByDayAndVenue(venueIds, startOf(start), startOf(end.plusDays(1)));
        List<PaymentRepository.RevenueRow> previousRows =
                paymentRepository.revenueByDayAndVenue(venueIds, startOf(previousStart), startOf(previousEnd.plusDays(1)));

        BigDecimal total = sum(rows);
        long bookings = rows.stream().mapToLong(PaymentRepository.RevenueRow::getBookings).sum();
        BigDecimal previousTotal = sum(previousRows);
        long previousBookings = previousRows.stream().mapToLong(PaymentRepository.RevenueRow::getBookings).sum();

        Map<LocalDate, BigDecimal> revenueByDay = new TreeMap<>();
        Map<LocalDate, Long> bookingsByDay = new TreeMap<>();
        for (PaymentRepository.RevenueRow row : rows) {
            LocalDate day = LocalDate.parse(row.getDay());
            revenueByDay.merge(day, row.getTotal(), BigDecimal::add);
            bookingsByDay.merge(day, row.getBookings(), Long::sum);
        }
        List<RevenueStatsResponse.DayPoint> daily = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            daily.add(new RevenueStatsResponse.DayPoint(day, revenueByDay.getOrDefault(day, BigDecimal.ZERO),
                    bookingsByDay.getOrDefault(day, 0L)));
        }

        Map<UUID, BigDecimal> revenueByVenue = rows.stream().collect(
                Collectors.groupingBy(PaymentRepository.RevenueRow::getVenueId,
                        Collectors.reducing(BigDecimal.ZERO, PaymentRepository.RevenueRow::getTotal, BigDecimal::add)));
        Map<UUID, Long> bookingsByVenue = rows.stream().collect(
                Collectors.groupingBy(PaymentRepository.RevenueRow::getVenueId,
                        Collectors.summingLong(PaymentRepository.RevenueRow::getBookings)));
        List<RevenueStatsResponse.VenueRevenue> byVenue = venues.stream()
                .map(venue -> new RevenueStatsResponse.VenueRevenue(venue.getId(), venue.getName(),
                        revenueByVenue.getOrDefault(venue.getId(), BigDecimal.ZERO),
                        bookingsByVenue.getOrDefault(venue.getId(), 0L)))
                .sorted(Comparator.comparing(RevenueStatsResponse.VenueRevenue::revenue).reversed()
                        .thenComparing(RevenueStatsResponse.VenueRevenue::venueName))
                .toList();

        return new RevenueStatsResponse(start, end, total, bookings, average(total, bookings),
                new RevenueStatsResponse.Previous(previousStart, previousEnd, previousTotal, previousBookings),
                changePercent(total, previousTotal), daily, byVenue);
    }

    private static void validate(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khoang ngay khong hop le.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Chi xem thong ke toi da " + MAX_DAYS + " ngay moi lan.",
                    Map.of("to", "Khoang ngay toi da " + MAX_DAYS + " ngay"));
        }
    }

    private static RevenueStatsResponse empty(LocalDate start, LocalDate end, LocalDate previousStart, LocalDate previousEnd) {
        List<RevenueStatsResponse.DayPoint> daily = new ArrayList<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            daily.add(new RevenueStatsResponse.DayPoint(day, BigDecimal.ZERO, 0));
        }
        return new RevenueStatsResponse(start, end, BigDecimal.ZERO, 0, BigDecimal.ZERO,
                new RevenueStatsResponse.Previous(previousStart, previousEnd, BigDecimal.ZERO, 0),
                null, daily, List.of());
    }

    private static Instant startOf(LocalDate date) {
        return date.atStartOfDay(AvailabilityService.ZONE).toInstant();
    }

    private static BigDecimal sum(List<PaymentRepository.RevenueRow> rows) {
        return rows.stream().map(PaymentRepository.RevenueRow::getTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal average(BigDecimal total, long count) {
        return count == 0 ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(count), 0, RoundingMode.HALF_UP);
    }

    private static BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP);
    }
}
