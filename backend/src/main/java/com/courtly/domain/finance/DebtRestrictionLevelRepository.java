package com.courtly.domain.finance;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DebtRestrictionLevelRepository extends JpaRepository<DebtRestrictionLevel, Short> {

    List<DebtRestrictionLevel> findAllByOrderByLevelAsc();
}
