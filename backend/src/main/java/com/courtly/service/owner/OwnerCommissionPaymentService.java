package com.courtly.service.owner;

import com.courtly.api.owner.dto.CommissionPaymentResponse;
import com.courtly.api.owner.dto.CreateCommissionPaymentRequest;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.CommissionPaymentReport;
import com.courtly.domain.finance.CommissionPaymentReportRepository;
import com.courtly.domain.finance.CommissionSettlement;
import com.courtly.domain.finance.CommissionSettlementRepository;
import com.courtly.service.audit.AuditService;
import com.courtly.service.booking.AvailabilityService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 2.3.24 - chu san gui thong tin thanh toan cong no hoa hong cho Courtly. */
@Service
@RequiredArgsConstructor
public class OwnerCommissionPaymentService {

    private static final int MAX_DAYS_BACK = 365;

    private final UserRepository userRepository;
    private final CommissionSettlementRepository settlementRepository;
    private final CommissionPaymentReportRepository reportRepository;
    private final BookingCommissionRepository commissionRepository;
    private final AuditService auditService;

    /**
     * Ghi nhan bao cao thanh toan o trang thai cho Courtly xac nhan. Cong no chi giam khi Courtly
     * xac nhan, nen gui bao cao khong tu y xoa no.
     */
    @Transactional
    public CommissionPaymentResponse submit(UUID ownerId, CreateCommissionPaymentRequest request) {
        validatePaidOn(request.paidOn());

        CommissionSettlement settlement = null;
        BigDecimal payable;
        if (request.settlementId() != null) {
            settlement = settlementRepository.findByIdAndOwnerId(request.settlementId(), ownerId)
                    .orElseThrow(() -> ApiException.notFound("Khong tim thay ky doi soat."));
            if ("paid".equals(settlement.getStatus()) || "disputed".equals(settlement.getStatus())) {
                throw ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID,
                        "paid".equals(settlement.getStatus())
                                ? "Ky doi soat nay da thanh toan xong." : "Ky doi soat dang tranh chap, chua the thanh toan.",
                        Map.of("settlementId", "Ky doi soat khong o trang thai co the thanh toan"));
            }
            payable = settlement.getAmountDue().subtract(settlement.getAmountPaid())
                    .subtract(reportRepository.sumPendingForSettlement(settlement.getId()));
        } else {
            payable = commissionRepository.sumOutstandingByOwnerId(ownerId)
                    .subtract(reportRepository.sumPendingWithoutSettlement(ownerId));
        }
        if (payable.signum() <= 0) {
            throw ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID,
                    "Khong con khoan nao can thanh toan hoac cac khoan con lai da duoc gui va dang cho xac nhan.",
                    Map.of("amount", "Khong con so tien can thanh toan"));
        }
        if (request.amount().compareTo(payable) > 0) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "So tien vuot qua so con phai thanh toan.",
                    Map.of("amount", "Toi da " + payable.stripTrailingZeros().toPlainString() + " dong"));
        }
        if (reportRepository.referenceInUse(ownerId, request.referenceCode())) {
            throw ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID,
                    "Ma tham chieu nay da duoc dung cho mot lan thanh toan khac.",
                    Map.of("referenceCode", "Ma tham chieu da duoc su dung"));
        }

        User owner = userRepository.findById(ownerId).orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
        Instant now = Instant.now();
        CommissionPaymentReport report = new CommissionPaymentReport();
        report.setId(UUID.randomUUID());
        report.setCreatedAt(now);
        report.setOwner(owner);
        report.setSettlement(settlement);
        report.setAmount(request.amount());
        report.setPaidOn(request.paidOn());
        report.setReferenceCode(request.referenceCode());
        report.setProofUrl(request.proofUrl());
        report.setNote(request.note());
        try {
            reportRepository.saveAndFlush(report);
        } catch (DataIntegrityViolationException exception) {
            // Hai yeu cau cung ma tham chieu den dong thoi: rang buoc duy nhat o DB chan lai.
            throw ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID,
                    "Ma tham chieu nay da duoc dung cho mot lan thanh toan khac.",
                    Map.of("referenceCode", "Ma tham chieu da duoc su dung"));
        }

        if (settlement != null && ("open".equals(settlement.getStatus()) || "overdue".equals(settlement.getStatus())
                || "partial".equals(settlement.getStatus()))) {
            settlement.setStatus("pending_review");
            settlement.setOwnerSubmittedAt(now);
            settlement.setPaymentReference(request.referenceCode());
            settlement.setProofUrl(request.proofUrl());
            settlementRepository.save(settlement);
        }

        Map<String, Object> audit = new LinkedHashMap<>();
        audit.put("amount", request.amount().toPlainString());
        audit.put("referenceCode", request.referenceCode());
        audit.put("paidOn", request.paidOn().toString());
        if (settlement != null) {
            audit.put("settlementId", settlement.getId().toString());
        }
        auditService.record(owner, "commission_payment.submit", "commission_payment_reports", report.getId(), null, audit);
        return toResponse(report);
    }

    private static void validatePaidOn(LocalDate paidOn) {
        LocalDate today = LocalDate.now(AvailabilityService.ZONE);
        if (paidOn.isAfter(today)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay thanh toan khong duoc o tuong lai.",
                    Map.of("paidOn", "Ngay thanh toan khong duoc sau hom nay"));
        }
        if (paidOn.isBefore(today.minusDays(MAX_DAYS_BACK))) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay thanh toan qua xa.",
                    Map.of("paidOn", "Chi nhan thanh toan trong " + MAX_DAYS_BACK + " ngay gan day"));
        }
    }

    static CommissionPaymentResponse toResponse(CommissionPaymentReport report) {
        CommissionSettlement settlement = report.getSettlement();
        return new CommissionPaymentResponse(
                report.getId(), settlement == null ? null : settlement.getId(),
                settlement == null ? null : settlement.getPeriodFrom(), settlement == null ? null : settlement.getPeriodTo(),
                report.getAmount(), report.getPaidOn(), report.getReferenceCode(), report.getProofUrl(), report.getNote(),
                report.getStatus(), report.getReviewNote(), report.getReviewedAt(), report.getCreatedAt());
    }
}
