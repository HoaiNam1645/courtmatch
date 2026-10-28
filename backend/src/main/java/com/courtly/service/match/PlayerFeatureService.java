package com.courtly.service.match;

import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.matching.PlayerMatchProfile;
import com.courtly.domain.matching.PlayerMatchProfileRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Trich xuat vector ky nang, khong tron toa do va khung gio vao dac trung hieu suat (2.2.5). */
@Service
@RequiredArgsConstructor
public class PlayerFeatureService {

    private static final int MIN_TRAINING_MATCHES = 3;

    private final PlayerStatisticsRepository statisticsRepository;
    private final PlayerMatchProfileRepository profileRepository;
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
            PlayerStatistics statistics = statisticsRepository.findById(userId).orElse(null);
            PlayerMatchProfile profile = profileRepository.findById(userId)
                    .orElseGet(() -> new PlayerMatchProfile(user));
            apply(profile, statistics, aggregates.get(userId));
            profileRepository.save(profile);
        }
    }

    @Transactional
    public void reconcileAll() {
        Set<UUID> userIds = new HashSet<>(statisticsRepository.findEligiblePlayerIds());
        profileRepository.findAll().forEach(profile -> userIds.add(profile.getUserId()));
        recomputeUsers(userIds);
    }

    private static void apply(PlayerMatchProfile profile, PlayerStatistics statistics,
                              PlayerStatisticsRepository.StatisticsAggregate aggregate) {
        if (statistics == null) {
            profile.setSkillScore(PlayerStatistics.DEFAULT_RATING);
            profile.setWinRate(BigDecimal.valueOf(50).setScale(2));
            profile.setTotalMatches(0);
            profile.setAveragePointDifference(null);
            profile.setPerformanceConsistency(null);
            profile.setDataRecencyDays(null);
            profile.setTrainingEligible(false);
            return;
        }
        profile.setSkillScore(statistics.getRatingScore());
        profile.setWinRate(statistics.getSmoothedWinRate());
        profile.setTotalMatches(statistics.getTotalMatches());
        profile.setAveragePointDifference(aggregate == null ? null : scale(aggregate.getAvgPointDifference()));
        profile.setPerformanceConsistency(aggregate == null ? null : scale(aggregate.getPerformanceConsistency()));
        profile.setDataRecencyDays(statistics.getLastPlayedAt() == null ? null
                : Math.toIntExact(Math.max(0, ChronoUnit.DAYS.between(
                        statistics.getLastPlayedAt(), Instant.now()))));
        profile.setTrainingEligible(statistics.getTotalMatches() >= MIN_TRAINING_MATCHES);
    }

    private static BigDecimal scale(BigDecimal value) {
        return value == null ? null : value.setScale(2, RoundingMode.HALF_UP);
    }
}
