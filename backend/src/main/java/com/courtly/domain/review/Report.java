package com.courtly.domain.review;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.ReportStatus;
import com.courtly.common.enums.ReportTargetType;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Bao cao nguoi dung, san hoac danh gia vi pham (2.3.36, 2.3.37). */
@Entity
@Table(name = "reports")
@Getter
@Setter
@NoArgsConstructor
public class Report extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reporter_id", nullable = false)
    private User reporter;

    @Column(name = "target_type", nullable = false, length = 50)
    private ReportTargetType targetType;

    /** Khoa da hinh: khong khai bao FK vi tro toi nhieu bang khac nhau. */
    @Column(name = "target_id", nullable = false)
    private UUID targetId;

    @Column(name = "reason", nullable = false, length = 100)
    private String reason;

    @Column(name = "description")
    private String description;

    @Column(name = "status", nullable = false, length = 30)
    private ReportStatus status = ReportStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "handled_by")
    private User handledBy;

    @Column(name = "handled_at")
    private Instant handledAt;
}
