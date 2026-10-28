package com.courtly.service.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.courtly.api.match.dto.PlayerPerformanceResponse;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.match.PlayerRatingHistoryRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.matching.PlayerMatchProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class PlayerPerformanceQueryServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PlayerStatisticsRepository statisticsRepository;
    @Mock private PlayerMatchProfileRepository profileRepository;
    @Mock private PlayerRatingHistoryRepository ratingHistoryRepository;
    @Mock private MatchRepository matchRepository;

    private PlayerPerformanceQueryService service;
    private UUID userId;

    @BeforeEach
    void setUp() {
        service = new PlayerPerformanceQueryService(userRepository, statisticsRepository,
                profileRepository, ratingHistoryRepository, matchRepository);
        userId = UUID.randomUUID();
        when(userRepository.existsById(userId)).thenReturn(true);
        when(userRepository.hasPlayerRole(userId)).thenReturn(true);
    }

    @Test
    void returnsReadOptimizedStreakScoringAndFixedMatchTypes() {
        PlayerStatistics statistics = new PlayerStatistics();
        statistics.setUserId(userId);
        statistics.setRatingScore(new BigDecimal("1120.50"));
        statistics.setTotalMatches(6);
        statistics.setTotalWins(4);
        statistics.setTotalLosses(2);
        statistics.setWinRate(new BigDecimal("66.67"));
        statistics.setSmoothedWinRate(new BigDecimal("59.09"));
        statistics.setCurrentWinStreak(2);
        statistics.setLongestWinStreak(3);
        statistics.setAvgPointsScored(new BigDecimal("41.50"));
        statistics.setAvgPointsConceded(new BigDecimal("37.25"));
        statistics.setTotalGamesWon(10);
        statistics.setTotalGamesLost(5);
        statistics.setMatchTypeStatistics(new LinkedHashMap<>(Map.of(
                "single", new PlayerStatistics.MatchTypeStatistics(
                        2, 1, 1, new BigDecimal("50.00")),
                "double", new PlayerStatistics.MatchTypeStatistics(
                        4, 3, 1, new BigDecimal("75.00")))));
        when(statisticsRepository.findById(userId)).thenReturn(Optional.of(statistics));
        when(profileRepository.findById(userId)).thenReturn(Optional.empty());

        PlayerPerformanceResponse response = service.getCurrentPlayer(userId);

        assertThat(response.summary().currentWinStreak()).isEqualTo(2);
        assertThat(response.summary().longestWinStreak()).isEqualTo(3);
        assertThat(response.scoring().averagePointDifference()).isEqualByComparingTo("4.25");
        assertThat(response.scoring().totalGamesWon()).isEqualTo(10);
        assertThat(response.matchTypes().single().totalMatches()).isEqualTo(2);
        assertThat(response.matchTypes().doubles().winRate()).isEqualByComparingTo("75.00");
        assertThat(response.matchTypes().mixedDouble().totalMatches()).isZero();
    }

    @Test
    void limitsRatingHistoryToRequestedNumberOfDays() {
        when(ratingHistoryRepository
                .findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(userId), any(Instant.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(java.util.List.of()));
        Instant before = Instant.now().minus(90, ChronoUnit.DAYS);

        service.getRatingHistory(userId, 90, 0, 100);

        ArgumentCaptor<Instant> cutoff = ArgumentCaptor.forClass(Instant.class);
        verify(ratingHistoryRepository)
                .findByUserIdAndCreatedAtGreaterThanEqualOrderByCreatedAtDesc(
                        eq(userId), cutoff.capture(), any(Pageable.class));
        assertThat(cutoff.getValue()).isBetween(before, before.plusSeconds(2));
    }
}
