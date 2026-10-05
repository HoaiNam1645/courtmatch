package com.courtly.domain.match;

import com.courtly.common.BaseEntity;
import com.courtly.domain.account.User;
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

/** Lich su thay doi diem rating sau moi tran (2.2.39). */
@Entity
@Table(name = "player_rating_history")
@Getter
@Setter
@NoArgsConstructor
public class PlayerRatingHistory extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @Column(name = "old_rating", nullable = false, precision = 8, scale = 2)
    private BigDecimal oldRating;

    @Column(name = "new_rating", nullable = false, precision = 8, scale = 2)
    private BigDecimal newRating;

    @Column(name = "change_amount", nullable = false, precision = 8, scale = 2)
    private BigDecimal changeAmount;

    /** Vi du: match_win, match_loss, manual_adjustment. */
    @Column(name = "reason", length = 100)
    private String reason;
}
