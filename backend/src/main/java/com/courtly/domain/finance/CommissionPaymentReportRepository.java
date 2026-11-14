package com.courtly.domain.finance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommissionPaymentReportRepository extends JpaRepository<CommissionPaymentReport, UUID> {

    /** Ma tham chieu da dung cho bao cao chua bi tu choi (2.3.24). */
    @Query("""
            select count(r) > 0 from CommissionPaymentReport r
            where r.owner.id = :ownerId and lower(r.referenceCode) = lower(:referenceCode) and r.status <> 'rejected'
            """)
    boolean referenceInUse(@Param("ownerId") UUID ownerId, @Param("referenceCode") String referenceCode);

    /** Tong tien dang cho Courtly xac nhan cua mot ky (2.3.24). */
    @Query("""
            select coalesce(sum(r.amount), 0) from CommissionPaymentReport r
            where r.settlement.id = :settlementId and r.status = 'pending_review'
            """)
    BigDecimal sumPendingForSettlement(@Param("settlementId") UUID settlementId);

    /** Tong tien dang cho xac nhan khong gan ky doi soat nao (2.3.24). */
    @Query("""
            select coalesce(sum(r.amount), 0) from CommissionPaymentReport r
            where r.owner.id = :ownerId and r.settlement is null and r.status = 'pending_review'
            """)
    BigDecimal sumPendingWithoutSettlement(@Param("ownerId") UUID ownerId);

    Optional<CommissionPaymentReport> findByIdAndOwnerId(UUID id, UUID ownerId);

    Optional<CommissionPaymentReport> findFirstByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    /** Tong so tien va so lan bao cao theo trang thai (2.3.25, 2.3.26). */
    @Query("select r.status as status, count(r) as total, coalesce(sum(r.amount), 0) as amount from CommissionPaymentReport r where r.owner.id = :ownerId group by r.status")
    List<StatusTotal> totalsByStatus(@Param("ownerId") UUID ownerId);

    /** Tien dang cho xac nhan theo tung ky doi soat (2.3.25). */
    @Query("select r.settlement.id as settlementId, coalesce(sum(r.amount), 0) as amount from CommissionPaymentReport r where r.owner.id = :ownerId and r.settlement is not null and r.status = 'pending_review' group by r.settlement.id")
    List<SettlementPending> pendingBySettlement(@Param("ownerId") UUID ownerId);

    interface StatusTotal {
        String getStatus();

        long getTotal();

        BigDecimal getAmount();
    }

    interface SettlementPending {
        UUID getSettlementId();

        BigDecimal getAmount();
    }

    /** Lich su thanh toan hoa hong, loc theo trang thai va ngay thanh toan [fromDate, toDate], moi nhat truoc (2.3.26). */
    @Query(value = """
            select r from CommissionPaymentReport r
            left join fetch r.settlement
            where r.owner.id = :ownerId and r.status in :statuses
              and r.paidOn >= :fromDate and r.paidOn <= :toDate
            order by r.createdAt desc, r.id desc
            """,
            countQuery = """
                    select count(r) from CommissionPaymentReport r
                    where r.owner.id = :ownerId and r.status in :statuses
                      and r.paidOn >= :fromDate and r.paidOn <= :toDate
                    """)
    org.springframework.data.domain.Page<CommissionPaymentReport> findHistory(
            @Param("ownerId") UUID ownerId, @Param("statuses") java.util.Collection<String> statuses,
            @Param("fromDate") java.time.LocalDate fromDate, @Param("toDate") java.time.LocalDate toDate,
            org.springframework.data.domain.Pageable pageable);

    /** Tong tien cua tap da loc, khong phan trang (2.3.26). */
    @Query("""
            select coalesce(sum(r.amount), 0) from CommissionPaymentReport r
            where r.owner.id = :ownerId and r.status in :statuses
              and r.paidOn >= :fromDate and r.paidOn <= :toDate
            """)
    BigDecimal sumHistory(@Param("ownerId") UUID ownerId, @Param("statuses") java.util.Collection<String> statuses,
                          @Param("fromDate") java.time.LocalDate fromDate, @Param("toDate") java.time.LocalDate toDate);
}
