package com.courtly.domain.booking;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.BookingItemStatus;
import com.courtly.domain.venue.Court;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mot khung gio tren mot san con trong don dat.
 *
 * <p>Database co exclusion constraint {@code booking_items_no_overlap} chan hai ban ghi
 * pending/confirmed trung gio tren cung mot san, nen khong chi dua vao kiem tra o code.
 */
@Entity
@Table(name = "booking_items")
@Getter
@Setter
@NoArgsConstructor
public class BookingItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "price", nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "status", nullable = false, length = 30)
    private BookingItemStatus status = BookingItemStatus.PENDING;
}
