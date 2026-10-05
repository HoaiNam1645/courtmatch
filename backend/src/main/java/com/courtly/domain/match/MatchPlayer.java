package com.courtly.domain.match;

import com.courtly.common.BaseEntity;
import com.courtly.common.enums.JoinedStatus;
import com.courtly.domain.account.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Nguoi choi tham gia mot tran dau. */
@Entity
@Table(name = "match_players", uniqueConstraints =
        @UniqueConstraint(name = "match_players_unique", columnNames = {"match_id", "user_id"}))
@Getter
@Setter
@NoArgsConstructor
public class MatchPlayer extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "match_id", nullable = false)
    private Match match;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** 1 hoac 2. */
    @Column(name = "team_no", nullable = false)
    private short teamNo;

    @Column(name = "position_no")
    private Short positionNo;

    @Column(name = "joined_status", nullable = false, length = 30)
    private JoinedStatus joinedStatus = JoinedStatus.INVITED;
}
