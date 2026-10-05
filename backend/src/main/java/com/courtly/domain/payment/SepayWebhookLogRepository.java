package com.courtly.domain.payment;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SepayWebhookLogRepository extends JpaRepository<SepayWebhookLog, UUID> {

    /** SePay gui lai webhook khi khong nhan duoc 2xx, nen phai chong xu ly trung (2.1.37). */
    boolean existsByTransactionCode(String transactionCode);
}
