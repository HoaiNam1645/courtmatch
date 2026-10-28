package com.courtly.api.match;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.JoinedStatus;
import com.courtly.common.enums.MatchDataSource;
import com.courtly.common.enums.MatchStatus;
import com.courtly.common.enums.MatchType;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchGame;
import com.courtly.domain.match.MatchPlayer;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.match.MatchResult;
import com.courtly.domain.match.PlayerRatingHistory;
import com.courtly.domain.match.PlayerRatingHistoryRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PlayerMatchDataControllerTest {

    private static final String PASSWORD = "Courtly@123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private PlayerRatingHistoryRepository ratingHistoryRepository;
    @Autowired private PlayerStatisticsRepository statisticsRepository;

    private User player;
    private User opponent;
    private User outsider;
    private String playerToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() throws Exception {
        ensurePlayerRole();
        player = persistPlayer("history-player");
        opponent = persistPlayer("history-opponent");
        outsider = persistPlayer("history-outsider");
        playerToken = loginAs(player);
        outsiderToken = loginAs(outsider);
    }

    @Test
    @DisplayName("2.2.9 lich su chi tra tran cua nguoi choi, phan trang va moi nhat truoc")
    void historyIsPrivatePagedAndNewestFirst() throws Exception {
        Match oldest = persistMatch(opponent, player, MatchStatus.COMPLETED,
                Instant.now().minus(3, ChronoUnit.DAYS), true);
        Match newest = persistMatch(opponent, player, MatchStatus.COMPLETED,
                Instant.now().minus(1, ChronoUnit.DAYS), true);
        persistMatch(opponent, player, MatchStatus.COMPLETED,
                Instant.now().minus(2, ChronoUnit.DAYS), true);
        persistMatch(opponent, outsider, MatchStatus.COMPLETED,
                Instant.now(), true);
        persistRating(player, oldest, "1000.00", "990.00", "-10.00",
                Instant.now().minus(3, ChronoUnit.DAYS));
        persistRating(player, newest, "990.00", "1002.50", "12.50",
                Instant.now().minus(1, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/v1/matches")
                        .header("Authorization", bearer(playerToken))
                        .param("page", "0").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(newest.getId().toString()))
                .andExpect(jsonPath("$.content[0].ratingChange").value(12.50))
                .andExpect(jsonPath("$.content[0].games[0].team1Score").value(21))
                .andExpect(jsonPath("$.content[0].team2[0].fullName").value(player.getFullName()));
    }

    @Test
    @DisplayName("2.2.9 bo loc pending chi dem tran dang cho chinh nguoi hien tai xac nhan")
    void pendingOnlyReturnsMatchesNeedingCurrentPlayerConfirmation() throws Exception {
        Match pending = persistMatch(opponent, player, MatchStatus.SCHEDULED,
                Instant.now().minus(1, ChronoUnit.HOURS), false);
        persistMatch(player, opponent, MatchStatus.SCHEDULED,
                Instant.now().minus(2, ChronoUnit.HOURS), true);

        mockMvc.perform(get("/api/v1/matches")
                        .header("Authorization", bearer(playerToken))
                        .param("state", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pending.getId().toString()))
                .andExpect(jsonPath("$.content[0].currentUserNeedsConfirmation").value(true));
    }

    @Test
    @DisplayName("2.2.9 nguoi ngoai nhan 404 khi xem chi tiet tran")
    void outsiderCannotViewMatchDetail() throws Exception {
        Match match = persistMatch(opponent, player, MatchStatus.COMPLETED,
                Instant.now().minus(1, ChronoUnit.DAYS), true);

        mockMvc.perform(get("/api/v1/matches/{id}", match.getId())
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("2.2.9 loc sai va thieu dang nhap bi tu choi dung hop dong API")
    void historyValidatesFilterAndAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/matches").param("state", "unknown")
                        .header("Authorization", bearer(playerToken)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.state").isNotEmpty());

        mockMvc.perform(get("/api/v1/matches"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("2.2.10 performance tra streak, scoring, ba loai tran va lich su 90 ngay")
    void performanceReturnsDashboardReadModelAndNinetyDayHistory() throws Exception {
        PlayerStatistics statistics = new PlayerStatistics(player);
        statistics.setTotalMatches(4);
        statistics.setTotalWins(3);
        statistics.setTotalLosses(1);
        statistics.setWinRate(new BigDecimal("75.00"));
        statistics.setSmoothedWinRate(new BigDecimal("61.11"));
        statistics.setRatingScore(new BigDecimal("1042.50"));
        statistics.setCurrentWinStreak(2);
        statistics.setLongestWinStreak(3);
        statistics.setTotalGamesWon(8);
        statistics.setTotalGamesLost(3);
        statistics.setAvgPointsScored(new BigDecimal("42.25"));
        statistics.setAvgPointsConceded(new BigDecimal("35.50"));
        statistics.setMatchTypeStatistics(new LinkedHashMap<>());
        statistics.getMatchTypeStatistics().put("double",
                new PlayerStatistics.MatchTypeStatistics(4, 3, 1, new BigDecimal("75.00")));
        statisticsRepository.saveAndFlush(statistics);
        persistRating(player, null, "1000.00", "1010.00", "10.00",
                Instant.now().minus(100, ChronoUnit.DAYS));
        persistRating(player, null, "1010.00", "1042.50", "32.50",
                Instant.now().minus(10, ChronoUnit.DAYS));

        mockMvc.perform(get("/api/v1/players/me/performance")
                        .header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.currentWinStreak").value(2))
                .andExpect(jsonPath("$.summary.longestWinStreak").value(3))
                .andExpect(jsonPath("$.scoring.averagePointDifference").value(6.75))
                .andExpect(jsonPath("$.scoring.totalGamesWon").value(8))
                .andExpect(jsonPath("$.matchTypes.single.totalMatches").value(0))
                .andExpect(jsonPath("$.matchTypes.double.totalMatches").value(4))
                .andExpect(jsonPath("$.matchTypes.mixedDouble.totalMatches").value(0));

        mockMvc.perform(get("/api/v1/players/me/rating-history")
                        .header("Authorization", bearer(playerToken))
                        .param("days", "90").param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].changeAmount").value(32.50));

        mockMvc.perform(get("/api/v1/players/me/rating-history")
                        .header("Authorization", bearer(playerToken)).param("days", "0"))
                .andExpect(status().isBadRequest());
    }

    private Match persistMatch(User team1User, User team2User, MatchStatus status,
                               Instant scheduledAt, boolean team2Confirmed) {
        Match match = new Match();
        match.setId(UUID.randomUUID());
        match.setMatchType(MatchType.SINGLE);
        match.setStatus(status);
        match.setDataSource(MatchDataSource.MANUAL);
        match.setScheduledAt(scheduledAt);
        match.setCreatedAt(scheduledAt);
        match.setCreatedBy(team1User);

        MatchPlayer team1 = player(match, team1User, (short) 1, true);
        MatchPlayer team2 = player(match, team2User, (short) 2, team2Confirmed);
        match.getPlayers().add(team1);
        match.getPlayers().add(team2);
        match.getGames().add(game(match, (short) 1, 21, 15));
        match.getGames().add(game(match, (short) 2, 21, 17));

        MatchResult result = new MatchResult(match, (short) 1);
        result.setRecordedBy(team1User);
        if (status == MatchStatus.COMPLETED) {
            result.setConfirmedAt(scheduledAt.plus(1, ChronoUnit.HOURS));
        }
        match.setResult(result);
        return matchRepository.saveAndFlush(match);
    }

    private static MatchPlayer player(Match match, User user, short teamNo, boolean confirmed) {
        MatchPlayer player = new MatchPlayer();
        player.setId(UUID.randomUUID());
        player.setMatch(match);
        player.setUser(user);
        player.setTeamNo(teamNo);
        player.setPositionNo((short) 1);
        player.setJoinedStatus(JoinedStatus.JOINED);
        player.setCreatedAt(match.getCreatedAt());
        if (confirmed) {
            player.setResultConfirmedAt(match.getCreatedAt());
        }
        return player;
    }

    private static MatchGame game(Match match, short gameNo, int team1Score, int team2Score) {
        MatchGame game = new MatchGame();
        game.setId(UUID.randomUUID());
        game.setMatch(match);
        game.setGameNo(gameNo);
        game.setTeam1Score(team1Score);
        game.setTeam2Score(team2Score);
        return game;
    }

    private void persistRating(User user, Match match, String oldRating, String newRating,
                               String change, Instant createdAt) {
        PlayerRatingHistory history = new PlayerRatingHistory();
        history.setId(UUID.randomUUID());
        history.setUser(user);
        history.setMatch(match);
        history.setOldRating(new BigDecimal(oldRating));
        history.setNewRating(new BigDecimal(newRating));
        history.setChangeAmount(new BigDecimal(change));
        history.setReason("test");
        history.setCreatedAt(createdAt);
        ratingHistoryRepository.saveAndFlush(history);
    }

    private void ensurePlayerRole() {
        if (roleRepository.findByCode(Role.PLAYER).isEmpty()) {
            Role role = new Role(Role.PLAYER, "Nguoi choi", null);
            role.setId(UUID.randomUUID());
            roleRepository.saveAndFlush(role);
        }
    }

    private User persistPlayer(String prefix) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Choi " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.getUserRoles().add(new UserRole(
                user, roleRepository.findByCode(Role.PLAYER).orElseThrow()));
        user.setPlayerProfile(new PlayerProfile(user));
        return userRepository.saveAndFlush(user);
    }

    private String loginAs(User user) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {"emailOrPhone":"%s","password":"%s"}
                                """.formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
