package com.courtly.seed;

import com.courtly.common.enums.JoinedStatus;
import com.courtly.common.enums.MatchStatus;
import com.courtly.common.enums.MatchType;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchGame;
import com.courtly.domain.match.MatchPlayer;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.match.MatchResult;
import com.courtly.domain.match.PlayerRatingHistory;
import com.courtly.domain.match.PlayerRatingHistoryRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.venue.VenueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buoc 6: matches, match_players, match_games, match_results,
 * player_statistics va player_rating_history.
 *
 * <p>Doi thang, chi so thong ke va tung buoc thay doi rating deu la hang so viet san
 * va da doi chieu khop voi ty so trong {@link #MATCHES}. Seeder khong chay thuat toan nao.
 */
@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class MatchSeeder implements Seeder {

    private final MatchRepository matchRepository;
    private final UserRepository userRepository;
    private final VenueRepository venueRepository;
    private final PlayerStatisticsRepository statisticsRepository;
    private final PlayerRatingHistoryRepository ratingHistoryRepository;

    /** @param games ty so tung set duoi dang {doi 1, doi 2} */
    private record MatchSeed(String key, String venueKey, MatchType type, int daysAgo,
                             List<String> team1, List<String> team2,
                             List<int[]> games, short winningTeam, String resultNote) {
    }

    private static final List<MatchSeed> MATCHES = List.of(
            new MatchSeed("match-01", "venue-01", MatchType.DOUBLE, 20,
                    List.of("player-01", "player-05"), List.of("player-03", "player-07"),
                    List.of(new int[]{21, 18}, new int[]{19, 21}, new int[]{21, 17}),
                    (short) 1, "Doi 1 thang 2-1"),
            new MatchSeed("match-02", "venue-03", MatchType.SINGLE, 15,
                    List.of("player-03"), List.of("player-01"),
                    List.of(new int[]{21, 15}, new int[]{21, 19}),
                    (short) 1, "Doi 1 thang 2-0"),
            new MatchSeed("match-03", "venue-02", MatchType.MIXED_DOUBLE, 12,
                    List.of("player-01", "player-02"), List.of("player-05", "player-06"),
                    List.of(new int[]{18, 21}, new int[]{21, 23}),
                    (short) 2, "Doi 2 thang 2-0"),
            new MatchSeed("match-04", "venue-04", MatchType.DOUBLE, 8,
                    List.of("player-04", "player-08"), List.of("player-02", "player-07"),
                    List.of(new int[]{15, 21}, new int[]{17, 21}),
                    (short) 2, "Doi 2 thang 2-0"),
            new MatchSeed("match-05", "venue-05", MatchType.SINGLE, 5,
                    List.of("player-06"), List.of("player-04"),
                    List.of(new int[]{21, 12}, new int[]{21, 14}),
                    (short) 1, "Doi 1 thang 2-0"),
            new MatchSeed("match-06", "venue-06", MatchType.DOUBLE, 3,
                    List.of("player-03", "player-06"), List.of("player-01", "player-08"),
                    List.of(new int[]{21, 19}, new int[]{21, 16}),
                    (short) 1, "Doi 1 thang 2-0"));

    /** Thong ke tong hop sau {@link #MATCHES}, ghi thang vao player_statistics. */
    private record StatsSeed(String playerKey, int matches, int wins, int losses,
                             String winRate, String rating, String avgScored, String avgConceded,
                             int lastPlayedDaysAgo) {
    }

    private static final List<StatsSeed> STATISTICS = List.of(
            new StatsSeed("player-01", 4, 1, 3, "25.00", "1372.00", "42.25", "46.00", 3),
            new StatsSeed("player-02", 2, 1, 1, "50.00", "1180.00", "40.50", "38.00", 8),
            new StatsSeed("player-03", 3, 2, 1, "66.67", "1704.00", "46.67", "43.33", 3),
            new StatsSeed("player-04", 2, 0, 2, "0.00", "832.00", "29.00", "42.00", 5),
            new StatsSeed("player-05", 2, 2, 0, "100.00", "1258.00", "52.50", "47.50", 12),
            new StatsSeed("player-06", 3, 3, 0, "100.00", "1467.00", "42.67", "33.33", 3),
            new StatsSeed("player-07", 2, 1, 1, "50.00", "1150.00", "49.00", "46.50", 8),
            new StatsSeed("player-08", 2, 0, 2, "0.00", "872.00", "33.50", "42.00", 3));

    /** Tung buoc thay doi rating, xep theo thu tu thoi gian cua tran. */
    private record RatingSeed(String playerKey, String matchKey,
                              String oldRating, String newRating, String change, String reason) {
    }

    private static final List<RatingSeed> RATING_HISTORY = List.of(
            new RatingSeed("player-01", "match-01", "1420.00", "1444.00", "24.00", "match_win"),
            new RatingSeed("player-01", "match-02", "1444.00", "1420.00", "-24.00", "match_loss"),
            new RatingSeed("player-01", "match-03", "1420.00", "1396.00", "-24.00", "match_loss"),
            new RatingSeed("player-01", "match-06", "1396.00", "1372.00", "-24.00", "match_loss"),
            new RatingSeed("player-02", "match-03", "1180.00", "1156.00", "-24.00", "match_loss"),
            new RatingSeed("player-02", "match-04", "1156.00", "1180.00", "24.00", "match_win"),
            new RatingSeed("player-03", "match-01", "1680.00", "1656.00", "-24.00", "match_loss"),
            new RatingSeed("player-03", "match-02", "1656.00", "1680.00", "24.00", "match_win"),
            new RatingSeed("player-03", "match-06", "1680.00", "1704.00", "24.00", "match_win"),
            new RatingSeed("player-04", "match-04", "880.00", "856.00", "-24.00", "match_loss"),
            new RatingSeed("player-04", "match-05", "856.00", "832.00", "-24.00", "match_loss"),
            new RatingSeed("player-05", "match-01", "1210.00", "1234.00", "24.00", "match_win"),
            new RatingSeed("player-05", "match-03", "1234.00", "1258.00", "24.00", "match_win"),
            new RatingSeed("player-06", "match-03", "1395.00", "1419.00", "24.00", "match_win"),
            new RatingSeed("player-06", "match-05", "1419.00", "1443.00", "24.00", "match_win"),
            new RatingSeed("player-06", "match-06", "1443.00", "1467.00", "24.00", "match_win"),
            new RatingSeed("player-07", "match-01", "1150.00", "1126.00", "-24.00", "match_loss"),
            new RatingSeed("player-07", "match-04", "1126.00", "1150.00", "24.00", "match_win"),
            new RatingSeed("player-08", "match-04", "920.00", "896.00", "-24.00", "match_loss"),
            new RatingSeed("player-08", "match-06", "896.00", "872.00", "-24.00", "match_loss"));

    @Override
    public String name() {
        return "matches + player statistics";
    }

    @Override
    @Transactional
    public void seed() {
        if (matchRepository.count() > 0) {
            log.info("  [matches] da co du lieu, bo qua");
            return;
        }

        MATCHES.forEach(this::seedMatch);
        STATISTICS.forEach(this::seedStatistics);
        RATING_HISTORY.forEach(this::seedRatingHistory);

        log.info("  [matches] {} tran, {} ban ghi thong ke, {} dong lich su rating",
                MATCHES.size(), STATISTICS.size(), RATING_HISTORY.size());
    }

    private void seedMatch(MatchSeed seed) {
        Instant scheduledAt = Instant.now().minus(seed.daysAgo(), ChronoUnit.DAYS);

        Match match = new Match();
        match.setId(SeedIds.of("match:" + seed.key()));
        match.setVenue(venueRepository.findById(SeedIds.of("venue:" + seed.venueKey())).orElseThrow());
        match.setMatchType(seed.type());
        match.setStatus(MatchStatus.COMPLETED);
        match.setScheduledAt(scheduledAt);
        match.setCreatedAt(scheduledAt.minus(2, ChronoUnit.DAYS));
        match.setCreatedBy(player(seed.team1().getFirst()));

        addTeam(match, seed, seed.team1(), (short) 1);
        addTeam(match, seed, seed.team2(), (short) 2);

        short gameNo = 1;
        for (int[] score : seed.games()) {
            MatchGame game = new MatchGame();
            game.setId(SeedIds.of("match-game:" + seed.key() + ":" + gameNo));
            game.setMatch(match);
            game.setGameNo(gameNo);
            game.setTeam1Score(score[0]);
            game.setTeam2Score(score[1]);
            match.getGames().add(game);
            gameNo++;
        }

        MatchResult result = new MatchResult(match, seed.winningTeam());
        result.setRecordedBy(match.getCreatedBy());
        result.setConfirmedAt(scheduledAt.plus(2, ChronoUnit.HOURS));
        result.setNote(seed.resultNote());
        match.setResult(result);

        matchRepository.save(match);
    }

    private void addTeam(Match match, MatchSeed seed, List<String> playerKeys, short teamNo) {
        short position = 1;
        for (String key : playerKeys) {
            MatchPlayer participant = new MatchPlayer();
            participant.setId(SeedIds.of("match-player:" + seed.key() + ":" + key));
            participant.setMatch(match);
            participant.setUser(player(key));
            participant.setTeamNo(teamNo);
            participant.setPositionNo(position++);
            participant.setJoinedStatus(JoinedStatus.JOINED);
            participant.setCreatedAt(match.getCreatedAt());
            match.getPlayers().add(participant);
        }
    }

    private void seedStatistics(StatsSeed seed) {
        User user = player(seed.playerKey());
        PlayerStatistics stats = statisticsRepository.findById(user.getId())
                .orElseGet(() -> new PlayerStatistics(user));
        stats.setTotalMatches(seed.matches());
        stats.setTotalWins(seed.wins());
        stats.setTotalLosses(seed.losses());
        stats.setWinRate(new BigDecimal(seed.winRate()));
        stats.setRatingScore(new BigDecimal(seed.rating()));
        stats.setAvgPointsScored(new BigDecimal(seed.avgScored()));
        stats.setAvgPointsConceded(new BigDecimal(seed.avgConceded()));
        stats.setLastPlayedAt(Instant.now().minus(seed.lastPlayedDaysAgo(), ChronoUnit.DAYS));
        statisticsRepository.save(stats);
    }

    private void seedRatingHistory(RatingSeed seed) {
        Match match = matchRepository.findById(SeedIds.of("match:" + seed.matchKey())).orElseThrow();

        PlayerRatingHistory history = new PlayerRatingHistory();
        history.setId(SeedIds.of("rating-history:" + seed.matchKey() + ":" + seed.playerKey()));
        history.setUser(player(seed.playerKey()));
        history.setMatch(match);
        history.setOldRating(new BigDecimal(seed.oldRating()));
        history.setNewRating(new BigDecimal(seed.newRating()));
        history.setChangeAmount(new BigDecimal(seed.change()));
        history.setReason(seed.reason());
        history.setCreatedAt(match.getScheduledAt().plus(2, ChronoUnit.HOURS));
        ratingHistoryRepository.save(history);
    }

    private User player(String key) {
        return userRepository.findById(SeedIds.of("user:" + key))
                .orElseThrow(() -> new IllegalStateException("Thieu nguoi choi '" + key + "'"));
    }
}
