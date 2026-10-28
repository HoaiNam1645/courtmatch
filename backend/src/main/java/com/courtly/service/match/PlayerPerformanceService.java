package com.courtly.service.match;

import com.courtly.domain.match.MatchRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dieu phoi pipeline 2.2.4-2.2.7 sau khi ket qua tran duoc xac nhan. */
@Service
@RequiredArgsConstructor
public class PlayerPerformanceService {

    private final MatchRepository matchRepository;
    private final PlayerDataQualityService dataQualityService;
    private final PlayerStatisticsService statisticsService;
    private final PlayerRatingService ratingService;
    private final PlayerFeatureService featureService;

    @Transactional
    public void processConfirmedMatch(UUID matchId) {
        dataQualityService.evaluateConfirmedMatch(matchId);
        List<UUID> playerIds = matchRepository.findParticipantUserIds(matchId);
        statisticsService.recomputeUsers(playerIds);
        ratingService.applyConfirmedMatch(matchId);
        featureService.recomputeUsers(playerIds);
    }

    @Transactional
    public void reconcileAll() {
        dataQualityService.reconcileAll();
        statisticsService.reconcileAll();
        ratingService.reconcileAll();
        featureService.reconcileAll();
    }
}
