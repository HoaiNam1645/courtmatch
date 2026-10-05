package com.courtly.domain.finance;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.FeeType;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Cau hinh phi nen tang (2.3.39). Chi mot ban ghi active tai mot thoi diem. */
@Entity
@Table(name = "platform_fee_configs")
@Getter
@Setter
@NoArgsConstructor
public class PlatformFeeConfig extends BaseEntity {

    @Column(name = "fee_type", nullable = false, length = 30)
    private FeeType feeType = FeeType.PERCENTAGE;

    /** percentage: 0-100. fixed: so tien VND tren moi booking. */
    @Column(name = "fee_value", nullable = false, precision = 12, scale = 2)
    private BigDecimal feeValue;

    @Column(name = "effective_from", nullable = false)
    private Instant effectiveFrom;

    @Column(name = "effective_to")
    private Instant effectiveTo;

    @Column(name = "status", nullable = false, length = 30)
    private ActiveStatus status = ActiveStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @PrePersist
    void applyFeeDefaults() {
        if (effectiveFrom == null) {
            effectiveFrom = Instant.now();
        }
    }
}
