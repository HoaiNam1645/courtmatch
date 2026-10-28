package com.courtly.service.match;

import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
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

    private final PlayerStatisticsRepository statisticsRepository;
    private final UserRepository userRepository;

    @Transactional
    public void recomputeUsers(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        Set<UUID> uniqueIds = new HashSet<>(userIds);
        Map<UUID, PlayerStatisticsRepository.StatisticsAggregate> aggregates = new HashMap<>();
        statisticsRepository.aggregateForUsers(uniqueIds)
                .forEach(item -> aggregates.put(item.getUserId(), item));
        Map<UUID, User> users = new HashMap<>();
        userRepository.findAllById(uniqueIds).forEach(user -> users.put(user.getId(), user));

        for (UUID userId : uniqueIds) {
            User user = users.get(userId);
            if (user == null) {
                continue;
            }
            PlayerStatistics statistics = statisticsRepository.findById(userId)
                    .orElseGet(() -> new PlayerStatistics(user));
            apply(statistics, aggregates.get(userId));
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
                              PlayerStatisticsRepository.StatisticsAggregate aggregate) {
        if (aggregate == null) {
            statistics.setTotalMatches(0);
            statistics.setTotalWins(0);
            statistics.setTotalLosses(0);
            statistics.setWinRate(BigDecimal.ZERO.setScale(2));
            statistics.setAvgPointsScored(BigDecimal.ZERO.setScale(2));
            statistics.setAvgPointsConceded(BigDecimal.ZERO.setScale(2));
            statistics.setLastPlayedAt(null);
            return;
        }
        statistics.setTotalMatches(Math.toIntExact(aggregate.getTotalMatches()));
        statistics.setTotalWins(Math.toIntExact(aggregate.getTotalWins()));
        statistics.setTotalLosses(Math.toIntExact(aggregate.getTotalLosses()));
        statistics.setWinRate(BigDecimal.valueOf(aggregate.getTotalWins())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(aggregate.getTotalMatches()), 2, RoundingMode.HALF_UP));
        statistics.setAvgPointsScored(scale(aggregate.getAvgPointsScored()));
        statistics.setAvgPointsConceded(scale(aggregate.getAvgPointsConceded()));
        statistics.setLastPlayedAt(aggregate.getLastPlayedAt());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value.setScale(2, RoundingMode.HALF_UP);
    }

    private record Snapshot(int matches, int wins, int losses, BigDecimal winRate,
                            BigDecimal scored, BigDecimal conceded, java.time.Instant lastPlayedAt) {
        static Snapshot of(PlayerStatistics item) {
            return new Snapshot(item.getTotalMatches(), item.getTotalWins(), item.getTotalLosses(),
                    item.getWinRate(), item.getAvgPointsScored(), item.getAvgPointsConceded(),
                    item.getLastPlayedAt());
        }
    }
}
