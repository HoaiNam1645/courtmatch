package com.courtly.service.owner;

import com.courtly.api.owner.dto.CommissionListResponse;
import com.courtly.api.owner.dto.SettlementResponses;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.CommissionSettlement;
import com.courtly.domain.finance.CommissionSettlementRepository;
import java.math.BigDecimal;
import java.time.Instant;
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

/** 2.3.23 - chu san xem cac ky doi soat hoa hong. */
@Service
@RequiredArgsConstructor
public class OwnerSettlementService {

    static final List<String> ALL_STATUSES = List.of("open", "pending_review", "partial", "paid", "overdue", "disputed");

    private final CommissionSettlementRepository settlementRepository;
    private final BookingCommissionRepository commissionRepository;

    /** @param status all hoac mot trang thai ky doi soat */
    @Transactional(readOnly = true)
    public SettlementResponses.ListResponse list(UUID ownerId, String status, int page, int size) {
        List<String> statuses = parseStatuses(status);
        Page<CommissionSettlement> settlements = settlementRepository.findForOwner(ownerId, statuses, PageRequest.of(page, size));

        Map<UUID, Long> counts = settlements.isEmpty() ? Map.of()
                : commissionRepository.countBySettlementIds(settlements.getContent().stream().map(CommissionSettlement::getId).toList()).stream()
                .collect(Collectors.toMap(BookingCommissionRepository.SettlementBookingCount::getSettlementId,
                        BookingCommissionRepository.SettlementBookingCount::getTotal));
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        ALL_STATUSES.forEach(value -> statusCounts.put(value, 0L));
        settlementRepository.countByStatus(ownerId).forEach(row -> statusCounts.put(row.getStatus(), row.getTotal()));

        return new SettlementResponses.ListResponse(
                settlements.getContent().stream().map(item -> toSummary(item, counts.getOrDefault(item.getId(), 0L))).toList(),
                settlements.getNumber(), settlements.getSize(), settlements.getTotalElements(), settlements.getTotalPages(),
                statusCounts);
    }

    @Transactional(readOnly = true)
    public SettlementResponses.Detail get(UUID ownerId, UUID settlementId, int page, int size) {
        CommissionSettlement settlement = settlementRepository.findByIdAndOwnerId(settlementId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay ky doi soat."));
        Page<BookingCommission> bookings = commissionRepository.findBySettlement(settlementId, PageRequest.of(page, size));
        Instant now = Instant.now();
        return new SettlementResponses.Detail(
                toSummary(settlement, bookings.getTotalElements()),
                bookings.getContent().stream().map(commission -> OwnerCommissionService.toItem(commission, now)).toList(),
                bookings.getNumber(), bookings.getSize(), bookings.getTotalElements(), bookings.getTotalPages());
    }

    private static List<String> parseStatuses(String status) {
        if (status == null || status.isBlank() || "all".equals(status)) {
            return ALL_STATUSES;
        }
        if (!ALL_STATUSES.contains(status)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai ky doi soat khong hop le.",
                    Map.of("status", "Chi nhan all hoac " + String.join(", ", ALL_STATUSES)));
        }
        return List.of(status);
    }

    static SettlementResponses.Summary toSummary(CommissionSettlement settlement, long bookingCount) {
        BigDecimal remaining = settlement.getAmountDue().subtract(settlement.getAmountPaid()).max(BigDecimal.ZERO);
        return new SettlementResponses.Summary(
                settlement.getId(), settlement.getPeriodFrom(), settlement.getPeriodTo(),
                settlement.getOpeningOutstanding(), settlement.getCommissionTotal(), settlement.getAdjustmentTotal(),
                settlement.getAmountDue(), settlement.getAmountPaid(), remaining, settlement.getStatus(), bookingCount,
                settlement.getOwnerSubmittedAt(), settlement.getReviewedAt(), settlement.getPaidAt(),
                settlement.getNote(), settlement.getCreatedAt());
    }
}
