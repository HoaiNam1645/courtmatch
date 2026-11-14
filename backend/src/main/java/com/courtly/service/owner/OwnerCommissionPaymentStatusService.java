package com.courtly.service.owner;

import com.courtly.api.owner.dto.CommissionPaymentResponse;
import com.courtly.api.owner.dto.CommissionPaymentStatusResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.CommissionPaymentReport;
import com.courtly.domain.finance.CommissionPaymentReportRepository;
import com.courtly.domain.finance.CommissionSettlement;
import com.courtly.domain.finance.CommissionSettlementRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.25 - chu san xem trang thai thanh toan cong no hoa hong. */
@Service
@RequiredArgsConstructor
public class OwnerCommissionPaymentStatusService {

    private static final int MAX_SETTLEMENTS = 20;

    private final BookingCommissionRepository commissionRepository;
    private final CommissionPaymentReportRepository reportRepository;
    private final CommissionSettlementRepository settlementRepository;

    @Transactional(readOnly = true)
    public CommissionPaymentStatusResponse get(UUID ownerId) {
        BigDecimal pendingAmount = BigDecimal.ZERO;
        BigDecimal confirmedAmount = BigDecimal.ZERO;
        long pendingCount = 0;
        long confirmedCount = 0;
        long rejectedCount = 0;
        for (CommissionPaymentReportRepository.StatusTotal row : reportRepository.totalsByStatus(ownerId)) {
            switch (row.getStatus()) {
                case "pending_review" -> {
                    pendingAmount = row.getAmount();
                    pendingCount = row.getTotal();
                }
                case "confirmed" -> {
                    confirmedAmount = row.getAmount();
                    confirmedCount = row.getTotal();
                }
                case "rejected" -> rejectedCount = row.getTotal();
                default -> { }
            }
        }

        Map<UUID, BigDecimal> pendingBySettlement = new HashMap<>();
        reportRepository.pendingBySettlement(ownerId).forEach(row -> pendingBySettlement.put(row.getSettlementId(), row.getAmount()));
        List<CommissionPaymentStatusResponse.SettlementStatus> unpaid = settlementRepository
                .findUnpaidForOwner(ownerId, PageRequest.of(0, MAX_SETTLEMENTS)).stream()
                .map(settlement -> toSettlementStatus(settlement, pendingBySettlement.getOrDefault(settlement.getId(), BigDecimal.ZERO)))
                .toList();

        return new CommissionPaymentStatusResponse(
                commissionRepository.sumOutstandingByOwnerId(ownerId), pendingAmount, pendingCount,
                confirmedAmount, confirmedCount, rejectedCount,
                reportRepository.findFirstByOwnerIdOrderByCreatedAtDesc(ownerId).map(OwnerCommissionPaymentService::toResponse).orElse(null),
                unpaid);
    }

    /** Trang thai cua mot lan gui thanh toan. */
    @Transactional(readOnly = true)
    public CommissionPaymentResponse getPayment(UUID ownerId, UUID paymentId) {
        CommissionPaymentReport report = reportRepository.findByIdAndOwnerId(paymentId, ownerId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay lan thanh toan."));
        return OwnerCommissionPaymentService.toResponse(report);
    }

    private static CommissionPaymentStatusResponse.SettlementStatus toSettlementStatus(CommissionSettlement settlement, BigDecimal pending) {
        BigDecimal remaining = settlement.getAmountDue().subtract(settlement.getAmountPaid()).max(BigDecimal.ZERO);
        return new CommissionPaymentStatusResponse.SettlementStatus(
                settlement.getId(), settlement.getPeriodFrom(), settlement.getPeriodTo(), settlement.getStatus(),
                settlement.getAmountDue(), settlement.getAmountPaid(), remaining, pending,
                remaining.subtract(pending).max(BigDecimal.ZERO), settlement.getOwnerSubmittedAt());
    }
}
