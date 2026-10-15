package com.courtly.domain.payment;

import com.courtly.common.AuditedEntity;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.Booking;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One compact dispute record containing both sides' statements and evidence lists. */
@Entity
@Table(name = "booking_disputes")
@Getter
@Setter
@NoArgsConstructor
public class BookingDispute extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "booking_id", nullable = false)
    private Booking booking;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id")
    private Payment payment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "category", nullable = false, length = 50)
    private String category;

    @Column(name = "description", nullable = false)
    private String description;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "reporter_evidences_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> reporterEvidences;

    @Column(name = "refund_requested", nullable = false)
    private boolean refundRequested;

    @Column(name = "requested_refund_amount", precision = 12, scale = 2)
    private BigDecimal requestedRefundAmount;

    @Column(name = "status", nullable = false, length = 30)
    private String status = "open";

    @Column(name = "owner_response_deadline")
    private Instant ownerResponseDeadline;

    @Column(name = "owner_response")
    private String ownerResponse;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_responded_by")
    private User ownerRespondedBy;

    @Column(name = "owner_responded_at")
    private Instant ownerRespondedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "owner_evidences_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> ownerEvidences;

    @Column(name = "resolution", length = 30)
    private String resolution;

    @Column(name = "resolution_note")
    private String resolutionNote;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resolution_evidences_json", columnDefinition = "jsonb")
    private List<Map<String, Object>> resolutionEvidences;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;
}
