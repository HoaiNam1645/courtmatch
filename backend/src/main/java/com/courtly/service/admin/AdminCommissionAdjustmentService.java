package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminCommissionDetailResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.service.audit.AuditService;
import com.courtly.service.finance.CommissionAllocator;
import com.courtly.service.owner.OwnerRestrictionService;
import java.math.BigDecimal;
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
 * 2.3.51 - quan tri vien dieu chinh cong no bang cach dieu chinh hoa hong cua mot booking.
 *
 * <p>Cong no luon tong hop truc tiep tu hoa hong tung booking nen dieu chinh sua chinh hoa hong (chuyen sang adjusted),
 * khong co so cai rieng. Hoa hong thuoc ky thi so phai thu cua ky doi theo; thanh toan khong gan ky thi so du co the
 * dong them hoa hong. Moi lan dieu chinh co ly do va duoc ghi vao audit_logs.
 */
@Service
@RequiredArgsConstructor
public class AdminCommissionAdjustmentService {

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;
    private final AuditService auditService;
    private final CommissionAllocator allocator;
    private final OwnerRestrictionService restrictionService;
    private final AdminCommissionDetailService detailService;

    @jakarta.persistence.PersistenceContext
    private jakarta.persistence.EntityManager entityManager;

    @Transactional
    public AdminCommissionDetailResponse adjust(UUID adminId, UUID commissionId, BigDecimal newAmount, String reason) {
        User admin = accessService.requirePermission(adminId, AdminSettlementService.MANAGE);
        String cleanReason = reason.trim();
        if (cleanReason.length() < 5) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ly do dieu chinh qua ngan.",
                    Map.of("reason", "Ly do tu 5 den 500 ky tu"));
        }

        List<Object[]> found = jdbc.query("""
                SELECT owner_id, settlement_id, commission_amount, status FROM booking_commissions WHERE id = ? FOR UPDATE
                """, (rs, index) -> new Object[] {rs.getObject("owner_id", UUID.class), rs.getObject("settlement_id", UUID.class),
                rs.getBigDecimal("commission_amount"), rs.getString("status")}, commissionId);
        if (found.isEmpty()) {
            throw ApiException.notFound("Khong tim thay giao dich doi soat.");
        }
        UUID ownerId = (UUID) found.getFirst()[0];
        UUID settlementId = (UUID) found.getFirst()[1];
        BigDecimal oldAmount = (BigDecimal) found.getFirst()[2];
        String oldStatus = (String) found.getFirst()[3];
        if (!List.of("pending", "due", "adjusted").contains(oldStatus)) {
            throw conflict("Chi dieu chinh duoc hoa hong chua thu (khong phai da doi soat, duoc mien hoac tam hoan).");
        }
        BigDecimal delta = newAmount.subtract(oldAmount);
        if (delta.signum() == 0) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "So tien khong thay doi.",
                    Map.of("newAmount", "So tien moi phai khac so hien tai"));
        }

        Instant now = Instant.now();
        jdbc.queryForObject("SELECT user_id FROM court_owner_profiles WHERE user_id = ? FOR UPDATE", UUID.class, ownerId);
        Map<String, Object> settlementAudit = null;
        List<UUID> settled;
        BigDecimal creditChange = null;

        if (settlementId != null) {
            Map<String, Object> settlement = jdbc.queryForMap(
                    "SELECT amount_due, amount_paid, adjustment_total, status FROM commission_settlements WHERE id = ? FOR UPDATE", settlementId);
            BigDecimal due = (BigDecimal) settlement.get("amount_due");
            BigDecimal paid = (BigDecimal) settlement.get("amount_paid");
            BigDecimal adjustment = (BigDecimal) settlement.get("adjustment_total");
            String settlementStatus = (String) settlement.get("status");
            if ("paid".equals(settlementStatus)) {
                throw conflict("Ky doi soat nay da thanh toan xong, khong dieu chinh duoc.");
            }
            BigDecimal newDue = due.add(delta);
            if (newDue.compareTo(paid) < 0) {
                throw conflict("Sau dieu chinh so phai thu cua ky (" + newDue.stripTrailingZeros().toPlainString()
                        + " dong) nho hon so da thu (" + paid.stripTrailingZeros().toPlainString() + " dong).");
            }
            updateCommission(commissionId, newAmount);
            boolean fullyPaid = newDue.compareTo(paid) == 0;
            settled = allocator.settleForSettlement(settlementId, paid, fullyPaid, now);
            String newStatus = fullyPaid ? "paid" : settlementStatus;
            jdbc.update("""
                    UPDATE commission_settlements SET adjustment_total = ?, amount_due = ?, status = ?, paid_at = ? WHERE id = ?
                    """, adjustment.add(delta), newDue, newStatus, fullyPaid ? Timestamp.from(now) : null, settlementId);
            settlementAudit = new LinkedHashMap<>();
            settlementAudit.put("commissionId", commissionId.toString());
            settlementAudit.put("delta", plain(delta));
            settlementAudit.put("amountDue", plain(newDue));
            settlementAudit.put("adjustmentTotal", plain(adjustment.add(delta)));
            settlementAudit.put("status", newStatus);
            settlementAudit.put("reason", cleanReason);
            Map<String, Object> before = new LinkedHashMap<>();
            before.put("amountDue", plain(due));
            before.put("adjustmentTotal", plain(adjustment));
            before.put("status", settlementStatus);
            auditService.record(admin, "settlement.adjust", "commission_settlement", settlementId, before, settlementAudit);
        } else {
            updateCommission(commissionId, newAmount);
            BigDecimal credit = jdbc.queryForObject("SELECT commission_credit FROM court_owner_profiles WHERE user_id = ?",
                    BigDecimal.class, ownerId);
            CommissionAllocator.CreditResult result = allocator.applyCredit(ownerId, credit, now);
            settled = result.settled();
            if (result.creditLeft().compareTo(credit) != 0) {
                jdbc.update("UPDATE court_owner_profiles SET commission_credit = ? WHERE user_id = ?", result.creditLeft(), ownerId);
                creditChange = result.creditLeft();
            }
        }

        Map<String, Object> before = new LinkedHashMap<>();
        before.put("commissionAmount", plain(oldAmount));
        before.put("status", oldStatus);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("commissionAmount", plain(newAmount));
        after.put("status", settled.contains(commissionId) ? "settled" : "adjusted");
        after.put("delta", plain(delta));
        after.put("reason", cleanReason);
        if (creditChange != null) {
            after.put("credit", plain(creditChange));
        }
        auditService.record(admin, "commission.adjust", "booking_commission", commissionId, before, after);
        for (UUID id : settled) {
            if (!id.equals(commissionId)) {
                auditService.record(admin, "commission.settle", "booking_commission", id, Map.of("status", "due"),
                        Map.of("reason", "Dieu chinh cong no " + commissionId));
            }
        }

        restrictionService.recompute(ownerId);
        // Nhat ky audit ghi qua JPA: day xuong database de chi tiet tra ve (doc bang JDBC) co dong lich su vua tao.
        entityManager.flush();
        return detailService.load(commissionId);
    }

    /** So tien dang chu so khong thua cho nhat ky (18000 thay vi 18000.00). */
    private static String plain(BigDecimal value) {
        return value.signum() == 0 ? "0" : value.stripTrailingZeros().toPlainString();
    }

    private void updateCommission(UUID commissionId, BigDecimal newAmount) {
        jdbc.update("UPDATE booking_commissions SET commission_amount = ?, status = 'adjusted' WHERE id = ?", newAmount, commissionId);
    }

    private static ApiException conflict(String message) {
        return ApiException.conflict(ErrorCode.COMMISSION_PAYMENT_INVALID, message, Map.of("commission", message));
    }
}
