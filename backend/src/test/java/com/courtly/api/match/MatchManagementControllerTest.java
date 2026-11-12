package com.courtly.api.match;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.match.PlayerRatingHistoryRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class MatchManagementControllerTest {

    private static final String PASSWORD = "Courtly@123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private PlayerRatingHistoryRepository ratingHistoryRepository;
    @Autowired private PlayerStatisticsRepository statisticsRepository;

    private User creator;
    private User opponent;
    private User thirdPlayer;
    private User outsider;
    private String creatorToken;
    private String opponentToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() throws Exception {
        ensurePlayerRole();
        creator = persistPlayer("match-creator");
        opponent = persistPlayer("match-opponent");
        thirdPlayer = persistPlayer("match-third");
        outsider = persistPlayer("match-outsider");
        creatorToken = loginAs(creator);
        opponentToken = loginAs(opponent);
        outsiderToken = loginAs(outsider);
    }

    @Test
    @DisplayName("2.2.37 tao tran don thu cong chua co ket qua")
    void createsManualMatchWithoutResult() throws Exception {
        String response = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleMatchPayload(creator.getId(), opponent.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matchType").value("single"))
                .andExpect(jsonPath("$.status").value("scheduled"))
                .andExpect(jsonPath("$.dataSource").value("manual"))
                .andExpect(jsonPath("$.games").isEmpty())
                .andExpect(jsonPath("$.winningTeam").doesNotExist())
                .andExpect(jsonPath("$.team1[0].confirmed").value(false))
                .andExpect(jsonPath("$.currentUserCanRecordResult").value(true))
                .andExpect(jsonPath("$.currentUserCanConfirm").value(false))
                .andReturn().getResponse().getContentAsString();

        UUID matchId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        Match saved = matchRepository.findById(matchId).orElseThrow();
        assertThat(saved.getPlayers()).hasSize(2);
        assertThat(saved.getGames()).isEmpty();
        assertThat(saved.getResult()).isNull();
    }

    @Test
    @DisplayName("2.2.37 kiem tra dang nhap, doi hinh va thoi gian")
    void validatesCreateMatchInput() throws Exception {
        mockMvc.perform(post("/api/v1/matches")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleMatchPayload(creator.getId(), opponent.getId())))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleMatchPayload(opponent.getId(), thirdPlayer.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleMatchPayload(creator.getId(), creator.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATCH_PLAYERS_INVALID"));

        mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleMatchPayload(
                                creator.getId(), opponent.getId(),
                                Instant.now().plus(1, ChronoUnit.DAYS))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.scheduledAt").isNotEmpty());
    }

    @Test
    @DisplayName("2.2.37 chi cho phep tai khoan player hoat dong tham gia")
    void rejectsInactiveOrNonPlayerParticipants() throws Exception {
        User inactive = persistPlayer("match-inactive");
        inactive.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(inactive);

        mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleMatchPayload(creator.getId(), inactive.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATCH_PLAYERS_INVALID"));
    }

    @Test
    @DisplayName("2.2.38 nguoi tao ghi ket qua hop le va tu dong xac nhan phan cua minh")
    void recordsValidMatchResult() throws Exception {
        UUID matchId = createSingleMatch();

        mockMvc.perform(put("/api/v1/matches/{matchId}/result", matchId)
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validResultPayload()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games.length()").value(3))
                .andExpect(jsonPath("$.winningTeam").value(1))
                .andExpect(jsonPath("$.team1[0].confirmed").value(true))
                .andExpect(jsonPath("$.team2[0].confirmed").value(false))
                .andExpect(jsonPath("$.currentUserCanConfirm").value(false))
                .andExpect(jsonPath("$.currentUserCanDispute").value(true));

        Match saved = matchRepository.findById(matchId).orElseThrow();
        assertThat(saved.getGames()).hasSize(3);
        assertThat(saved.getResult()).isNotNull();
        assertThat(saved.getResult().getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("2.2.38 chan nguoi khong phai nguoi tao va ty so sai luat")
    void validatesResultRecorderAndBadmintonScore() throws Exception {
        UUID matchId = createSingleMatch();

        mockMvc.perform(put("/api/v1/matches/{matchId}/result", matchId)
                        .header("Authorization", bearer(opponentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validResultPayload()))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/api/v1/matches/{matchId}/result", matchId)
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"games":[
                                  {"gameNo":1,"team1Score":21,"team2Score":20},
                                  {"gameNo":2,"team1Score":21,"team2Score":18}
                                ]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MATCH_SCORE_INVALID"))
                .andExpect(jsonPath("$.fieldErrors.games").isNotEmpty());
    }

    @Test
    @DisplayName("2.2.39 xac nhan tran cap nhat Elo mot lan cho moi nguoi choi")
    void confirmationUpdatesRatingsExactlyOnce() throws Exception {
        UUID matchId = createAndRecordResult();

        mockMvc.perform(post("/api/v1/matches/{matchId}/confirm", matchId)
                        .header("Authorization", bearer(opponentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("completed"))
                .andExpect(jsonPath("$.confirmedAt").isNotEmpty());

        assertThat(ratingHistoryRepository.findByMatchId(matchId)).hasSize(2);
        PlayerStatistics winner = statisticsRepository.findById(creator.getId()).orElseThrow();
        PlayerStatistics loser = statisticsRepository.findById(opponent.getId()).orElseThrow();
        assertThat(winner.getRatingScore()).isGreaterThan(PlayerStatistics.DEFAULT_RATING);
        assertThat(loser.getRatingScore()).isLessThan(PlayerStatistics.DEFAULT_RATING);

        mockMvc.perform(post("/api/v1/matches/{matchId}/confirm", matchId)
                        .header("Authorization", bearer(opponentToken)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_STATE_INVALID"));
        assertThat(ratingHistoryRepository.findByMatchId(matchId)).hasSize(2);
    }

    @Test
    @DisplayName("2.2.40 xac nhan tran cap nhat day du thong ke hieu suat")
    void confirmationUpdatesPlayerPerformanceStatistics() throws Exception {
        UUID matchId = createAndRecordResult();
        mockMvc.perform(post("/api/v1/matches/{matchId}/confirm", matchId)
                        .header("Authorization", bearer(opponentToken)))
                .andExpect(status().isOk());

        PlayerStatistics winner = statisticsRepository.findById(creator.getId()).orElseThrow();
        PlayerStatistics loser = statisticsRepository.findById(opponent.getId()).orElseThrow();
        assertThat(winner.getTotalMatches()).isEqualTo(1);
        assertThat(winner.getTotalWins()).isEqualTo(1);
        assertThat(winner.getTotalLosses()).isZero();
        assertThat(winner.getWinRate()).isEqualByComparingTo("100.00");
        assertThat(winner.getTotalGamesWon()).isEqualTo(2);
        assertThat(winner.getTotalGamesLost()).isEqualTo(1);
        assertThat(winner.getAvgPointsScored()).isEqualByComparingTo("61.00");
        assertThat(winner.getAvgPointsConceded()).isEqualByComparingTo("55.00");
        assertThat(loser.getTotalMatches()).isEqualTo(1);
        assertThat(loser.getTotalLosses()).isEqualTo(1);
        assertThat(loser.getWinRate()).isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("2.2.41 chi nguoi tham gia xem duoc ket qua va trang thai xac nhan")
    void participantsCanViewMatchResult() throws Exception {
        UUID matchId = createAndRecordResult();

        mockMvc.perform(get("/api/v1/matches/{matchId}", matchId)
                        .header("Authorization", bearer(opponentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.games.length()").value(3))
                .andExpect(jsonPath("$.winningTeam").value(1))
                .andExpect(jsonPath("$.team1[0].fullName").value(creator.getFullName()))
                .andExpect(jsonPath("$.team2[0].fullName").value(opponent.getFullName()))
                .andExpect(jsonPath("$.currentUserCanConfirm").value(true))
                .andExpect(jsonPath("$.currentUserCanDispute").value(true));

        mockMvc.perform(get("/api/v1/matches/{matchId}", matchId)
                        .header("Authorization", bearer(outsiderToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("2.2.42 lich su phan trang va chi danh dau tran da co ket qua can xac nhan")
    void historySeparatesUnscoredAndPendingConfirmationMatches() throws Exception {
        UUID unscoredMatchId = createSingleMatchAt(Instant.now().minus(2, ChronoUnit.DAYS));
        UUID pendingMatchId = createSingleMatchAt(Instant.now().minus(1, ChronoUnit.DAYS));
        recordResult(pendingMatchId);

        mockMvc.perform(get("/api/v1/matches")
                        .header("Authorization", bearer(opponentToken))
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content[0].id").value(pendingMatchId.toString()))
                .andExpect(jsonPath("$.content[0].currentUserNeedsConfirmation").value(true));

        mockMvc.perform(get("/api/v1/matches")
                        .header("Authorization", bearer(opponentToken))
                        .param("state", "pending"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(pendingMatchId.toString()));

        mockMvc.perform(get("/api/v1/matches/{matchId}", unscoredMatchId)
                        .header("Authorization", bearer(opponentToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentUserCanConfirm").value(false));
    }

    private String singleMatchPayload(UUID team1, UUID team2) throws Exception {
        return singleMatchPayload(team1, team2, Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private UUID createSingleMatch() throws Exception {
        return createSingleMatchAt(Instant.now().minus(1, ChronoUnit.DAYS));
    }

    private UUID createSingleMatchAt(Instant scheduledAt) throws Exception {
        String response = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleMatchPayload(creator.getId(), opponent.getId(), scheduledAt)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID createAndRecordResult() throws Exception {
        UUID matchId = createSingleMatch();
        recordResult(matchId);
        return matchId;
    }

    private void recordResult(UUID matchId) throws Exception {
        mockMvc.perform(put("/api/v1/matches/{matchId}/result", matchId)
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validResultPayload()))
                .andExpect(status().isOk());
    }

    private static String validResultPayload() {
        return """
                {"games":[
                  {"gameNo":1,"team1Score":21,"team2Score":18},
                  {"gameNo":2,"team1Score":19,"team2Score":21},
                  {"gameNo":3,"team1Score":21,"team2Score":16}
                ]}
                """;
    }

    private String singleMatchPayload(UUID team1, UUID team2, Instant scheduledAt) throws Exception {
        JsonNode payload = objectMapper.readTree("{}" );
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) payload;
        object.putNull("bookingId");
        object.putNull("venueId");
        object.put("matchType", "single");
        object.put("scheduledAt", scheduledAt.toString());
        object.putArray("team1UserIds").add(team1.toString());
        object.putArray("team2UserIds").add(team2.toString());
        return objectMapper.writeValueAsString(object);
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
                        .contentType(MediaType.APPLICATION_JSON)
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
