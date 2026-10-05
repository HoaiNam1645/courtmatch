package com.courtly.domain.venue;

import com.courtly.common.AuditedEntity;
import com.courtly.common.enums.CourtStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** San con trong mot dia diem, vi du San A, San VIP. */
@Entity
@Table(name = "courts", uniqueConstraints =
        @UniqueConstraint(name = "courts_code_unique", columnNames = {"venue_id", "court_code"}))
@Getter
@Setter
@NoArgsConstructor
public class Court extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "court_code", nullable = false, length = 50)
    private String courtCode;

    /** standard hoac vip. */
    @Column(name = "court_type", nullable = false, length = 50)
    private String courtType = "standard";

    /** vinyl, wood, concrete... */
    @Column(name = "surface_type", length = 50)
    private String surfaceType;

    @Column(name = "indoor", nullable = false)
    private boolean indoor = true;

    @Column(name = "status", nullable = false, length = 30)
    private CourtStatus status = CourtStatus.ACTIVE;

    @OneToMany(mappedBy = "court", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CourtPriceRule> priceRules = new ArrayList<>();
}
