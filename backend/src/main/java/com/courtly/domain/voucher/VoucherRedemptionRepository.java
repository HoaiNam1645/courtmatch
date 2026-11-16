package com.courtly.domain.voucher;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, UUID> {

    Optional<VoucherRedemption> findByBookingId(UUID bookingId);

    boolean existsByVoucherIdAndUserIdAndStatus(UUID voucherId, UUID userId, String status);

    long countByVoucherIdAndStatus(UUID voucherId, String status);

    @Query("SELECT r.voucher.id FROM VoucherRedemption r WHERE r.user.id = :userId AND r.status = 'applied'")
    List<UUID> appliedVoucherIds(@Param("userId") UUID userId);

    /** Luot dung (con hieu luc) va tong tien giam theo tung voucher. */
    @Query("""
            SELECT r.voucher.id AS voucherId, count(r) AS total, coalesce(sum(r.discountAmount), 0) AS discount
            FROM VoucherRedemption r WHERE r.status = 'applied' AND r.voucher.id IN :ids GROUP BY r.voucher.id
            """)
    List<UsageRow> usage(@Param("ids") Collection<UUID> ids);

    @Query("""
            SELECT r FROM VoucherRedemption r JOIN FETCH r.user JOIN FETCH r.booking
            WHERE r.voucher.id = :voucherId ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<VoucherRedemption> findByVoucher(@Param("voucherId") UUID voucherId);

    interface UsageRow {
        UUID getVoucherId();

        long getTotal();

        BigDecimal getDiscount();
    }
}
