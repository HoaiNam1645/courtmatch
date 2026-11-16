package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.domain.notification.NotificationRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

/** 2.3.58 - Thong bao tran dau: duoc ghi vao tran, ket qua can xac nhan, tran bi huy. */
class MatchNotificationTest extends OwnerApiTestBase {

    private static final String RESULT = """
            {"games":[{"gameNo":1,"team1Score":21,"team2Score":18},{"gameNo":2,"team1Score":19,"team2Score":21},{"gameNo":3,"team1Score":21,"team2Score":16}]}
            """;

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;

    private User creator;
    private User opponent;
    private User third;
    private User fourth;
    private String creatorToken;
    private String opponentToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        creator = persistUser("match-n-creator", Role.PLAYER);
        opponent = persistUser("match-n-opponent", Role.PLAYER);
        third = persistUser("match-n-third", Role.PLAYER);
        fourth = persistUser("match-n-fourth", Role.PLAYER);
        creatorToken = loginAs(creator);
        opponentToken = loginAs(opponent);
    }

    private List<Notification> notificationsOf(User user) {
        return notificationRepository.findForUser(user.getId(), "match_scheduled", false, PageRequest.of(0, 50)).getContent();
    }

    private String singleBody(String venueId) {
        return """
                {"bookingId":null,"venueId":%s,"matchType":"single","scheduledAt":"%s","team1UserIds":["%s"],"team2UserIds":["%s"]}
                """.formatted(venueId == null ? "null" : "\"" + venueId + "\"", Instant.now().minus(3, ChronoUnit.HOURS), creator.getId(), opponent.getId());
    }

    private String create(String body, String token, int expected) throws Exception {
        String response = mockMvc.perform(post("/api/v1/matches").header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body)).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return expected == 201 ? objectMapper.readTree(response).get("id").asText() : null;
    }

    private void recordResult(String matchId, String token, int expected) throws Exception {
        mockMvc.perform(put("/api/v1/matches/" + matchId + "/result").header("Authorization", bearer(token))
                .contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().is(expected));
    }

    @Test
    @DisplayName("Tao tran don: doi thu nhan thong bao match_scheduled co ten nguoi tao, hinh thuc, gio va route toi tran; nguoi tao khong nhan")
    void createdNotifiesOpponent() throws Exception {
        String matchId = create(singleBody(null), creatorToken, 201);
        List<Notification> sent = notificationsOf(opponent);
        assertThat(sent).hasSize(1);
        Notification notification = sent.getFirst();
        assertThat(notification.getTitle()).isEqualTo("Bạn được ghi vào một trận đấu");
        assertThat(notification.getContent()).contains(creator.getFullName()).contains("trận đơn").containsPattern("lúc \\d{2}:\\d{2} \\d{2}/\\d{2}/\\d{4}");
        assertThat(notification.getDataJson()).containsEntry("route", "/matches/" + matchId).containsEntry("matchId", matchId);
        assertThat(notificationsOf(creator)).isEmpty();
    }

    @Test
    @DisplayName("Tran danh doi: ba nguoi con lai deu duoc bao, nguoi tao khong; tran co san thi noi ro ten san")
    void doubleMatchNotifiesEveryoneElse() throws Exception {
        User owner = persistOwner("match-n-owner");
        String venueId = persistVenue(owner, "San Cau Long Tran Dau").getId().toString();
        String body = """
                {"bookingId":null,"venueId":"%s","matchType":"double","scheduledAt":"%s","team1UserIds":["%s","%s"],"team2UserIds":["%s","%s"]}
                """.formatted(venueId, Instant.now().minus(2, ChronoUnit.HOURS), creator.getId(), opponent.getId(), third.getId(), fourth.getId());
        create(body, creatorToken, 201);
        for (User user : List.of(opponent, third, fourth)) {
            assertThat(notificationsOf(user)).hasSize(1);
            assertThat(notificationsOf(user).getFirst().getContent()).contains("trận đôi").contains("tại San Cau Long Tran Dau");
        }
        assertThat(notificationsOf(creator)).isEmpty();
    }

    @Test
    @DisplayName("Nhap ket qua: cac nguoi choi khac nhan thong bao can xac nhan; nhap lai ket qua bao lai; nguoi tao khong nhan")
    void resultRecordedNotifiesOthers() throws Exception {
        String matchId = create(singleBody(null), creatorToken, 201);
        recordResult(matchId, creatorToken, 200);
        List<Notification> sent = notificationsOf(opponent);
        assertThat(sent).hasSize(2);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Kết quả trận đấu cần bạn xác nhận");
        assertThat(sent.getFirst().getContent()).contains(creator.getFullName()).contains("xác nhận").contains("báo sai");
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/matches/" + matchId);

        recordResult(matchId, creatorToken, 200);
        assertThat(notificationsOf(opponent)).hasSize(3);
        assertThat(notificationsOf(creator)).isEmpty();
    }

    @Test
    @DisplayName("Huy tran: nguoi con lai nhan thong bao huy co route toi danh sach tran; nguoi huy khong nhan")
    void cancelledNotifiesOthers() throws Exception {
        String matchId = create(singleBody(null), creatorToken, 201);
        mockMvc.perform(delete("/api/v1/matches/" + matchId).header("Authorization", bearer(creatorToken))).andExpect(status().isNoContent());
        List<Notification> sent = notificationsOf(opponent);
        assertThat(sent).hasSize(2);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Trận đấu đã bị hủy");
        assertThat(sent.getFirst().getContent()).contains(creator.getFullName()).contains("không còn được tính");
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/matches");
        assertThat(notificationsOf(creator)).isEmpty();
    }

    @Test
    @DisplayName("Thao tac that bai khong tao thong bao: tao tran sai, nguoi khong phai chu tran nhap ket qua / huy, huy tran da huy")
    void failedActionsSendNothing() throws Exception {
        create("{\"matchType\":\"single\",\"team1UserIds\":[],\"team2UserIds\":[]}", creatorToken, 400);
        assertThat(notificationsOf(opponent)).isEmpty();

        String matchId = create(singleBody(null), creatorToken, 201);
        recordResult(matchId, opponentToken, 403);
        mockMvc.perform(delete("/api/v1/matches/" + matchId).header("Authorization", bearer(opponentToken))).andExpect(status().isForbidden());
        assertThat(notificationsOf(opponent)).hasSize(1);
        assertThat(notificationsOf(creator)).isEmpty();

        mockMvc.perform(delete("/api/v1/matches/" + matchId).header("Authorization", bearer(creatorToken))).andExpect(status().isNoContent());
        mockMvc.perform(delete("/api/v1/matches/" + matchId).header("Authorization", bearer(creatorToken))).andExpect(status().isConflict());
        assertThat(notificationsOf(opponent)).hasSize(2);
    }

    @Test
    @DisplayName("Xac nhan va bao sai ket qua khong gui them thong bao; nguoi choi tat match_scheduled thi khong nhan")
    void confirmationSendsNothingAndPreferencesApply() throws Exception {
        String matchId = create(singleBody(null), creatorToken, 201);
        recordResult(matchId, creatorToken, 200);
        long before = notificationsOf(opponent).size();
        mockMvc.perform(post("/api/v1/matches/" + matchId + "/confirm").header("Authorization", bearer(opponentToken))).andExpect(status().isOk());
        assertThat(notificationsOf(opponent)).hasSize((int) before);
        assertThat(notificationsOf(creator)).isEmpty();

        NotificationPreference preference = new NotificationPreference(third, "match_scheduled");
        preference.setInAppEnabled(false);
        preferenceRepository.saveAndFlush(preference);
        String body = """
                {"bookingId":null,"venueId":null,"matchType":"double","scheduledAt":"%s","team1UserIds":["%s","%s"],"team2UserIds":["%s","%s"]}
                """.formatted(Instant.now().minus(1, ChronoUnit.HOURS), creator.getId(), opponent.getId(), third.getId(), fourth.getId());
        create(body, creatorToken, 201);
        assertThat(notificationsOf(third)).isEmpty();
        assertThat(notificationsOf(fourth)).hasSize(1);
    }
}
