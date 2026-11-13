package com.courtly.api.owner;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.Venue;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.16 - Danh sach thanh toan truc tiep cua san. */
class OwnerPaymentListTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/owner/payments";

    private User owner;
    private String ownerToken;
    private Venue venue;
    private Court court;
    private User player;
    private String playerToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-payments");
        ownerToken = loginAs(owner);
        venue = persistVenue(owner, "San Thanh Toan");
        court = venue.getCourts().getFirst();
        player = persistUser("player", Role.PLAYER);
        player.setPhone("0911000444");
        userRepository.saveAndFlush(player);
        playerToken = loginAs(player);
    }

    @Test
    @DisplayName("Chi co thanh toan sau khi chu san chap nhan; day du trang thai, so tien, phuong thuc, tai khoan nhan, nguoi tra")
    void listsPaymentsWithSnapshot() throws Exception {
        String pendingOnly = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        String accepted = createBooking(playerToken, court, nextMonday(), LocalTime.of(12, 0));
        accept(accepted);

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].bookingId").value(accepted))
                .andExpect(jsonPath("$.content[0].status").value("awaiting_payment"))
                .andExpect(jsonPath("$.content[0].method").value("owner_bank_transfer"))
                .andExpect(jsonPath("$.content[0].amount").value(180000))
                .andExpect(jsonPath("$.content[0].bookingStatus").value("awaiting_payment"))
                .andExpect(jsonPath("$.content[0].venueName").value("San Thanh Toan"))
                .andExpect(jsonPath("$.content[0].playerName").value(player.getFullName()))
                .andExpect(jsonPath("$.content[0].playerPhone").value("0911000444"))
                .andExpect(jsonPath("$.content[0].bankAccountNo").value("0359111001"))
                .andExpect(jsonPath("$.content[0].bankName").value("MB Bank"))
                .andExpect(jsonPath("$.content[0].expiredAt").exists())
                .andExpect(jsonPath("$.content[0].balance").doesNotExist())
                .andExpect(jsonPath("$.content[?(@.bookingId=='" + pendingOnly + "')]").isEmpty());
    }

    @Test
    @DisplayName("Theo doi du cac trang thai: cho thanh toan -> da bao -> da thu / chua nhan duoc tien; dem theo trang thai")
    void followsStatusLifecycle() throws Exception {
        String a = createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0));
        String b = createBooking(playerToken, court, nextMonday(), LocalTime.of(12, 0));
        String c = createBooking(playerToken, court, nextMonday(), LocalTime.of(15, 0));
        String paymentA = accept(a);
        String paymentB = accept(b);
        accept(c);
        report(paymentA);
        report(paymentB);
        mockMvc.perform(post("/api/v1/payments/" + paymentA + "/confirm-received").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/payments/" + paymentB + "/reject-report").header("Authorization", bearer(ownerToken))
                .contentType(MediaType.APPLICATION_JSON).content("{\"reason\":\"Chua thay tien\"}")).andExpect(status().isOk());

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.statusCounts.confirmed").value(1))
                .andExpect(jsonPath("$.statusCounts.rejected").value(1))
                .andExpect(jsonPath("$.statusCounts.awaiting_payment").value(1))
                .andExpect(jsonPath("$.statusCounts.payer_reported").value(0))
                .andExpect(jsonPath("$.statusCounts.disputed").value(0));
        mockMvc.perform(get(PATH).param("status", "confirmed").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].bookingId").value(a))
                .andExpect(jsonPath("$.content[0].confirmedAt").exists())
                .andExpect(jsonPath("$.content[0].payerReportedAt").exists())
                .andExpect(jsonPath("$.content[0].proofUrl").value("https://example.com/proof.png"));
        mockMvc.perform(get(PATH).param("status", "rejected").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content[0].bookingId").value(b))
                .andExpect(jsonPath("$.content[0].rejectionReason").value("Chua thay tien"))
                .andExpect(jsonPath("$.statusCounts.awaiting_payment").value(1));
        mockMvc.perform(get(PATH).param("status", "expired").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(0)));
    }

    @Test
    @DisplayName("Loc theo san, phan trang; chi thay thanh toan cua san minh")
    void venueFilterPaginationOwnership() throws Exception {
        accept(createBooking(playerToken, court, nextMonday(), LocalTime.of(9, 0)));
        accept(createBooking(playerToken, court, nextMonday(), LocalTime.of(12, 0)));
        Venue second = persistVenue(owner, "San Thu Hai");
        accept(createBooking(playerToken, second.getCourts().getFirst(), nextMonday(), LocalTime.of(9, 0)));

        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2));
        mockMvc.perform(get(PATH).param("size", "2").param("page", "1").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2));

        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(get(PATH).header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get(PATH).param("venueId", venue.getId().toString()).header("Authorization", bearer(otherToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Tham so sai 400; nguoi choi 403; chua dang nhap 401")
    void validation() throws Exception {
        mockMvc.perform(get(PATH).param("status", "bay-ba").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        mockMvc.perform(get(PATH).param("venueId", "khong-uuid").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("size", "0").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("page", "-1").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
    }

    /** Chap nhan yeu cau; tra ve id thanh toan vua tao. */
    private String accept(String bookingId) throws Exception {
        mockMvc.perform(post("/api/v1/owner/booking-requests/" + bookingId + "/accept").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
        String body = mockMvc.perform(get("/api/v1/payments/by-booking/" + bookingId).header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("id").asText();
    }

    private void report(String paymentId) throws Exception {
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/report-paid").header("Authorization", bearer(playerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"method\":\"owner_bank_transfer\",\"proofUrl\":\"https://example.com/proof.png\"}"))
                .andExpect(status().isOk());
        UUID.fromString(paymentId);
    }
}
