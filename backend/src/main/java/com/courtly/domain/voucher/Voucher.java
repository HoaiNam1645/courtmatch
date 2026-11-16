package com.courtly.domain.voucher;

import com.courtly.common.AuditedEntity;
import com.courtly.domain.account.User;
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
 * Voucher giam gia theo phan tram (B4). Courtly chiu phan giam; dieu kien nhan do quan tri vien dat cho
 * tung voucher (Elo toi thieu, so tran co booking, so tuan hoat dong lien tiep), de trong la khong yeu cau.
 */
@Entity
@Table(name = "vouchers")
@Getter
@Setter
@NoArgsConstructor
public class Voucher extends AuditedEntity {

    public static final String ACTIVE = "active";
    public static final String LOCKED = "locked";

    @Column(name = "code", nullable = false, length = 40)
    private String code;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "description")
    private String description;

    @Column(name = "discount_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal discountPercent;

    /** Tran giam toi da moi booking (VND); null = khong gioi han. */
    @Column(name = "max_discount_amount", precision = 12, scale = 2)
    private BigDecimal maxDiscountAmount;

    @Column(name = "valid_from", nullable = false)
    private Instant validFrom;

    @Column(name = "valid_until", nullable = false)
    private Instant validUntil;

    /** Tong so luot phat hanh; null = khong gioi han. */
    @Column(name = "total_limit")
    private Integer totalLimit;

    @Column(name = "min_elo", precision = 8, scale = 2)
    private BigDecimal minElo;

    @Column(name = "min_booking_matches")
    private Integer minBookingMatches;

    @Column(name = "min_active_weeks")
    private Integer minActiveWeeks;

    @Column(name = "status", nullable = false, length = 20)
    private String status = ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
