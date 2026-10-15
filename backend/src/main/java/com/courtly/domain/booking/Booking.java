package com.courtly.domain.booking;

import com.courtly.common.AuditedEntity;
import com.courtly.common.enums.BookingStatus;
import com.courtly.domain.account.User;
import com.courtly.domain.venue.Venue;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Don dat san (2.1.28). */
@Entity
@Table(name = "bookings")
@Getter
@Setter
@NoArgsConstructor
public class Booking extends AuditedEntity {

    /** Ma hien thi cho nguoi dung, vi du CT-20260923-0001. */
    @Column(name = "booking_code", nullable = false, unique = true, length = 50)
    private String bookingCode;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "status", nullable = false, length = 40)
    private BookingStatus status = BookingStatus.PENDING_OWNER_CONFIRMATION;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount = BigDecimal.ZERO;

    @Column(name = "note")
    private String note;

    @Column(name = "price_locked_at", nullable = false)
    private Instant priceLockedAt;

    @Column(name = "owner_response_expires_at")
    private Instant ownerResponseExpiresAt;

    @Column(name = "owner_responded_at")
    private Instant ownerRespondedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_responded_by")
    private User ownerRespondedBy;

    @Column(name = "payment_expires_at")
    private Instant paymentExpiresAt;

    @Column(name = "confirmed_at")
    private Instant confirmedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "rejected_at")
    private Instant rejectedAt;

    @Column(name = "rejection_reason")
    private String rejectionReason;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancellation_reason")
    private String cancellationReason;

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("startTime ASC")
    private List<BookingItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "booking", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("createdAt ASC")
    private List<BookingStatusHistory> statusHistory = new ArrayList<>();
}
