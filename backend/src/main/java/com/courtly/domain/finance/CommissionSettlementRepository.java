package com.courtly.domain.finance;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CommissionSettlementRepository extends JpaRepository<CommissionSettlement, UUID> {

    /** Ky doi soat cua chu san, ky moi nhat truoc (2.3.23). */
    @Query(value = """
            select s from CommissionSettlement s
            where s.owner.id = :ownerId and s.status in :statuses
            order by s.periodTo desc, s.periodFrom desc, s.id desc
            """,
            countQuery = "select count(s) from CommissionSettlement s where s.owner.id = :ownerId and s.status in :statuses")
    Page<CommissionSettlement> findForOwner(@Param("ownerId") UUID ownerId,
                                            @Param("statuses") Collection<String> statuses, Pageable pageable);

    Optional<CommissionSettlement> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Query("select s.status as status, count(s) as total from CommissionSettlement s where s.owner.id = :ownerId group by s.status")
    List<StatusCount> countByStatus(@Param("ownerId") UUID ownerId);

    interface StatusCount {
        String getStatus();

        long getTotal();
    }
}
