package com.courtly.service.owner;

import com.courtly.api.owner.dto.CommissionDebtResponse;
import com.courtly.api.owner.dto.DebtBookingListResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.venue.VenueRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    private static final Instant FAR_PAST = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueRepository venueRepository;
    private final BookingCommissionRepository commissionRepository;
    private final OwnerRestrictionService restrictionService;
    private final com.courtly.domain.finance.DebtRestrictionLevelRepository restrictionLevelRepository;

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
                oldestOverdue, nextDue, byVenue, restriction(ownerId, now));
    }

    private CommissionDebtResponse.Restriction restriction(UUID ownerId, Instant now) {
        OwnerRestrictionService.Evaluation evaluation = restrictionService.evaluate(ownerId, now);
        int stored = restrictionService.levelOf(ownerId);
        // Muc dang ap dung la muc da luu; ly do (so tien / so ngay) lay tu danh gia hien tai khi muc con khop.
        boolean sameLevel = evaluation.level() == stored;
        return new CommissionDebtResponse.Restriction(stored, sameLevel && evaluation.byAmount(), sameLevel && evaluation.byDays(),
                evaluation.overdueAmount(), evaluation.overdueDays(),
                restrictionLevelRepository.findAllByOrderByLevelAsc().stream()
                        .map(item -> new CommissionDebtResponse.Level(item.getLevel(), item.isEnabled(), item.getMinOverdueAmount(),
                                item.getMinOverdueDays())).toList());
    }

    /**
     * 2.3.21 - cac booking cau thanh cong no.
     *
     * @param bucket all, overdue (da qua han) hoac upcoming (chua den han)
     * @param venueId null = moi san cua chu san
     */
    @Transactional(readOnly = true)
    public DebtBookingListResponse listBookings(UUID ownerId, UUID venueId, String bucket, int page, int size) {
        if (!List.of("all", "overdue", "upcoming").contains(bucket)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Nhom cong no khong hop le.",
                    Map.of("bucket", "Chi nhan all, overdue hoac upcoming"));
        }
        List<UUID> venueIds;
        if (venueId == null) {
            venueIds = venueRepository.findIdsByOwnerId(ownerId);
        } else {
            ownedVenueLookup.requireVenue(ownerId, venueId);
            venueIds = List.of(venueId);
        }
        if (venueIds.isEmpty()) {
            return new DebtBookingListResponse(List.of(), page, size, 0, 0, BigDecimal.ZERO);
        }
        Instant now = Instant.now();
        Instant dueFrom = "upcoming".equals(bucket) ? now : FAR_PAST;
        Instant dueTo = "overdue".equals(bucket) ? now : FAR_FUTURE;
        Page<BookingCommission> commissions = commissionRepository.findDebtForVenues(venueIds, dueFrom, dueTo, PageRequest.of(page, size));
        return new DebtBookingListResponse(
                commissions.getContent().stream().map(commission -> OwnerCommissionService.toItem(commission, now)).toList(),
                commissions.getNumber(), commissions.getSize(), commissions.getTotalElements(), commissions.getTotalPages(),
                commissionRepository.sumDebtForVenues(venueIds, dueFrom, dueTo));
    }

    private static Instant earliest(Instant current, Instant candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.isBefore(current) ? candidate : current;
    }
}
