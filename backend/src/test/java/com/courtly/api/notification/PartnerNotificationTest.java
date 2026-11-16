package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.domain.notification.NotificationRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;

/** 2.3.56 - Thong bao yeu cau ghep cap; 2.3.57 - Thong bao phan hoi yeu cau ghep cap. */
class PartnerNotificationTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/partner-requests";

    @Autowired private NotificationRepository notificationRepository;
    @Autowired private NotificationPreferenceRepository preferenceRepository;

    private User sender;
    private User receiver;
    private String senderToken;
    private String receiverToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        sender = persistUser("partner-sender", Role.PLAYER);
        receiver = persistUser("partner-receiver", Role.PLAYER);
        senderToken = loginAs(sender);
        receiverToken = loginAs(receiver);
    }

    private List<Notification> notificationsOf(User user, String type) {
        return notificationRepository.findForUser(user.getId(), type, false, PageRequest.of(0, 50)).getContent();
    }

    private String send(String message) throws Exception {
        String response = mockMvc.perform(post(PATH).header("Authorization", bearer(senderToken)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + receiver.getId() + "\",\"message\":\"" + message + "\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asText();
    }

    private void act(String requestId, String action, String token, int expected) throws Exception {
        mockMvc.perform(post(PATH + "/" + requestId + "/" + action).header("Authorization", bearer(token))).andExpect(status().is(expected));
    }

    @Test
    @DisplayName("Gui yeu cau: nguoi nhan co thong bao partner_request kem ten nguoi gui, loi nhan, route va requestId; nguoi gui khong nhan")
    void requestNotifiesReceiver() throws Exception {
        String requestId = send("Minh hay danh doi toi thu 7");
        List<Notification> sent = notificationsOf(receiver, "partner_request");
        assertThat(sent).hasSize(1);
        Notification notification = sent.getFirst();
        assertThat(notification.getTitle()).isEqualTo("Lời mời ghép cặp mới");
        assertThat(notification.getContent()).contains(sender.getFullName()).contains("Minh hay danh doi toi thu 7");
        assertThat(notification.getDataJson()).containsEntry("route", "/partners/requests").containsEntry("requestId", requestId)
                .containsEntry("requesterId", sender.getId().toString());
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(sender.getId())).isZero();
    }

    @Test
    @DisplayName("Loi nhan dai duoc cat ngan co dau ba cham, khong lam thong bao qua dai")
    void longMessageIsShortened() throws Exception {
        send("x".repeat(200));
        String content = notificationsOf(receiver, "partner_request").getFirst().getContent();
        assertThat(content).contains("…").doesNotContain("x".repeat(121));
    }

    @Test
    @DisplayName("Nguoi nhan chap nhan: nguoi gui nhan thong bao partner_request_response goi y dat san, route toi danh sach doi tac; nguoi nhan khong nhan them")
    void acceptedNotifiesSender() throws Exception {
        String requestId = send("Choi cung nhau nhe");
        act(requestId, "accept", receiverToken, 200);

        List<Notification> sent = notificationsOf(sender, "partner_request_response");
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Lời mời ghép cặp được chấp nhận");
        assertThat(sent.getFirst().getContent()).contains(receiver.getFullName()).contains("đặt sân");
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/partners/connections").containsEntry("requestId", requestId);
        assertThat(notificationsOf(receiver, "partner_request_response")).isEmpty();
        assertThat(notificationsOf(receiver, "partner_request")).hasSize(1);
    }

    @Test
    @DisplayName("Nguoi nhan tu choi: nguoi gui nhan thong bao bi tu choi, route toi tim doi tac")
    void rejectedNotifiesSender() throws Exception {
        String requestId = send("Thu choi voi toi");
        act(requestId, "reject", receiverToken, 200);
        List<Notification> sent = notificationsOf(sender, "partner_request_response");
        assertThat(sent).hasSize(1);
        assertThat(sent.getFirst().getTitle()).isEqualTo("Lời mời ghép cặp bị từ chối");
        assertThat(sent.getFirst().getContent()).contains(receiver.getFullName());
        assertThat(sent.getFirst().getDataJson()).containsEntry("route", "/partners");
    }

    @Test
    @DisplayName("Nguoi gui huy yeu cau khong gui them thong bao; thao tac that bai (trung loi moi, tu gui, xu ly lai, sai nguoi) khong tao thong bao")
    void cancelAndFailuresSendNothing() throws Exception {
        String requestId = send("Loi moi se bi huy");
        // Loi moi trung khi con dang cho.
        mockMvc.perform(post(PATH).header("Authorization", bearer(senderToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + receiver.getId() + "\",\"message\":\"Gui lai\"}")).andExpect(status().isConflict());
        // Tu gui cho chinh minh.
        mockMvc.perform(post(PATH).header("Authorization", bearer(senderToken)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"receiverId\":\"" + sender.getId() + "\",\"message\":\"Tu gui\"}")).andExpect(status().isBadRequest());
        // Nguoi gui khong duoc chap nhan / tu choi loi moi cua chinh minh.
        act(requestId, "accept", senderToken, 403);
        assertThat(notificationsOf(receiver, "partner_request")).hasSize(1);
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(sender.getId())).isZero();

        act(requestId, "cancel", senderToken, 200);
        assertThat(notificationsOf(receiver)).hasSize(1);
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(sender.getId())).isZero();
        // Da huy roi: nguoi nhan khong chap nhan duoc nua.
        act(requestId, "accept", receiverToken, 409);
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(sender.getId())).isZero();
    }

    private List<Notification> notificationsOf(User user) {
        return notificationRepository.findForUser(user.getId(), null, false, PageRequest.of(0, 50)).getContent();
    }

    @Test
    @DisplayName("Tuy chon duoc ton trong: nguoi nhan tat partner_request thi khong co thong bao; nguoi gui tat partner_request_response thi khong biet phan hoi qua thong bao")
    void respectsPreferences() throws Exception {
        NotificationPreference receiverPref = new NotificationPreference(receiver, "partner_request");
        receiverPref.setInAppEnabled(false);
        preferenceRepository.saveAndFlush(receiverPref);
        NotificationPreference senderPref = new NotificationPreference(sender, "partner_request_response");
        senderPref.setInAppEnabled(false);
        preferenceRepository.saveAndFlush(senderPref);

        String requestId = send("Tat thong bao");
        assertThat(notificationsOf(receiver)).isEmpty();
        act(requestId, "accept", receiverToken, 200);
        assertThat(notificationsOf(sender)).isEmpty();
    }
}
