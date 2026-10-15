package com.courtly.domain.finance;

import com.courtly.common.enums.ActiveStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PlatformFeeConfigRepository extends JpaRepository<PlatformFeeConfig, UUID> {
    Optional<PlatformFeeConfig> findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus status);
}
