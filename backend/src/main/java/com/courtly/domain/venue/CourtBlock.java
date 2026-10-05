package com.courtly.domain.venue;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.BlockStatus;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Khoa khung gio san vi bao tri hoac su kien (2.3.10). */
@Entity
@Table(name = "court_blocks")
@Getter
@Setter
@NoArgsConstructor
public class CourtBlock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "court_id", nullable = false)
    private Court court;

    @Column(name = "start_time", nullable = false)
    private Instant startTime;

    @Column(name = "end_time", nullable = false)
    private Instant endTime;

    @Column(name = "reason")
    private String reason;

    @Column(name = "status", nullable = false, length = 30)
    private BlockStatus status = BlockStatus.ACTIVE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;
}
