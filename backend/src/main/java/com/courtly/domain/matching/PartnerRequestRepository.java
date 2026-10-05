package com.courtly.domain.matching;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PartnerRequestRepository extends JpaRepository<PartnerRequest, UUID> {
}
