package com.courtly.domain.payment;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentStatusHistoryRepository extends JpaRepository<PaymentStatusHistory, UUID> {

    /** Tien trinh trang thai cua mot thanh toan, cu nhat truoc (2.3.27). */
    @Query("select h from PaymentStatusHistory h left join fetch h.changedBy where h.payment.id = :paymentId order by h.createdAt asc, h.id asc")
    List<PaymentStatusHistory> findTimeline(@Param("paymentId") UUID paymentId);
}
