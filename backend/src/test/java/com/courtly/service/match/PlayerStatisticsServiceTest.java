package com.courtly.service.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class PlayerStatisticsServiceTest {

    @Mock
    private PlayerStatisticsRepository statisticsRepository;

    @Mock
    private UserRepository userRepository;

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

    @Test
    void aggregatesStreaksGamesScoringAndMatchTypes() {
        UUID userId = UUID.randomUUID();
        User user = new User();
        user.setId(userId);
        when(userRepository.findAllById(any())).thenReturn(List.of(user));
        when(statisticsRepository.findById(userId)).thenReturn(Optional.empty());
        List<PlayerStatisticsRepository.PerformanceMatchRow> rows = List.of(
                row(userId, "single", false, 35, 42, 1, 2, "2026-08-01T10:00:00Z"),
                row(userId, "double", true, 42, 33, 2, 0, "2026-08-08T10:00:00Z"),
                row(userId, "double", true, 45, 38, 2, 1, "2026-08-15T10:00:00Z"),
                row(userId, "mixed_double", false, 39, 44, 1, 2, "2026-08-22T10:00:00Z"),
                row(userId, "mixed_double", true, 42, 28, 2, 0, "2026-08-29T10:00:00Z"));
        when(statisticsRepository.findPerformanceRows(any())).thenReturn(rows);

        new PlayerStatisticsService(statisticsRepository, userRepository)
                .recomputeUsers(List.of(userId));

        ArgumentCaptor<PlayerStatistics> captor = ArgumentCaptor.forClass(PlayerStatistics.class);
        verify(statisticsRepository).save(captor.capture());
        PlayerStatistics result = captor.getValue();
        assertThat(result.getTotalMatches()).isEqualTo(5);
        assertThat(result.getTotalWins()).isEqualTo(3);
        assertThat(result.getTotalLosses()).isEqualTo(2);
        assertThat(result.getCurrentWinStreak()).isEqualTo(1);
        assertThat(result.getLongestWinStreak()).isEqualTo(2);
        assertThat(result.getTotalGamesWon()).isEqualTo(8);
        assertThat(result.getTotalGamesLost()).isEqualTo(5);
        assertThat(result.getAvgPointsScored()).isEqualByComparingTo("40.60");
        assertThat(result.getAvgPointsConceded()).isEqualByComparingTo("37.00");
        assertThat(result.getLastPlayedAt()).isEqualTo(Instant.parse("2026-08-29T10:00:00Z"));
        assertThat(result.getMatchTypeStatistics().get("double").totalMatches()).isEqualTo(2);
        assertThat(result.getMatchTypeStatistics().get("double").winRate())
                .isEqualByComparingTo("100.00");
        assertThat(result.getMatchTypeStatistics().get("mixed_double").winRate())
                .isEqualByComparingTo("50.00");
    }

    private static PlayerStatisticsRepository.PerformanceMatchRow row(
            UUID userId, String matchType, boolean won, long scored, long conceded,
            long gamesWon, long gamesLost, String scheduledAt) {
        PlayerStatisticsRepository.PerformanceMatchRow row =
                mock(PlayerStatisticsRepository.PerformanceMatchRow.class);
        when(row.getUserId()).thenReturn(userId);
        when(row.getMatchType()).thenReturn(matchType);
        when(row.getWon()).thenReturn(won);
        when(row.getScored()).thenReturn(scored);
        when(row.getConceded()).thenReturn(conceded);
        when(row.getGamesWon()).thenReturn(gamesWon);
        when(row.getGamesLost()).thenReturn(gamesLost);
        when(row.getScheduledAt()).thenReturn(Instant.parse(scheduledAt));
        return row;
    }
}
