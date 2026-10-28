package com.courtly.service.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlayerRatingServiceTest {

    @Test
    void keepsHighKFactorThroughThirtyMatches() {
        assertThat(PlayerRatingService.kFactor(0)).isEqualByComparingTo(BigDecimal.valueOf(40));
        assertThat(PlayerRatingService.kFactor(30)).isEqualByComparingTo(BigDecimal.valueOf(40));
    }

    @Test
    void lowersKFactorAfterThirtyMatches() {
        assertThat(PlayerRatingService.kFactor(31)).isEqualByComparingTo(BigDecimal.valueOf(16));
    }

    @Test
    void givesEqualPlayersEqualExpectedScore() {
        assertThat(PlayerRatingService.expectedScore(
                BigDecimal.valueOf(1000), BigDecimal.valueOf(1000)))
                .isEqualByComparingTo(new BigDecimal("0.50000000"));
    }

    @Test
    void favorsHigherRatedTeamWithoutMakingOutcomeCertain() {
        BigDecimal expected = PlayerRatingService.expectedScore(
                BigDecimal.valueOf(1200), BigDecimal.valueOf(1000));
        assertThat(expected).isGreaterThan(new BigDecimal("0.75"));
        assertThat(expected).isLessThan(BigDecimal.ONE);
    }
}
