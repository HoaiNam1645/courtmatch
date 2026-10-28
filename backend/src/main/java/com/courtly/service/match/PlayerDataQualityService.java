package com.courtly.service.match;

import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchPlayer;
import com.courtly.domain.match.MatchRepository;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Lam sach tran hop le va loai cac cap doi thu co dau hieu cay diem (2.2.4). */
@Service
@RequiredArgsConstructor
public class PlayerDataQualityService {

    public static final String PAIR_LIMIT_REASON = "PAIR_LIMIT_20_IN_7_DAYS";
    private static final int MAX_RATED_MEETINGS = 20;

    private final MatchRepository matchRepository;

    @Transactional
    public void evaluateConfirmedMatch(java.util.UUID matchId) {
        matchRepository.findConfirmedWithPlayers(matchId).ifPresent(match -> {
            evaluate(match);
            matchRepository.save(match);
        });
    }

    /** Tinh lai theo thu tu thoi gian de lan chay sau cho cung mot ket qua. */
    @Transactional
    public void reconcileAll() {
        List<Match> matches = matchRepository.findAllConfirmedWithPlayers();
        matches.stream()
                .filter(match -> PAIR_LIMIT_REASON.equals(match.getExclusionReason()))
                .forEach(match -> {
                    match.setExcludedFromRating(false);
                    match.setExclusionReason(null);
                });
        matchRepository.saveAllAndFlush(matches);

        for (Match match : matches) {
            if (!match.isExcludedFromRating()) {
                evaluate(match);
                matchRepository.saveAndFlush(match);
            }
        }
    }

    private void evaluate(Match match) {
        if (match.getScheduledAt() == null || match.getCreatedAt() == null) {
            return;
        }
        List<MatchPlayer> team1 = playersOf(match, (short) 1);
        List<MatchPlayer> team2 = playersOf(match, (short) 2);
        boolean exceeded = team1.stream().anyMatch(first -> team2.stream().anyMatch(second ->
                matchRepository.countPriorRatedMeetings(
                        first.getUser().getId(), second.getUser().getId(),
                        match.getScheduledAt().minus(7, ChronoUnit.DAYS),
                        match.getScheduledAt(), match.getCreatedAt()) >= MAX_RATED_MEETINGS));
        if (exceeded) {
            match.setExcludedFromRating(true);
            match.setExclusionReason(PAIR_LIMIT_REASON);
        } else if (PAIR_LIMIT_REASON.equals(match.getExclusionReason())) {
            match.setExcludedFromRating(false);
            match.setExclusionReason(null);
        }
    }

    private static List<MatchPlayer> playersOf(Match match, short teamNo) {
        return match.getPlayers().stream()
                .filter(player -> player.getTeamNo() == teamNo)
                .toList();
    }
}
