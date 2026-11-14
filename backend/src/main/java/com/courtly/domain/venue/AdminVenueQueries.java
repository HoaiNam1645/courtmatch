package com.courtly.domain.venue;

import com.courtly.common.enums.ApprovalStatus;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Danh sach san cho quan tri vien duyet (2.3.34); chi doc. */
public interface AdminVenueQueries extends Repository<Venue, UUID> {

    /** keyword la mau LIKE (da them %); moi nhat truoc. */
    @Query(value = """
            SELECT v FROM Venue v JOIN FETCH v.owner
            WHERE v.approvalStatus IN :statuses
              AND (:anyDistrict = true OR v.district = :district)
              AND (lower(function('courtly_unaccent', v.name)) LIKE lower(function('courtly_unaccent', :keyword))
                   OR lower(function('courtly_unaccent', coalesce(v.address, ''))) LIKE lower(function('courtly_unaccent', :keyword)))
            ORDER BY v.createdAt DESC, v.id DESC
            """,
            countQuery = """
                    SELECT count(v) FROM Venue v
                    WHERE v.approvalStatus IN :statuses
                      AND (:anyDistrict = true OR v.district = :district)
                      AND (lower(function('courtly_unaccent', v.name)) LIKE lower(function('courtly_unaccent', :keyword))
                           OR lower(function('courtly_unaccent', coalesce(v.address, ''))) LIKE lower(function('courtly_unaccent', :keyword)))
                    """)
    Page<Venue> search(@Param("statuses") Collection<ApprovalStatus> statuses,
                       @Param("anyDistrict") boolean anyDistrict,
                       @Param("district") String district,
                       @Param("keyword") String keyword,
                       Pageable pageable);

    @Query("SELECT v.approvalStatus AS status, count(v) AS total FROM Venue v GROUP BY v.approvalStatus")
    List<ApprovalCount> countByApproval();

    interface ApprovalCount {
        ApprovalStatus getStatus();

        long getTotal();
    }
}
