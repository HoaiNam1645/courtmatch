package com.courtly.domain.venue;

import com.courtly.common.AuditedEntity;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.domain.account.User;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.locationtech.jts.geom.Point;

/** Dia diem / cum san cau long thuoc mot chu san. */
@Entity
@Table(name = "venues")
@Getter
@Setter
@NoArgsConstructor
public class Venue extends AuditedEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "slug", nullable = false, unique = true, length = 220)
    private String slug;

    @Column(name = "description")
    private String description;

    @Column(name = "address")
    private String address;

    @Column(name = "ward", length = 100)
    private String ward;

    @Column(name = "district", length = 100)
    private String district;

    @Column(name = "province", length = 100)
    private String province;

    @Column(name = "country", nullable = false, length = 100)
    private String country = "VN";

    @Column(name = "latitude", precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(name = "longitude", precision = 10, scale = 7)
    private BigDecimal longitude;

    /** Chi doc: trigger {@code courtly_sync_location} tu sinh tu latitude/longitude. */
    @JdbcTypeCode(SqlTypes.GEOGRAPHY)
    @Column(name = "location", insertable = false, updatable = false)
    private Point location;

    @Column(name = "phone", length = 30)
    private String phone;

    @Column(name = "email", length = 255)
    private String email;

    @Column(name = "status", nullable = false, length = 30)
    private VenueStatus status = VenueStatus.PENDING;

    @Column(name = "approval_status", nullable = false, length = 30)
    private ApprovalStatus approvalStatus = ApprovalStatus.PENDING;

    /** Cache tu venue_reviews de khong phai tinh lai moi lan hien danh sach san. */
    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("courtCode ASC")
    private List<Court> courts = new ArrayList<>();

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("displayOrder ASC")
    private List<VenueImage> images = new ArrayList<>();

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("dayOfWeek ASC")
    private List<VenueOperatingHours> operatingHours = new ArrayList<>();

    @OneToMany(mappedBy = "venue", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<VenueService> venueServices = new LinkedHashSet<>();
}
