package com.courtly.service.match;

import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Du lieu tong hop theo nguoi choi cua 2.2.3. Khong tinh Elo trong service nay. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlayerStatisticsService {

    private static final BigDecimal PRIOR_WINS = BigDecimal.valueOf(2.5);
    private static final BigDecimal PRIOR_MATCHES = BigDecimal.valueOf(5);

    private final PlayerStatisticsRepository statisticsRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recomputeUsers(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Set<UUID> uniqueIds = new HashSet<>(userIds);
        Map<UUID, List<PlayerStatisticsRepository.PerformanceMatchRow>> matches = new HashMap<>();
        statisticsRepository.findPerformanceRows(uniqueIds).forEach(row ->
                matches.computeIfAbsent(row.getUserId(), ignored -> new ArrayList<>()).add(row));
        Map<UUID, User> users = new HashMap<>();
        userRepository.findAllById(uniqueIds).forEach(user -> users.put(user.getId(), user));

        for (UUID userId : uniqueIds) {
            User user = users.get(userId);
            if (user == null) {
                continue;
            }
            PlayerStatistics statistics = statisticsRepository.findById(userId)
                    .orElseGet(() -> new PlayerStatistics(user));
            apply(statistics, matches.getOrDefault(userId, List.of()));
            statisticsRepository.save(statistics);
        }
    }

    /** Job dem tinh lai toan bo va sua moi sai lech so voi du lieu tran goc. */
    @Transactional
    public void reconcileAll() {
        Set<UUID> userIds = new HashSet<>(statisticsRepository.findEligiblePlayerIds());
        statisticsRepository.findAll().forEach(item -> userIds.add(item.getUserId()));

        Map<UUID, Snapshot> before = new HashMap<>();
        statisticsRepository.findAll().forEach(item -> before.put(item.getUserId(), Snapshot.of(item)));
        recomputeUsers(userIds);

        List<UUID> repaired = statisticsRepository.findAll().stream()
                .filter(item -> !Snapshot.of(item).equals(before.get(item.getUserId())))
                .map(PlayerStatistics::getUserId)
                .toList();
        if (!repaired.isEmpty()) {
            log.warn("Doi soat player_statistics da sua sai lech cho {} nguoi choi: {}",
                    repaired.size(), repaired);
        }
    }

    private static void apply(PlayerStatistics statistics,
                              List<PlayerStatisticsRepository.PerformanceMatchRow> matches) {
        if (matches.isEmpty()) {
            statistics.setTotalMatches(0);
            statistics.setTotalWins(0);
            statistics.setTotalLosses(0);
            statistics.setWinRate(BigDecimal.ZERO.setScale(2));
            statistics.setSmoothedWinRate(BigDecimal.valueOf(50).setScale(2));
            statistics.setAvgPointsScored(null);
            statistics.setAvgPointsConceded(null);
            statistics.setCurrentWinStreak(0);
            statistics.setLongestWinStreak(0);
            statistics.setTotalGamesWon(0);
            statistics.setTotalGamesLost(0);
            statistics.setMatchTypeStatistics(new LinkedHashMap<>());
            statistics.setLastPlayedAt(null);
            return;
        }

        int totalWins = 0;
        int currentStreak = 0;
        int longestStreak = 0;
        long totalScored = 0;
        long totalConceded = 0;
        long totalGamesWon = 0;
        long totalGamesLost = 0;
        java.time.Instant lastPlayedAt = null;
        Map<String, MatchTypeAccumulator> byType = new LinkedHashMap<>();
        for (PlayerStatisticsRepository.PerformanceMatchRow match : matches) {
            if (match.getWon()) {
                totalWins++;
                currentStreak++;
                longestStreak = Math.max(longestStreak, currentStreak);
            } else {
                currentStreak = 0;
            }
            totalScored += match.getScored();
            totalConceded += match.getConceded();
            totalGamesWon += match.getGamesWon();
            totalGamesLost += match.getGamesLost();
            if (match.getScheduledAt() != null
                    && (lastPlayedAt == null || match.getScheduledAt().isAfter(lastPlayedAt))) {
                lastPlayedAt = match.getScheduledAt();
            }
            byType.computeIfAbsent(match.getMatchType(), ignored -> new MatchTypeAccumulator())
                    .add(match.getWon());
        }

        int totalMatches = matches.size();
        statistics.setTotalMatches(totalMatches);
        statistics.setTotalWins(totalWins);
        statistics.setTotalLosses(totalMatches - totalWins);
        statistics.setWinRate(BigDecimal.valueOf(totalWins)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalMatches), 2, RoundingMode.HALF_UP));
        statistics.setSmoothedWinRate(smoothedWinRate(totalWins, totalMatches));
        statistics.setAvgPointsScored(average(totalScored, totalMatches));
        statistics.setAvgPointsConceded(average(totalConceded, totalMatches));
        statistics.setCurrentWinStreak(currentStreak);
        statistics.setLongestWinStreak(longestStreak);
        statistics.setTotalGamesWon(Math.toIntExact(totalGamesWon));
        statistics.setTotalGamesLost(Math.toIntExact(totalGamesLost));
        Map<String, PlayerStatistics.MatchTypeStatistics> matchTypes = new LinkedHashMap<>();
        byType.forEach((type, value) -> matchTypes.put(type, value.toStatistics()));
        statistics.setMatchTypeStatistics(matchTypes);
        statistics.setLastPlayedAt(lastPlayedAt);
    }

    private static BigDecimal average(long total, int count) {
        return BigDecimal.valueOf(total).divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }

    static BigDecimal smoothedWinRate(long wins, long matches) {
        return BigDecimal.valueOf(wins).add(PRIOR_WINS)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(matches).add(PRIOR_MATCHES), 2, RoundingMode.HALF_UP);
    }

    private record Snapshot(int matches, int wins, int losses, BigDecimal winRate,
                            BigDecimal smoothedWinRate,
                            BigDecimal scored, BigDecimal conceded, int currentStreak,
                            int longestStreak, int gamesWon, int gamesLost,
                            Map<String, PlayerStatistics.MatchTypeStatistics> matchTypes,
                            java.time.Instant lastPlayedAt) {
        static Snapshot of(PlayerStatistics item) {
            return new Snapshot(item.getTotalMatches(), item.getTotalWins(), item.getTotalLosses(),
                    item.getWinRate(), item.getSmoothedWinRate(),
                    item.getAvgPointsScored(), item.getAvgPointsConceded(),
                    item.getCurrentWinStreak(), item.getLongestWinStreak(),
                    item.getTotalGamesWon(), item.getTotalGamesLost(),
                    item.getMatchTypeStatistics(),
                    item.getLastPlayedAt());
        }
    }

    private static final class MatchTypeAccumulator {
        private int matches;
        private int wins;

        void add(boolean won) {
            matches++;
            if (won) {
                wins++;
            }
        }

        PlayerStatistics.MatchTypeStatistics toStatistics() {
            BigDecimal winRate = BigDecimal.valueOf(wins).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(matches), 2, RoundingMode.HALF_UP);
            return new PlayerStatistics.MatchTypeStatistics(matches, wins, matches - wins, winRate);
        }
    }
}
