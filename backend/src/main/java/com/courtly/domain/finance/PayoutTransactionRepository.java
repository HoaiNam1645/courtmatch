package com.courtly.domain.finance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.UUID;

@Repository
public interface PayoutTransactionRepository extends JpaRepository<PayoutTransaction, UUID> {
}
