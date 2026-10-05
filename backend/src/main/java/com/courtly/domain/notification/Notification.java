package com.courtly.domain.notification;

import com.courtly.common.BaseEntity;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Thong bao trong ung dung (2.3.61). */
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Xem cac hang so trong {@link NotificationType}. */
    @Column(name = "type", nullable = false, length = 80)
    private String type;

    @Column(name = "title", nullable = false, length = 255)
    private String title;

    @Column(name = "content")
    private String content;

    /** Metadata dieu huong, vi du {"bookingId": "...", "route": "/bookings/..."}. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "data_json")
    private Map<String, Object> dataJson;

    @Column(name = "read_at")
    private Instant readAt;
}
