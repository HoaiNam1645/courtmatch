package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminCommissionDetailResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.service.audit.AuditService;
import com.courtly.service.finance.CommissionAllocator;
import com.courtly.service.owner.OwnerRestrictionService;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.52 - quan tri vien ghi nhan chu san da hoan tien truc tiep cho nguoi choi va dieu chinh hoa hong cua booking theo phan hoan.
 *
 * <p>Courtly khong giu tien: day chi la xac nhan khoan chu san da tu hoan. Hoa hong, phan voucher Courtly boi va khoan Courtly
 * no chu san cung giam theo ty le phan con lai cua khoan da thu (dung duoc voi hoa hong da dieu chinh o 2.3.51). Hoan toan bo thi
 * hoa hong chua thu thanh waived. Hoa hong da dong ma sau do giam, hoac ky da thu vuot so phai thu moi, thi phan thua duoc giu lam
 * so du tra truoc cua chu san va tru vao hoa hong cu nhat o lan sau.
 */
@Service
@RequiredArgsConstructor
public class AdminRefundService {

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final CommissionAllocator allocator;
    private final OwnerRestrictionService restrictionService;
    private final AdminCommissionDetailService detailService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Transactional
    public AdminCommissionDetailResponse record(UUID adminId, UUID commissionId, BigDecimal amount, String reason) {
        User admin = accessService.requirePermission(adminId, AdminSettlementService.MANAGE);
        String cleanReason = reason.trim();
        if (cleanReason.length() < 5) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ly do hoan tien qua ngan.", Map.of("reason", "Ly do tu 5 den 500 ky tu"));
        }

        List<Map<String, Object>> found = jdbc.queryForList("""
                SELECT booking_id, owner_id, settlement_id, commission_amount, voucher_discount, courtly_owed, status
                FROM booking_commissions WHERE id = ? FOR UPDATE
                """, commissionId);
        if (found.isEmpty()) {
            throw ApiException.notFound("Khong tim thay giao dich doi soat.");
        }
        Map<String, Object> commission = found.getFirst();
        UUID bookingId = (UUID) commission.get("booking_id");
        UUID ownerId = (UUID) commission.get("owner_id");
        UUID settlementId = (UUID) commission.get("settlement_id");
        BigDecimal oldCommission = (BigDecimal) commission.get("commission_amount");
        BigDecimal oldDiscount = (BigDecimal) commission.get("voucher_discount");
        BigDecimal oldOwed = (BigDecimal) commission.get("courtly_owed");
        String oldStatus = (String) commission.get("status");
        if ("waived".equals(oldStatus) || "suspended".equals(oldStatus)) {
            throw conflict("Hoa hong dang " + ("waived".equals(oldStatus) ? "duoc mien" : "tam hoan do tranh chap")
                    + ", khong dieu chinh theo hoan tien duoc.");
        }

        List<Map<String, Object>> payments = jdbc.queryForList("""
                SELECT id, amount FROM payments WHERE booking_id = ? AND status IN ('confirmed', 'partially_refunded')
                ORDER BY created_at DESC LIMIT 1 FOR UPDATE
                """, bookingId);
        if (payments.isEmpty()) {
            throw conflict("Booking chua co khoan thanh toan nao duoc chu san xac nhan da thu, khong the ghi nhan hoan tien.");
        }
        UUID paymentId = (UUID) payments.getFirst().get("id");
        BigDecimal paid = (BigDecimal) payments.getFirst().get("amount");
        BigDecimal refundedBefore = jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM refunds WHERE payment_id = ? AND status = 'confirmed'",
                BigDecimal.class, paymentId);
        BigDecimal refundedAfter = refundedBefore.add(amount);
        if (refundedAfter.compareTo(paid) > 0) {
            throw conflict("So tien hoan vuot so da thu (con co the hoan " + paid.subtract(refundedBefore).stripTrailingZeros().toPlainString()
                    + " dong).");
        }

        Instant now = Instant.now();
        jdbc.queryForObject("SELECT user_id FROM court_owner_profiles WHERE user_id = ? FOR UPDATE", UUID.class, ownerId);
        boolean fullRefund = refundedAfter.compareTo(paid) == 0;
        UUID refundId = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO refunds (id, payment_id, booking_id, amount, reason, status, approved_by, confirmed_by, confirmed_at)
                VALUES (?, ?, ?, ?, ?, 'confirmed', ?, ?, ?)
                """, refundId, paymentId, bookingId, amount, cleanReason, admin.getId(), admin.getId(), Timestamp.from(now));
        jdbc.update("UPDATE payments SET status = ? WHERE id = ?", fullRefund ? "refunded" : "partially_refunded", paymentId);

        // Ty le con lai cua khoan da thu so voi truoc lan hoan nay; ap dung len hoa hong hien tai (ke ca da dieu chinh).
        BigDecimal ratio = paid.subtract(refundedAfter).divide(paid.subtract(refundedBefore), new MathContext(12, RoundingMode.HALF_UP));
        BigDecimal newCommission = scale(oldCommission, ratio, fullRefund);
        BigDecimal newDiscount = scale(oldDiscount, ratio, fullRefund);
        BigDecimal newOwed = scale(oldOwed, ratio, fullRefund);
        BigDecimal delta = newCommission.subtract(oldCommission);
        boolean settledAlready = "settled".equals(oldStatus);
        String newStatus = settledAlready ? "settled" : fullRefund ? "waived" : "adjusted";

        jdbc.update("""
                UPDATE booking_commissions SET commission_amount = ?, voucher_discount = ?, courtly_owed = ?, status = ? WHERE id = ?
                """, newCommission, newDiscount, newOwed, newStatus, commissionId);

        BigDecimal credit = jdbc.queryForObject("SELECT commission_credit FROM court_owner_profiles WHERE user_id = ?", BigDecimal.class, ownerId);
        BigDecimal creditAdded = BigDecimal.ZERO;
        List<UUID> settled = List.of();
        Map<String, Object> settlementAudit = null;

        if (settledAlready) {
            // Da dong roi: phan chu san tra thua thanh so du.
            creditAdded = oldCommission.subtract(newCommission);
        } else if (settlementId != null && delta.signum() != 0) {
            Map<String, Object> settlement = jdbc.queryForMap(
                    "SELECT amount_due, amount_paid, adjustment_total, status FROM commission_settlements WHERE id = ? FOR UPDATE", settlementId);
            BigDecimal due = (BigDecimal) settlement.get("amount_due");
            BigDecimal settlementPaid = (BigDecimal) settlement.get("amount_paid");
            BigDecimal adjustment = (BigDecimal) settlement.get("adjustment_total");
            String settlementStatus = (String) settlement.get("status");
            BigDecimal newDueRaw = due.add(delta);
            BigDecimal newDue = newDueRaw.max(settlementPaid);
            creditAdded = settlementPaid.subtract(newDueRaw).max(BigDecimal.ZERO);
            boolean fullyPaid = newDue.compareTo(settlementPaid) == 0;
            settled = allocator.settleForSettlement(settlementId, settlementPaid, fullyPaid, now);
            String settlementNewStatus = fullyPaid ? "paid" : settlementStatus;
            jdbc.update("UPDATE commission_settlements SET adjustment_total = ?, amount_due = ?, status = ?, paid_at = ? WHERE id = ?",
                    adjustment.add(newDue.subtract(due)), newDue, settlementNewStatus,
                    fullyPaid && !"paid".equals(settlementStatus) ? Timestamp.from(now) : null, settlementId);
            settlementAudit = new LinkedHashMap<>();
            settlementAudit.put("commissionId", commissionId.toString());
            settlementAudit.put("refundId", refundId.toString());
            settlementAudit.put("delta", plain(newDue.subtract(due)));
            settlementAudit.put("amountDue", plain(newDue));
            settlementAudit.put("status", settlementNewStatus);
            settlementAudit.put("reason", cleanReason);
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("amountDue", plain(due));
            before.put("status", settlementStatus);
            auditService.record(admin, "settlement.adjust", "commission_settlement", settlementId, before, settlementAudit);
        }
        // Phan thua (hoa hong da dong / ky da thu vuot) cong vao so du; so du dong dan hoa hong cu nhat chua thuoc ky.
        BigDecimal pool = credit.add(creditAdded);
        List<UUID> settledByCredit = List.of();
        BigDecimal creditAfter = pool;
        if (pool.signum() > 0) {
            CommissionAllocator.CreditResult result = allocator.applyCredit(ownerId, pool, now);
            settledByCredit = result.settled();
            creditAfter = result.creditLeft();
        }
        if (creditAfter.compareTo(credit) != 0) {
            jdbc.update("UPDATE court_owner_profiles SET commission_credit = ? WHERE user_id = ?", creditAfter, ownerId);
        }
        List<UUID> allSettled = new java.util.ArrayList<>(settled);
        allSettled.addAll(settledByCredit);

        Map<String, Object> refundAudit = new LinkedHashMap<>();
        refundAudit.put("amount", plain(amount));
        refundAudit.put("refundedTotal", plain(refundedAfter));
        refundAudit.put("paymentAmount", plain(paid));
        refundAudit.put("commissionId", commissionId.toString());
        refundAudit.put("reason", cleanReason);
        auditService.record(admin, "refund.record", "refunds", refundId, null, refundAudit);

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("commissionAmount", plain(oldCommission));
        before.put("status", oldStatus);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("commissionAmount", plain(newCommission));
        after.put("status", newStatus);
        after.put("refundId", refundId.toString());
        after.put("refunded", plain(amount));
        after.put("reason", cleanReason);
        if (creditAfter.compareTo(credit) != 0) {
            after.put("credit", plain(creditAfter));
        }
        auditService.record(admin, "commission.refund_adjust", "booking_commission", commissionId, before, after);
        for (UUID id : allSettled) {
            if (!id.equals(commissionId)) {
                auditService.record(admin, "commission.settle", "booking_commission", id, Map.of("status", "due"),
                        Map.of("reason", "Hoan tien " + refundId));
            }
        }

        restrictionService.recompute(ownerId);
        entityManager.flush();
        return detailService.load(commissionId);
    }

    private static BigDecimal scale(BigDecimal value, BigDecimal ratio, boolean zero) {
        return zero ? BigDecimal.ZERO : value.multiply(ratio).setScale(0, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private static ApiException conflict(String message) {
        return ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID, message, Map.of("refund", message));
    }
}
