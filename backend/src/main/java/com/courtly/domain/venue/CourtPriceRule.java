package com.courtly.domain.venue;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.ActiveStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cau hinh gia theo san con, thu trong tuan va khung gio (2.1.27). */
@Entity
@Table(name = "court_price_rules")
@Getter
@Setter
@NoArgsConstructor
public class CourtPriceRule extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    /** NULL = ap dung cho moi ngay trong tuan. */
    @Column(name = "day_of_week")
    private Short dayOfWeek;

    @Column(name = "start_time", nullable = false)
    private LocalTime startTime;

    @Column(name = "end_time", nullable = false)
    private LocalTime endTime;

    @Column(name = "price_per_hour", nullable = false, precision = 12, scale = 2)
    private BigDecimal pricePerHour;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "status", nullable = false, length = 30)
    private ActiveStatus status = ActiveStatus.ACTIVE;

    @PrePersist
    void applyPriceDefaults() {
        if (effectiveFrom == null) {
            effectiveFrom = LocalDate.now();
        }
    }
}
