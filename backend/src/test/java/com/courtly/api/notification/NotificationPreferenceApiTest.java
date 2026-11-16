package com.courtly.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.domain.notification.NotificationType;
import com.courtly.service.notification.NotificationService;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.62 - Quan ly tuy chon nhan thong bao. */
class NotificationPreferenceApiTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/notifications/preferences";

    @Autowired private NotificationPreferenceRepository preferenceRepository;
    @Autowired private NotificationService notificationService;

    private User player;
    private User other;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        player = persistUser("pref-player", Role.PLAYER);
        other = persistUser("pref-other", Role.PLAYER);
        token = loginAs(player);
    }

    private String body(String... items) {
        return "{\"preferences\":[" + String.join(",", items) + "]}";
    }

    private String item(String type, boolean inApp, boolean email) {
        return "{\"type\":\"" + type + "\",\"inAppEnabled\":" + inApp + ",\"emailEnabled\":" + email + "}";
    }

    private JsonNode preferences() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get(PATH).header("Authorization", bearer(token))).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode find(JsonNode body, String type) {
        for (JsonNode item : body.get("preferences")) {
            if (type.equals(item.get("type").asText())) {
                return item;
            }
        }
        throw new AssertionError("Khong co loai " + type);
    }

    @Test
    @DisplayName("Nguoi dung moi chua co ban ghi: moi loai co nhan, mo ta, mac dinh bat ca hai kenh, push chua ho tro, loai bat buoc duoc danh dau")
    void defaults() throws Exception {
        JsonNode body = preferences();
        assertThat(body.get("preferences")).hasSize(NotificationType.ALL.length);
        for (JsonNode item : body.get("preferences")) {
            assertThat(item.get("label").asText()).isNotBlank();
            assertThat(item.get("description").asText()).isNotBlank();
            assertThat(item.get("inAppEnabled").asBoolean()).isTrue();
            assertThat(item.get("emailEnabled").asBoolean()).isTrue();
            assertThat(item.get("pushSupported").asBoolean()).isFalse();
        }
        assertThat(find(body, "debt_restriction").get("mandatory").asBoolean()).isTrue();
        assertThat(find(body, "booking_confirmed").get("mandatory").asBoolean()).isFalse();
        assertThat(preferenceRepository.findByUserId(player.getId())).isEmpty();
    }

    @Test
    @DisplayName("Cap nhat: luu theo loai, loai khong gui giu nguyen, lan sau doc lai dung, thong bao tuan theo tuy chon moi")
    void updatePersistsAndIsApplied() throws Exception {
        mockMvc.perform(put(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(item("booking_confirmed", false, true), item("payment_status", true, false))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.preferences", hasSize(NotificationType.ALL.length)));
        JsonNode body = preferences();
        assertThat(find(body, "booking_confirmed").get("inAppEnabled").asBoolean()).isFalse();
        assertThat(find(body, "booking_confirmed").get("emailEnabled").asBoolean()).isTrue();
        assertThat(find(body, "payment_status").get("inAppEnabled").asBoolean()).isTrue();
        assertThat(find(body, "payment_status").get("emailEnabled").asBoolean()).isFalse();
        assertThat(find(body, "booking_cancelled").get("inAppEnabled").asBoolean()).isTrue();

        assertThat(notificationService.send(player.getId(), "booking_confirmed", "A", "a", Map.of())).isEmpty();
        assertThat(notificationService.send(player.getId(), "payment_status", "B", "b", Map.of())).isPresent();

        // Bat lai, cap nhat ban ghi da co.
        mockMvc.perform(put(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(item("booking_confirmed", true, true)))).andExpect(status().isOk());
        assertThat(notificationService.send(player.getId(), "booking_confirmed", "C", "c", Map.of())).isPresent();
        assertThat(preferenceRepository.findByUserId(player.getId())).hasSize(2);
    }

    @Test
    @DisplayName("Chi sua tuy chon cua chinh minh: tuy chon nguoi khac khong doi")
    void ownPreferencesOnly() throws Exception {
        mockMvc.perform(put(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(body(item("booking_confirmed", false, false)))).andExpect(status().isOk());
        assertThat(preferenceRepository.findByUserId(other.getId())).isEmpty();
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(other)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences[?(@.type=='booking_confirmed')].inAppEnabled").value(true));
    }

    @Test
    @DisplayName("Loai bat buoc khong tat duoc kenh trong ung dung (400) nhung tat email duoc; khong ghi gi khi that bai")
    void mandatory() throws Exception {
        mockMvc.perform(put(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(item("booking_confirmed", false, false), item("debt_restriction", false, true))))
                .andExpect(status().isBadRequest());
        assertThat(preferenceRepository.findByUserId(player.getId())).isEmpty();
        mockMvc.perform(put(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                        .content(body(item("debt_restriction", true, false)))).andExpect(status().isOk())
                .andExpect(jsonPath("$.preferences[?(@.type=='debt_restriction')].emailEnabled").value(false));
    }

    @Test
    @DisplayName("Du lieu sai tra 400: danh sach rong / thieu, loai la, loai lap, thieu kenh, qua 20 muc, JSON hong")
    void validation() throws Exception {
        String[] bad = {
                "{}", body(), "{\"preferences\":null}", body(item("khong-co", true, true)),
                body(item("booking_confirmed", true, true), item("booking_confirmed", false, false)),
                body("{\"type\":\"booking_confirmed\",\"emailEnabled\":true}"),
                body("{\"type\":\"booking_confirmed\",\"inAppEnabled\":true}"),
                body("{\"inAppEnabled\":true,\"emailEnabled\":true}"),
                body("{\"type\":\"booking_confirmed\",\"inAppEnabled\":\"co\",\"emailEnabled\":true}"),
                "{\"preferences\":[", "khong phai json",
        };
        for (String payload : bad) {
            mockMvc.perform(put(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(payload))
                    .andExpect(status().isBadRequest());
        }
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 21; i++) {
            many.append(i == 0 ? "" : ",").append(item("booking_confirmed", true, true));
        }
        mockMvc.perform(put(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON)
                .content(body(many.toString()))).andExpect(status().isBadRequest());
        assertThat(preferenceRepository.findByUserId(player.getId())).isEmpty();
    }

    @Test
    @DisplayName("Moi vai tro dung duoc (chu san); chua dang nhap 401")
    void roles() throws Exception {
        User owner = persistOwner("pref-owner");
        mockMvc.perform(get(PATH).header("Authorization", bearer(loginAs(owner)))).andExpect(status().isOk());
        mockMvc.perform(put(PATH).header("Authorization", bearer(loginAs(owner))).contentType(MediaType.APPLICATION_JSON)
                .content(body(item("payment_status", true, false)))).andExpect(status().isOk());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(put(PATH).contentType(MediaType.APPLICATION_JSON).content(body(item("payment_status", true, false))))
                .andExpect(status().isUnauthorized());
    }
}
