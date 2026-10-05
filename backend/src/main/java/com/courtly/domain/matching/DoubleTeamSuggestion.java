package com.courtly.domain.matching;

import com.courtly.common.BaseEntity;
import com.courtly.domain.match.Match;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Goi y chia doi doi nam/nu can bang (2.3.66, 2.3.69). */
@Entity
@Table(name = "double_team_suggestions")
@Getter
@Setter
@NoArgsConstructor
public class DoubleTeamSuggestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "run_id", nullable = false)
    private AlgorithmRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_id")
    private Match match;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "team1_players", nullable = false)
    private List<UUID> team1Players;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "team2_players", nullable = false)
    private List<UUID> team2Players;

    @Column(name = "team1_strength", precision = 8, scale = 2)
    private BigDecimal team1Strength;

    @Column(name = "team2_strength", precision = 8, scale = 2)
    private BigDecimal team2Strength;

    /** Cang gan 0 thi hai doi cang can bang (2.3.68). */
    @Column(name = "balance_score", precision = 8, scale = 2)
    private BigDecimal balanceScore;
}
