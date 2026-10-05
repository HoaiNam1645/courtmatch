package com.courtly.domain.venue;

import com.courtly.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Anh cua dia diem san, hien thi theo display_order (2.1.24). */
@Entity
@Table(name = "venue_images")
@Getter
@Setter
@NoArgsConstructor
public class VenueImage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "venue_id", nullable = false)
    private Venue venue;

    @Column(name = "image_url", nullable = false)
    private String imageUrl;

    @Column(name = "caption", length = 255)
    private String caption;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    /** Moi venue chi duoc co toi da mot anh bia (unique partial index trong migration). */
    @Column(name = "is_cover", nullable = false)
    private boolean cover;
}
