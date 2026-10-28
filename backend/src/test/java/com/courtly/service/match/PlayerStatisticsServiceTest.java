package com.courtly.service.match;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class PlayerStatisticsServiceTest {

    @Test
    void smoothsPerfectSmallSampleTowardPrior() {
        assertThat(PlayerStatisticsService.smoothedWinRate(2, 2))
                .isEqualByComparingTo(new BigDecimal("64.29"));
    }

    @Test
    void barelyChangesLargeSample() {
        assertThat(PlayerStatisticsService.smoothedWinRate(40, 50))
                .isEqualByComparingTo(new BigDecimal("77.27"));
    }

    @Test
    void usesNeutralPriorWithoutMatches() {
        assertThat(PlayerStatisticsService.smoothedWinRate(0, 0))
                .isEqualByComparingTo(new BigDecimal("50.00"));
    }
}
