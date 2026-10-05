package com.courtly.domain.account;

import com.courtly.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/** Khu vuc choi uu tien cua nguoi choi (2.1.11). */
@Entity
@Table(name = "player_preferred_locations")
@Getter
@Setter
@NoArgsConstructor
public class PlayerPreferredLocation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "label", length = 100)
    private String label;

    @Column(name = "address")
    private String address;

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /**
     * Chi doc: trigger {@code courtly_sync_location} tu sinh tu latitude/longitude
     * moi lan insert/update nen ung dung chi can set toa do.
     */
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "location", insertable = false, updatable = false)
    private Point location;

    @Column(name = "radius_km", nullable = false, precision = 6, scale = 2)
    private BigDecimal radiusKm = BigDecimal.valueOf(5);

    @Column(name = "is_default", nullable = false)
    private boolean isDefault;
}
