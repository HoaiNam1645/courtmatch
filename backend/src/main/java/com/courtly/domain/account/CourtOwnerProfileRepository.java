package com.courtly.domain.account;

import java.math.BigDecimal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface CourtOwnerProfileRepository extends JpaRepository<CourtOwnerProfile, UUID> {

    /** So du chu san da thanh toan hoa hong nhung chua du dong mot hoa hong nguyen ven (2.3.47). */
    @Query(value = "select coalesce(max(commission_credit), 0) from court_owner_profiles where user_id = :ownerId", nativeQuery = true)
    BigDecimal commissionCreditOf(UUID ownerId);
}
