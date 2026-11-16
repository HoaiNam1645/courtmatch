package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
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
import com.courtly.service.notification.ReminderService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 2.3.60 - Nhac nho tran dau: nhac xac nhan ket qua truoc khi tu dong xac nhan sau 72 gio. */
class MatchReminderTest extends OwnerApiTestBase {

    private static final String RESULT = """
            {"games":[{"gameNo":1,"team1Score":21,"team2Score":18},{"gameNo":2,"team1Score":19,"team2Score":21},{"gameNo":3,"team1Score":21,"team2Score":16}]}
            """;

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private ReminderService reminderService;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;

    private User creator;
    private User opponent;
    private User third;
    private User fourth;
    private String creatorToken;
    private String opponentToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        creator = persistUser("match-r-creator", Role.PLAYER);
        opponent = persistUser("match-r-opponent", Role.PLAYER);
        third = persistUser("match-r-third", Role.PLAYER);
        fourth = persistUser("match-r-fourth", Role.PLAYER);
        creatorToken = loginAs(creator);
        opponentToken = loginAs(opponent);
    }

    private List<Notification> reminders(User user) {
        return notificationRepository.findForUser(user.getId(), "match_reminder", false, PageRequest.of(0, 50)).getContent();
    }

    private String singleMatch(boolean withResult) throws Exception {
        String body = """
                {"bookingId":null,"venueId":null,"matchType":"single","scheduledAt":"%s","team1UserIds":["%s"],"team2UserIds":["%s"]}
                """.formatted(Instant.now().minus(5, ChronoUnit.HOURS), creator.getId(), opponent.getId());
        String id = objectMapper.readTree(mockMvc.perform(post("/api/v1/matches").header("Authorization", bearer(creatorToken))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).get("id").asText();
        if (withResult) {
            mockMvc.perform(put("/api/v1/matches/" + id + "/result").header("Authorization", bearer(creatorToken))
                    .contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().isOk());
        }
        return id;
    }

    /** Dat thoi diem tao tran cach day {@code hoursAgo} gio (cac moc nhac tinh theo thoi diem tao). */
    private void createdHoursAgo(String matchId, long hoursAgo) {
        entityManager.flush();
        jdbc.update("UPDATE matches SET created_at = ? WHERE id = ?::uuid", java.sql.Timestamp.from(Instant.now().minus(hoursAgo, ChronoUnit.HOURS)), matchId);
        // Ghi het thay doi dang cho truoc khi xoa bo nho dem: JDBC khong thay thay doi chua flush.
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Tran da co ket qua tao 30 gio truoc: nguoi chua xac nhan duoc nhac (nguoi tao da xac nhan khi nhap thi khong), noi ro con khoang bao nhieu gio, route toi tran; chay lai khong gui trung")
    void remindsPlayersWhoHaveNotConfirmed() throws Exception {
        String matchId = singleMatch(true);
        createdHoursAgo(matchId, 30);
        assertThat(reminderService.remindMatches(Instant.now())).isEqualTo(1);

        List<Notification> sent = reminders(opponent);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Nhắc xác nhận kết quả trận đấu");
        assertThat(sent.getFirst().getContent()).contains("tự động được xác nhận").contains("khoảng 42 giờ").contains("báo sai");
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/matches/" + matchId).containsEntry("matchId", matchId);
        assertThat(reminders(creator)).isEmpty();

        assertThat(reminderService.remindMatches(Instant.now())).isZero();
        assertThat(reminders(opponent)).hasSize(1);
    }

    @Test
    @DisplayName("Moc 12 gio: tran tao 65 gio truoc duoc nhac them mot lan nua voi so gio con lai ngan hon")
    void finalReminder() throws Exception {
        String matchId = singleMatch(true);
        createdHoursAgo(matchId, 30);
        reminderService.remindMatches(Instant.now());
        createdHoursAgo(matchId, 65);
        assertThat(reminderService.remindMatches(Instant.now())).isEqualTo(1);
        assertThat(reminders(opponent)).hasSize(2);
        assertThat(reminders(opponent).getFirst().getContent()).contains("khoảng 7 giờ");
        assertThat(reminderService.remindMatches(Instant.now())).isZero();
    }

    @Test
    @DisplayName("Khong nhac: chua co ket qua, tao chua toi 24 gio, qua 72 gio, da huy, da bao sai, nguoi da xac nhan")
    void remindsOnlyWhenNeeded() throws Exception {
        String noResult = singleMatch(false);
        createdHoursAgo(noResult, 30);
        String fresh = singleMatch(true);
        createdHoursAgo(fresh, 10);
        String old = singleMatch(true);
        createdHoursAgo(old, 80);
        String cancelled = singleMatch(true);
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete("/api/v1/matches/" + cancelled)
                .header("Authorization", bearer(creatorToken))).andExpect(status().isNoContent());
        createdHoursAgo(cancelled, 30);
        String disputed = singleMatch(true);
        mockMvc.perform(post("/api/v1/matches/" + disputed + "/dispute").header("Authorization", bearer(opponentToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Ty so ghi sai\"}")).andExpect(status().isOk());
        createdHoursAgo(disputed, 30);
        String confirmed = singleMatch(true);
        mockMvc.perform(post("/api/v1/matches/" + confirmed + "/confirm").header("Authorization", bearer(opponentToken))).andExpect(status().isOk());
        createdHoursAgo(confirmed, 30);

        assertThat(reminderService.remindMatches(Instant.now())).isZero();
        assertThat(reminders(opponent)).isEmpty();
    }

    @Test
    @DisplayName("Tran danh doi: chi nhung nguoi chua xac nhan duoc nhac; mot nguoi da xac nhan thi khong bi nhac")
    void doublesRemindOnlyUnconfirmed() throws Exception {
        String body = """
                {"bookingId":null,"venueId":null,"matchType":"double","scheduledAt":"%s","team1UserIds":["%s","%s"],"team2UserIds":["%s","%s"]}
                """.formatted(Instant.now().minus(5, ChronoUnit.HOURS), creator.getId(), opponent.getId(), third.getId(), fourth.getId());
        String id = objectMapper.readTree(mockMvc.perform(post("/api/v1/matches").header("Authorization", bearer(creatorToken))
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isCreated()).andReturn().getResponse()
                .getContentAsString()).get("id").asText();
        mockMvc.perform(put("/api/v1/matches/" + id + "/result").header("Authorization", bearer(creatorToken))
                .contentType(MediaType.APPLICATION_JSON).content(RESULT)).andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/matches/" + id + "/confirm").header("Authorization", bearer(opponentToken))).andExpect(status().isOk());
        createdHoursAgo(id, 30);

        assertThat(reminderService.remindMatches(Instant.now())).isEqualTo(2);
        assertThat(reminders(third)).hasSize(1);
        assertThat(reminders(fourth)).hasSize(1);
        assertThat(reminders(opponent)).isEmpty();
        assertThat(reminders(creator)).isEmpty();
    }

    @Test
    @DisplayName("Tuy chon duoc ton trong: nguoi choi tat match_reminder thi khong nhan")
    void respectsPreferences() throws Exception {
        NotificationPreference preference = new NotificationPreference(opponent, "match_reminder");
        preference.setInAppEnabled(false);
        preferenceRepository.saveAndFlush(preference);
        String matchId = singleMatch(true);
        createdHoursAgo(matchId, 30);
        reminderService.remindMatches(Instant.now());
        assertThat(reminders(opponent)).isEmpty();
    }
}
