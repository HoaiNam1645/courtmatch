package com.courtly.service.owner;

import com.courtly.api.owner.dto.CommissionListResponse;
import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.PlatformFeeConfig;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
import com.courtly.domain.venue.VenueRepository;
import com.courtly.service.booking.AvailabilityService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.18 - chu san xem phi nen tang (hoa hong) cua tung booking. */
@Service
@RequiredArgsConstructor
public class OwnerCommissionService {

    /** Hoa hong chua doi soat va con phai tra. */
    static final Set<String> OUTSTANDING = Set.of("pending", "due", "adjusted");

    static final List<String> ALL_STATUSES = List.of("pending", "due", "suspended", "adjusted", "waived", "settled");

    private static final Instant FAR_PAST = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    private final OwnedVenueLookup ownedVenueLookup;
    private final VenueRepository venueRepository;
    private final BookingCommissionRepository commissionRepository;
    private final PlatformFeeConfigRepository feeConfigRepository;

    /**
     * @param status all hoac mot trang thai hoa hong
     * @param from   loc theo ngay ghi nhan (gio Viet Nam), tinh ca ngay nay
     */
    @Transactional(readOnly = true)
    public CommissionListResponse list(UUID ownerId, UUID venueId, String status, LocalDate from, LocalDate to,
                                       int page, int size) {
        List<String> statuses = parseStatuses(status);
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
        CommissionListResponse.Policy policy = activePolicy();
        if (venueIds.isEmpty()) {
            return new CommissionListResponse(List.of(), page, size, 0, 0, BigDecimal.ZERO, policy);
        }

        Instant start = from == null ? FAR_PAST : from.atStartOfDay(AvailabilityService.ZONE).toInstant();
        Instant end = to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay(AvailabilityService.ZONE).toInstant();
        Page<BookingCommission> commissions = commissionRepository.findForVenues(venueIds, statuses, start, end, PageRequest.of(page, size));
        Instant now = Instant.now();
        return new CommissionListResponse(
                commissions.getContent().stream().map(commission -> toItem(commission, now)).toList(),
                commissions.getNumber(), commissions.getSize(), commissions.getTotalElements(), commissions.getTotalPages(),
                commissionRepository.sumForVenues(venueIds, statuses, start, end), policy);
    }

    /** Chinh sach dang hieu luc, dung de hien muc phi se ap dung cho booking tiep theo. */
    public CommissionListResponse.Policy activePolicy() {
        return feeConfigRepository.findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus.ACTIVE)
                .map(OwnerCommissionService::toPolicy)
                .orElse(null);
    }

    private static List<String> parseStatuses(String status) {
        if (status == null || status.isBlank() || "all".equals(status)) {
            return ALL_STATUSES;
        }
        if (!ALL_STATUSES.contains(status)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai hoa hong khong hop le.",
                    Map.of("status", "Chi nhan all hoac " + String.join(", ", ALL_STATUSES)));
        }
        return List.of(status);
    }

    static CommissionListResponse.Policy toPolicy(PlatformFeeConfig config) {
        return new CommissionListResponse.Policy(config.getName(), config.getFeeType().getValue(), config.getFeeValue());
    }

    private static CommissionListResponse.Item toItem(BookingCommission commission, Instant now) {
        Booking booking = commission.getBooking();
        boolean overdue = OUTSTANDING.contains(commission.getStatus())
                && commission.getDueAt() != null && commission.getDueAt().isBefore(now);
        return new CommissionListResponse.Item(
                booking.getId(), booking.getBookingCode(), booking.getStatus().getValue(),
                booking.getVenue().getId(), booking.getVenue().getName(), booking.getUser().getFullName(),
                commission.getGrossAmount(), commission.getFeeType().getValue(), commission.getFeeValue(),
                commission.getCommissionAmount(),
                commission.getFeeConfig() == null ? null : commission.getFeeConfig().getName(),
                commission.getStatus(), overdue,
                commission.getCalculatedAt(), commission.getDueAt(), commission.getSettledAt());
    }
}
