package com.courtly.domain.finance;

import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Nguong han che chu san co cong no hoa hong qua han (2.3.39 - 2.3.41).
 *
 * <p>Muc 1: chan nhan booking moi. Muc 2: tam an san. Dat mot trong hai nguong (so tien hoac
 * so ngay qua han) la ap dung; nguong null la khong dung.
 */
@Entity
@Table(name = "debt_restriction_levels")
@Getter
@Setter
@NoArgsConstructor
public class DebtRestrictionLevel {

    public static final short LEVEL_BLOCK_BOOKINGS = 1;
    public static final short LEVEL_HIDE_VENUES = 2;

    @Id
    @Column(name = "level", nullable = false)
    private short level;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "min_overdue_amount", precision = 14, scale = 2)
    private BigDecimal minOverdueAmount;

    @Column(name = "min_overdue_days")
    private Integer minOverdueDays;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    private User updatedBy;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
