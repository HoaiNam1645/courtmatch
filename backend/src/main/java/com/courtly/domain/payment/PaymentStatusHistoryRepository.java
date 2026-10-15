package com.courtly.domain.payment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, UUID> {
}
