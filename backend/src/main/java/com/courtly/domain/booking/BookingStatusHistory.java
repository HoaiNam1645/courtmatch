package com.courtly.domain.booking;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Timeline doi trang thai don, dung cho man hinh chi tiet booking (2.1.30). */
@Entity
@Table(name = "booking_status_history")
@Getter
@Setter
@NoArgsConstructor
public class BookingStatusHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @Column(name = "old_status", length = 30)
    private BookingStatus oldStatus;

    @Column(name = "new_status", nullable = false, length = 30)
    private BookingStatus newStatus;

    @Column(name = "reason")
    private String reason;

    /** NULL khi trang thai do he thong tu dong cap nhat. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;
}
