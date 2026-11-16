package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.notification.Notification;
import com.courtly.domain.notification.NotificationRepository;
import com.courtly.domain.notification.NotificationType;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.61 - Xem lich su thong bao va danh dau da doc. */
class NotificationApiTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/notifications";

    @Autowired private NotificationRepository notificationRepository;

    private User player;
    private User other;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        player = persistUser("history-player", Role.PLAYER);
        other = persistUser("history-other", Role.PLAYER);
        token = loginAs(player);
    }

    private Notification notification(User user, String type, String title, int minutesAgo, boolean read) {
        Notification notification = new Notification();
        notification.setId(UUID.randomUUID());
        notification.setUser(user);
        notification.setType(type);
        notification.setTitle(title);
        notification.setContent("Noi dung " + title);
        notification.setDataJson(Map.of("route", "/bookings"));
        notification.setCreatedAt(Instant.now().minus(minutesAgo, ChronoUnit.MINUTES));
        notification.setReadAt(read ? Instant.now() : null);
        return notificationRepository.saveAndFlush(notification);
    }

    private JsonNode list(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(token));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    @Test
    @DisplayName("Danh sach moi nhat truoc, day du truong, chi thong bao cua minh, kem tong so chua doc")
    void listsOwnNewestFirst() throws Exception {
        notification(player, NotificationType.BOOKING_CONFIRMED, "Cu nhat", 30, true);
        notification(player, NotificationType.PAYMENT_STATUS, "Giua", 20, false);
        notification(player, NotificationType.BOOKING_CANCELLED, "Moi nhat", 10, false);
        notification(other, NotificationType.BOOKING_CONFIRMED, "Cua nguoi khac", 5, false);

        JsonNode body = list();
        assertThat(body.get("page").get("content")).hasSize(3);
        assertThat(body.get("page").get("totalElements").asLong()).isEqualTo(3);
        assertThat(body.get("unreadCount").asLong()).isEqualTo(2);
        JsonNode first = body.get("page").get("content").get(0);
        assertThat(first.get("title").asText()).isEqualTo("Moi nhat");
        assertThat(first.get("type").asText()).isEqualTo("booking_cancelled");
        assertThat(first.get("content").asText()).isEqualTo("Noi dung Moi nhat");
        assertThat(first.get("data").get("route").asText()).isEqualTo("/bookings");
        assertThat(first.get("read").asBoolean()).isFalse();
        assertThat(first.has("readAt")).isFalse();
        assertThat(first.get("createdAt").asText()).isNotBlank();
        assertThat(body.get("page").get("content").get(2).get("read").asBoolean()).isTrue();
        assertThat(body.get("page").get("content").get(2).get("readAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("Loc theo loai, chi chua doc, ket hop; tong chua doc khong phu thuoc bo loc; phan trang dung")
    void filtersAndPaging() throws Exception {
        for (int i = 0; i < 5; i++) {
            notification(player, NotificationType.BOOKING_CONFIRMED, "Xac nhan " + i, 50 - i, i % 2 == 0);
        }
        notification(player, NotificationType.PAYMENT_STATUS, "Thanh toan", 1, false);

        assertThat(list("type", "payment_status").get("page").get("content")).hasSize(1);
        assertThat(list("type", "all").get("page").get("totalElements").asLong()).isEqualTo(6);
        JsonNode unread = list("unreadOnly", "true");
        assertThat(unread.get("page").get("totalElements").asLong()).isEqualTo(3);
        assertThat(unread.get("unreadCount").asLong()).isEqualTo(3);
        JsonNode combined = list("type", "booking_confirmed", "unreadOnly", "true");
        assertThat(combined.get("page").get("totalElements").asLong()).isEqualTo(2);
        assertThat(combined.get("unreadCount").asLong()).isEqualTo(3);

        JsonNode second = list("size", "4", "page", "1");
        assertThat(second.get("page").get("content")).hasSize(2);
        assertThat(second.get("page").get("totalPages").asInt()).isEqualTo(2);
        assertThat(list().get("page").get("content").get(0).get("title").asText()).isEqualTo("Thanh toan");
    }

    @Test
    @DisplayName("Tham so sai tra 400: loai khong hop le, size ngoai 1-100, page am")
    void invalidParameters() throws Exception {
        for (String[] bad : new String[][] {{"type", "khong-co"}, {"size", "0"}, {"size", "101"}, {"page", "-1"}}) {
            mockMvc.perform(get(PATH).header("Authorization", bearer(token)).param(bad[0], bad[1])).andExpect(status().isBadRequest());
        }
        mockMvc.perform(post(PATH + "/read-all").header("Authorization", bearer(token)).param("type", "khong-co"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("So chua doc rieng: dem dung va chi cua minh")
    void unreadCount() throws Exception {
        notification(player, NotificationType.BOOKING_CONFIRMED, "A", 3, false);
        notification(player, NotificationType.BOOKING_CONFIRMED, "B", 2, true);
        notification(other, NotificationType.BOOKING_CONFIRMED, "C", 1, false);
        mockMvc.perform(get(PATH + "/unread-count").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.unreadCount").value(1));
    }

    @Test
    @DisplayName("Danh dau da doc: ghi gio doc, doc lai khong doi gio, thong bao cua nguoi khac 404, id la 404, sai dinh dang 400")
    void markRead() throws Exception {
        Notification mine = notification(player, NotificationType.BOOKING_CONFIRMED, "Cua toi", 5, false);
        Notification theirs = notification(other, NotificationType.BOOKING_CONFIRMED, "Cua nguoi khac", 5, false);

        JsonNode first = objectMapper.readTree(mockMvc.perform(post(PATH + "/" + mine.getId() + "/read").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.read").value(true)).andReturn().getResponse().getContentAsString());
        String readAt = first.get("readAt").asText();
        mockMvc.perform(post(PATH + "/" + mine.getId() + "/read").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.readAt").value(readAt));
        mockMvc.perform(post(PATH + "/" + theirs.getId() + "/read").header("Authorization", bearer(token))).andExpect(status().isNotFound());
        assertThat(notificationRepository.findById(theirs.getId()).orElseThrow().getReadAt()).isNull();
        mockMvc.perform(post(PATH + "/" + UUID.randomUUID() + "/read").header("Authorization", bearer(token))).andExpect(status().isNotFound());
        mockMvc.perform(post(PATH + "/khong-phai-uuid/read").header("Authorization", bearer(token))).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Danh dau tat ca da doc: chi cua minh, co the theo loai, tra ve so vua doi va so chua doc con lai")
    void markAllRead() throws Exception {
        notification(player, NotificationType.BOOKING_CONFIRMED, "A", 5, false);
        notification(player, NotificationType.BOOKING_CONFIRMED, "B", 4, false);
        notification(player, NotificationType.PAYMENT_STATUS, "C", 3, false);
        notification(other, NotificationType.BOOKING_CONFIRMED, "D", 2, false);

        mockMvc.perform(post(PATH + "/read-all").header("Authorization", bearer(token)).param("type", "booking_confirmed"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(2)).andExpect(jsonPath("$.unreadCount").value(1));
        mockMvc.perform(post(PATH + "/read-all").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(1)).andExpect(jsonPath("$.unreadCount").value(0));
        mockMvc.perform(post(PATH + "/read-all").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(0));
        assertThat(notificationRepository.countByUserIdAndReadAtIsNull(other.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("Moi vai tro xem duoc thong bao cua chinh minh (nguoi choi, chu san, quan tri); chua dang nhap 401")
    void everyRole() throws Exception {
        User owner = persistOwner("history-owner");
        notification(owner, NotificationType.PAYMENT_STATUS, "Cua chu san", 5, false);
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.page.content", hasSize(1)))
                .andExpect(jsonPath("$.page.content[0].title").value("Cua chu san"));
        mockMvc.perform(get(PATH).header("Authorization", bearer(token))).andExpect(status().isOk()).andExpect(jsonPath("$.page.content", hasSize(0)));
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(PATH + "/unread-count")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH + "/read-all")).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
