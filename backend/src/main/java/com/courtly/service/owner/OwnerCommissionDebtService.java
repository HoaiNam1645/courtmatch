package com.courtly.service.owner;

import com.courtly.api.owner.dto.CommissionDebtResponse;
import com.courtly.domain.finance.BookingCommissionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.20 - chu san xem tong cong no hoa hong voi Courtly. */
@Service
@RequiredArgsConstructor
public class OwnerCommissionDebtService {

    private final BookingCommissionRepository commissionRepository;

    @Transactional(readOnly = true)
    public CommissionDebtResponse get(UUID ownerId) {
        Instant now = Instant.now();
        List<BookingCommissionRepository.DebtRow> rows = commissionRepository.debtRows(ownerId, now);

        BigDecimal overdue = BigDecimal.ZERO;
        BigDecimal upcoming = BigDecimal.ZERO;
        BigDecimal suspended = BigDecimal.ZERO;
        BigDecimal settled = BigDecimal.ZERO;
        long overdueCount = 0;
        long upcomingCount = 0;
        long suspendedCount = 0;
        Instant oldestOverdue = null;
        Instant nextDue = null;
        Map<UUID, BigDecimal> outstandingByVenue = new LinkedHashMap<>();
        Map<UUID, BigDecimal> overdueByVenue = new LinkedHashMap<>();
        Map<UUID, Long> countByVenue = new LinkedHashMap<>();

        for (BookingCommissionRepository.DebtRow row : rows) {
            String status = row.getStatus();
            if ("suspended".equals(status)) {
                suspended = suspended.add(row.getTotal());
                suspendedCount += row.getBookings();
            } else if ("settled".equals(status)) {
                settled = settled.add(row.getTotal());
            } else if (OwnerCommissionService.OUTSTANDING.contains(status)) {
                boolean late = Boolean.TRUE.equals(row.getOverdue());
                if (late) {
                    overdue = overdue.add(row.getTotal());
                    overdueCount += row.getBookings();
                    oldestOverdue = earliest(oldestOverdue, row.getEarliestDue());
                    overdueByVenue.merge(row.getVenueId(), row.getTotal(), BigDecimal::add);
                } else {
                    upcoming = upcoming.add(row.getTotal());
                    upcomingCount += row.getBookings();
                    nextDue = earliest(nextDue, row.getEarliestDue());
                }
                outstandingByVenue.merge(row.getVenueId(), row.getTotal(), BigDecimal::add);
                countByVenue.merge(row.getVenueId(), row.getBookings(), Long::sum);
            }
            // waived: duoc mien, khong phai tra.
        }

        Map<UUID, String> names = rows.stream().collect(
                java.util.stream.Collectors.toMap(BookingCommissionRepository.DebtRow::getVenueId,
                        BookingCommissionRepository.DebtRow::getVenueName, (first, second) -> first));
        List<CommissionDebtResponse.VenueDebt> byVenue = outstandingByVenue.entrySet().stream()
                .map(entry -> new CommissionDebtResponse.VenueDebt(entry.getKey(), names.get(entry.getKey()),
                        entry.getValue(), overdueByVenue.getOrDefault(entry.getKey(), BigDecimal.ZERO),
                        countByVenue.getOrDefault(entry.getKey(), 0L)))
                .sorted(Comparator.comparing(CommissionDebtResponse.VenueDebt::outstanding).reversed()
                        .thenComparing(CommissionDebtResponse.VenueDebt::venueName))
                .toList();

        return new CommissionDebtResponse(now, overdue.add(upcoming), overdueCount + upcomingCount,
                overdue, overdueCount, upcoming, upcomingCount, suspended, suspendedCount, settled,
                oldestOverdue, nextDue, byVenue);
    }

    private static Instant earliest(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isBefore(current) ? candidate : current;
    }
}
