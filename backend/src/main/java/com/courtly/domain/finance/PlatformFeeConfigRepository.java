package com.courtly.domain.finance;

import com.courtly.common.enums.ActiveStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PlatformFeeConfigRepository extends JpaRepository<PlatformFeeConfig, UUID> {
    Optional<PlatformFeeConfig> findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus status);

    /** Tat ca chinh sach, moi nhat truoc, kem nguoi tao. */
    @Query("SELECT c FROM PlatformFeeConfig c LEFT JOIN FETCH c.createdBy ORDER BY c.effectiveFrom DESC, c.createdAt DESC")
    List<PlatformFeeConfig> findAllWithCreator();

    /** So hoa hong da ghi nhan va tong tien theo tung chinh sach. */
    @Query("""
            SELECT bc.feeConfig.id AS configId, count(bc) AS total, coalesce(sum(bc.commissionAmount), 0) AS amount
            FROM BookingCommission bc WHERE bc.feeConfig IS NOT NULL GROUP BY bc.feeConfig.id
            """)
    List<UsageRow> usageByConfig();

    interface UsageRow {
        UUID getConfigId();

        long getTotal();

        BigDecimal getAmount();
    }
}
