package com.courtly.service.match;

import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchPlayer;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.match.PlayerRatingHistory;
import com.courtly.domain.match.PlayerRatingHistoryRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Elo 1000 diem, san 100; tran doi dung diem trung binh cua hai thanh vien (2.2.6). */
@Service
@RequiredArgsConstructor
public class PlayerRatingService {

    static final BigDecimal RATING_FLOOR = BigDecimal.valueOf(100);
    private static final int EXPERIENCED_MATCH_THRESHOLD = 30;
    private static final BigDecimal NEW_PLAYER_K = BigDecimal.valueOf(40);
    private static final BigDecimal EXPERIENCED_PLAYER_K = BigDecimal.valueOf(16);

    private final MatchRepository matchRepository;
    private final PlayerStatisticsRepository statisticsRepository;
    private final PlayerRatingHistoryRepository historyRepository;

    @Transactional
    public void applyConfirmedMatch(UUID matchId) {
        if (historyRepository.existsByMatchId(matchId)) {
            return;
        }
        matchRepository.findConfirmedWithPlayers(matchId)
                .filter(match -> !match.isExcludedFromRating())
                .ifPresent(this::apply);
    }

    /** Doi soat Elo tu dau theo thu tu tran, dong thoi khoi phuc lich su bi lech. */
    @Transactional
    public void reconcileAll() {
        historyRepository.deleteAllInBatch();
        List<PlayerStatistics> statistics = statisticsRepository.findAll();
        statistics.forEach(item -> item.setRatingScore(PlayerStatistics.DEFAULT_RATING));
        statisticsRepository.saveAllAndFlush(statistics);

        matchRepository.findAllConfirmedWithPlayers().stream()
                .filter(match -> !match.isExcludedFromRating())
                .forEach(this::apply);
    }

    private void apply(Match match) {
        Map<UUID, PlayerStatistics> statistics = match.getPlayers().stream()
                .map(player -> statisticsRepository.findById(player.getUser().getId())
                        .orElseGet(() -> new PlayerStatistics(player.getUser())))
                .collect(Collectors.toMap(PlayerStatistics::getUserId, Function.identity()));
        List<MatchPlayer> team1 = playersOf(match, (short) 1);
        List<MatchPlayer> team2 = playersOf(match, (short) 2);
        BigDecimal team1Rating = teamAverage(team1, statistics);
        BigDecimal team2Rating = teamAverage(team2, statistics);
        BigDecimal expectedTeam1 = expectedScore(team1Rating, team2Rating);
        boolean team1Won = match.getResult().getWinningTeam() == 1;

        BigDecimal team1Delta = teamDelta(team1, team1Won ? BigDecimal.ONE : BigDecimal.ZERO,
                expectedTeam1);
        BigDecimal team2Delta = teamDelta(team2, team1Won ? BigDecimal.ZERO : BigDecimal.ONE,
                BigDecimal.ONE.subtract(expectedTeam1));
        applyTeam(match, team1, statistics, team1Delta);
        applyTeam(match, team2, statistics, team2Delta);
        statisticsRepository.saveAll(statistics.values());
    }

    private void applyTeam(Match match, List<MatchPlayer> players,
                           Map<UUID, PlayerStatistics> statistics, BigDecimal delta) {
        for (MatchPlayer player : players) {
            PlayerStatistics item = statistics.get(player.getUser().getId());
            BigDecimal oldRating = item.getRatingScore();
            BigDecimal newRating = oldRating.add(delta).max(RATING_FLOOR)
                    .setScale(2, RoundingMode.HALF_UP);
            BigDecimal actualChange = newRating.subtract(oldRating).setScale(2, RoundingMode.HALF_UP);
            item.setRatingScore(newRating);

            PlayerRatingHistory history = new PlayerRatingHistory();
            history.setUser(player.getUser());
            history.setMatch(match);
            history.setOldRating(oldRating);
            history.setNewRating(newRating);
            history.setChangeAmount(actualChange);
            history.setReason("ELO_CONFIRMED_MATCH");
            history.setCreatedAt(match.getResult().getConfirmedAt());
            historyRepository.save(history);
        }
    }

    private BigDecimal teamDelta(List<MatchPlayer> players, BigDecimal actual, BigDecimal expected) {
        BigDecimal averageK = players.stream()
                .map(player -> kFactor(historyRepository.countByUserId(player.getUser().getId())))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(players.size()), 8, RoundingMode.HALF_UP);
        return averageK.multiply(actual.subtract(expected)).setScale(2, RoundingMode.HALF_UP);
    }

    static BigDecimal expectedScore(BigDecimal ownRating, BigDecimal opponentRating) {
        double exponent = opponentRating.subtract(ownRating).doubleValue() / 400.0;
        return BigDecimal.valueOf(1.0 / (1.0 + Math.pow(10.0, exponent)))
                .setScale(8, RoundingMode.HALF_UP);
    }

    static BigDecimal kFactor(long priorMatches) {
        return priorMatches > EXPERIENCED_MATCH_THRESHOLD ? EXPERIENCED_PLAYER_K : NEW_PLAYER_K;
    }

    private static BigDecimal teamAverage(List<MatchPlayer> players,
                                          Map<UUID, PlayerStatistics> statistics) {
        return players.stream()
                .map(player -> statistics.get(player.getUser().getId()).getRatingScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(players.size()), 8, RoundingMode.HALF_UP);
    }

    private static List<MatchPlayer> playersOf(Match match, short teamNo) {
        return match.getPlayers().stream().filter(player -> player.getTeamNo() == teamNo).toList();
    }
}
