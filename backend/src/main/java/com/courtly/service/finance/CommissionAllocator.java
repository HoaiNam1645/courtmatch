package com.courtly.service.finance;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Dong dan hoa hong khi cong no duoc tra (2.3.47, 2.3.49) hoac giam (2.3.51): hoa hong chuyen sang settled khi du tien.
 *
 * <p>Phai goi ben trong transaction cua nghiep vu; cac dong hoa hong duoc khoa de hai thao tac khong dong cung mot hoa hong.
 */
@Component
@RequiredArgsConstructor
public class CommissionAllocator {

    private final JdbcTemplate jdbc;

    /**
     * Dong dan hoa hong cua ky theo han thanh toan: du tien thi dong, thanh toan du ky ({@code fullyPaid}) thi dong het.
     *
     * @param paidAfter so tien da thu cua ky sau thao tac
     * @return id cac hoa hong vua chuyen sang settled
     */
    public List<UUID> settleForSettlement(UUID settlementId, BigDecimal paidAfter, boolean fullyPaid, Instant now) {
        BigDecimal alreadySettled = jdbc.queryForObject(
                "SELECT coalesce(sum(commission_amount), 0) FROM booking_commissions WHERE settlement_id = ? AND status = 'settled'",
                BigDecimal.class, settlementId);
        BigDecimal budget = paidAfter.subtract(alreadySettled);
        List<UUID> settled = new ArrayList<>();
        List<Object[]> unsettled = jdbc.query("""
                SELECT id, commission_amount FROM booking_commissions
                WHERE settlement_id = ? AND status IN ('pending', 'due', 'adjusted') ORDER BY due_at, id FOR UPDATE
                """, (rs, index) -> new Object[] {rs.getObject("id", UUID.class), rs.getBigDecimal("commission_amount")}, settlementId);
        for (Object[] item : unsettled) {
            BigDecimal amount = (BigDecimal) item[1];
            if (!fullyPaid && amount.compareTo(budget) > 0) {
                break;
            }
            jdbc.update("UPDATE booking_commissions SET status = 'settled', settled_at = ? WHERE id = ?", Timestamp.from(now), item[0]);
            budget = budget.subtract(amount);
            settled.add((UUID) item[0]);
        }
        return settled;
    }

    /** Ket qua dong dan bang so du: cac hoa hong da dong va so du con lai. */
    public record CreditResult(List<UUID> settled, BigDecimal creditLeft) {
    }

    /**
     * Dung so du de dong cac hoa hong cu nhat chua thuoc ky nao cho den khi het du tien dong mot hoa hong nguyen ven.
     */
    public CreditResult applyCredit(UUID ownerId, BigDecimal creditAvailable, Instant now) {
        BigDecimal left = creditAvailable;
        List<UUID> settled = new ArrayList<>();
        List<Object[]> unsettled = jdbc.query("""
                SELECT id, commission_amount FROM booking_commissions
                WHERE owner_id = ? AND settlement_id IS NULL AND status IN ('pending', 'due', 'adjusted')
                ORDER BY due_at, id FOR UPDATE
                """, (rs, index) -> new Object[] {rs.getObject("id", UUID.class), rs.getBigDecimal("commission_amount")}, ownerId);
        for (Object[] item : unsettled) {
            BigDecimal amount = (BigDecimal) item[1];
            if (amount.compareTo(left) > 0) {
                break;
            }
            jdbc.update("UPDATE booking_commissions SET status = 'settled', settled_at = ? WHERE id = ?", Timestamp.from(now), item[0]);
            left = left.subtract(amount);
            settled.add((UUID) item[0]);
        }
        return new CreditResult(settled, left);
    }
}
