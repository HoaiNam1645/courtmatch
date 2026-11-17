package com.courtly.service.matching;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.68: nhan can bang va xac suat thang theo cong thuc Elo. */
class DoubleTeamScoringTest {

    @Test
    @DisplayName("Nhan can bang theo nguong 25 va 75 diem chenh lech")
    void balanceLabelThresholds() {
        assertThat(DoubleTeamService.label(new BigDecimal("0"))).isEqualTo("Rất cân bằng");
        assertThat(DoubleTeamService.label(new BigDecimal("25.00"))).isEqualTo("Rất cân bằng");
        assertThat(DoubleTeamService.label(new BigDecimal("25.01"))).isEqualTo("Cân bằng");
        assertThat(DoubleTeamService.label(new BigDecimal("75.00"))).isEqualTo("Cân bằng");
        assertThat(DoubleTeamService.label(new BigDecimal("75.01"))).isEqualTo("Chênh lệch");
    }

    @Test
    @DisplayName("Xac suat doi 1 thang: bang nhau 50%, hon 400 diem 90.9%, kem 400 diem 9.1%, doi xung")
    void winProbabilityFollowsElo() {
        assertThat(DoubleTeamService.winProbability(new BigDecimal("1200"), new BigDecimal("1200"))).isEqualByComparingTo("50.0");
        assertThat(DoubleTeamService.winProbability(new BigDecimal("1600"), new BigDecimal("1200"))).isEqualByComparingTo("90.9");
        assertThat(DoubleTeamService.winProbability(new BigDecimal("1200"), new BigDecimal("1600"))).isEqualByComparingTo("9.1");
    }
}
