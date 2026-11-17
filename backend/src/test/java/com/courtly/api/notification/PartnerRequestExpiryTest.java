package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.domain.notification.NotificationRepository;
import com.courtly.service.matching.PartnerRequestService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Yeu cau ghep cap khong duoc phan hoi trong 60 phut tu het han, nguoi gui duoc bao. */
class PartnerRequestExpiryTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/partner-requests";

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private PartnerRequestService requestService;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;

    private User sender;
    private User receiver;
    private String senderToken;
    private String receiverToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        sender = persistUser("expiry-sender", Role.PLAYER);
        receiver = persistUser("expiry-receiver", Role.PLAYER);
        senderToken = loginAs(sender);
        receiverToken = loginAs(receiver);
    }

    private String send(String message) throws Exception {
        String response = mockMvc.perform(post(PATH).header("Authorization", bearer(senderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + receiver.getId() + "\",\"message\":\"" + message + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private void age(String requestId, int minutes) {
        entityManager.flush();
        jdbc.update("UPDATE partner_requests SET created_at = now() - make_interval(mins => ?) WHERE id = ?::uuid", minutes, requestId);
        entityManager.clear();
    }

    private String statusOf(String requestId) {
        entityManager.flush();
        return jdbc.queryForObject("SELECT status FROM partner_requests WHERE id = ?::uuid", String.class, requestId);
    }

    private void act(String requestId, String action, String token, int expected) throws Exception {
        mockMvc.perform(post(PATH + "/" + requestId + "/" + action).header("Authorization", bearer(token))).andExpect(status().is(expected));
    }

    private List<Notification> expiredOf(User user) {
        entityManager.flush();
        return notificationRepository.findForUser(user.getId(), "partner_request_expired", false, PageRequest.of(0, 50)).getContent();
    }

    @Test
    @DisplayName("Job het han: chi yeu cau dang cho qua 60 phut chuyen expired, nguoi gui nhan thong bao route /partners kem requestId; yeu cau moi giu nguyen; chay lai khong thong bao trung")
    void jobExpiresOnlyOverdue() throws Exception {
        String old = send("Loi moi cu");
        User other = persistUser("expiry-other", Role.PLAYER);
        mockMvc.perform(post(PATH).header("Authorization", bearer(senderToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + other.getId() + "\",\"message\":\"Loi moi moi\"}")).andExpect(status().isCreated());
        age(old, 61);

        assertThat(requestService.expireOverdue(Instant.now())).isGreaterThanOrEqualTo(1);
        assertThat(statusOf(old)).isEqualTo("expired");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM partner_requests WHERE requester_id = ? AND status = 'pending'",
                Integer.class, sender.getId())).isEqualTo(1);

        List<Notification> sent = expiredOf(sender);
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Lời mời ghép cặp đã hết hạn");
        assertThat(sent.getFirst().getContent()).contains(receiver.getFullName());
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/partners").containsEntry("requestId", old);
        assertThat(expiredOf(receiver)).isEmpty();

        requestService.expireOverdue(Instant.now());
        assertThat(expiredOf(sender)).hasSize(1);
    }

    @Test
    @DisplayName("Yeu cau con trong han (59 phut) khong bi het han")
    void withinWindowStaysPending() throws Exception {
        String id = send("Van con han");
        age(id, 59);
        requestService.expireOverdue(Instant.now());
        assertThat(statusOf(id)).isEqualTo("pending");
        act(id, "accept", receiverToken, 200);
    }

    @Test
    @DisplayName("Chap nhan / tu choi / huy yeu cau da qua han bi 409, yeu cau thanh expired, khong tao ket noi doi tac")
    void actionsOnOverdueAreRejected() throws Exception {
        for (String[] step : new String[][]{{"accept", "r"}, {"reject", "r"}, {"cancel", "s"}}) {
            String id = send("Qua han " + step[0]);
            age(id, 90);
            act(id, step[0], step[1].equals("r") ? receiverToken : senderToken, 409);
            assertThat(statusOf(id)).as(step[0]).isEqualTo("expired");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM player_connections WHERE user_id = ?", Integer.class, sender.getId())).isZero();
        assertThat(expiredOf(sender)).hasSize(3);
    }

    @Test
    @DisplayName("Sau khi het han gui lai duoc yeu cau moi (khong bi bao trung)")
    void canResendAfterExpiry() throws Exception {
        String old = send("Lan dau");
        age(old, 120);
        String again = send("Gui lai");
        assertThat(again).isNotEqualTo(old);
        assertThat(statusOf(old)).isEqualTo("expired");
        assertThat(statusOf(again)).isEqualTo("pending");
    }

    @Test
    @DisplayName("Danh sach da nhan va da gui hien yeu cau qua han la expired, loc pending khong con thay")
    void listsShowExpired() throws Exception {
        String id = send("Se het han");
        age(id, 75);
        mockMvc.perform(get(PATH + "/received?status=pending").header("Authorization", bearer(receiverToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0));
        mockMvc.perform(get(PATH + "/sent?status=expired").header("Authorization", bearer(senderToken)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].status").value("expired"))
                .andExpect(jsonPath("$.content[0].id").value(id));
    }

    @Test
    @DisplayName("Tuy chon duoc ton trong: nguoi gui tat partner_request_expired thi khong co thong bao nhung yeu cau van het han")
    void respectsPreference() throws Exception {
        NotificationPreference pref = new NotificationPreference(sender, "partner_request_expired");
        pref.setInAppEnabled(false);
        preferenceRepository.saveAndFlush(pref);
        String id = send("Tat thong bao het han");
        age(id, 70);
        requestService.expireOverdue(Instant.now());
        assertThat(statusOf(id)).isEqualTo("expired");
        assertThat(expiredOf(sender)).isEmpty();
    }
}
