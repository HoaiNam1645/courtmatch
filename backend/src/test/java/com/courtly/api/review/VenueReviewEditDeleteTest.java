package com.courtly.api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.78 - sua va 2.3.79 - xoa danh gia san cua chinh minh. */
class VenueReviewEditDeleteTest extends VenueReviewTestBase {

    private JsonNode edit(String token, String reviewId, int expected, String body) throws Exception {
        return objectMapper.readTree(mockMvc.perform(put("/api/v1/venue-reviews/" + reviewId)
                        .header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private void remove(String token, String reviewId, int expected) throws Exception {
        mockMvc.perform(delete("/api/v1/venue-reviews/" + reviewId).header("Authorization", bearer(token)))
                .andExpect(status().is(expected));
    }

    private String write(String token, UUID booking, int rating, String comment) throws Exception {
        return review(token, booking, 201, body(rating, comment)).get("id").asText();
    }

    @Test
    @DisplayName("Sua so sao va nhan xet: luu moi, giu bookingId, moc sua cap nhat, diem trung binh cua san tinh lai")
    void editsOwnReview() throws Exception {
        UUID booking = completedBooking(playerToken, 1);
        String id = write(playerToken, booking, 5, "Rat tot");
        assertThat(venueRating()).isEqualTo(5.0);
        // Cung mot giao dich test thi now() khong doi; lui moc tao de thay duoc moc sua.
        jdbc.update("UPDATE venue_reviews SET created_at = created_at - interval '1 hour' WHERE id = ?::uuid", id);

        JsonNode edited = edit(playerToken, id, 200, body(2, "  Lan sau toi hon  "));
        assertThat(edited.get("id").asText()).isEqualTo(id);
        assertThat(edited.get("rating").asInt()).isEqualTo(2);
        assertThat(edited.get("comment").asText()).isEqualTo("Lan sau toi hon");
        assertThat(edited.get("bookingId").asText()).isEqualTo(booking.toString());
        assertThat(venueRating()).isEqualTo(2.0);
        assertThat(venueReviewCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT updated_at > created_at FROM venue_reviews WHERE id = ?::uuid", Boolean.class, id)).isTrue();

        // Xoa nhan xet: gui comment rong.
        assertThat(edit(playerToken, id, 200, body(4, "")).has("comment")).isFalse();
        assertThat(venueRating()).isEqualTo(4.0);
    }

    @Test
    @DisplayName("Chi sua duoc danh gia cua minh (nguoi khac va id la tra 404), du lieu sai bi tu choi")
    void editRequiresOwnershipAndValidInput() throws Exception {
        String id = write(playerToken, completedBooking(playerToken, 1), 5, null);
        String stranger = loginAs(persistUser("stranger-edit", Role.PLAYER));
        edit(stranger, id, 404, body(1, "Pha"));
        edit(playerToken, UUID.randomUUID().toString(), 404, body(1, null));
        assertThat(edit(playerToken, id, 400, body(7, null)).get("fieldErrors").has("rating")).isTrue();
        assertThat(edit(playerToken, id, 400, body(3, "x".repeat(1001))).get("fieldErrors").has("comment")).isTrue();
        mockMvc.perform(put("/api/v1/venue-reviews/" + id).contentType(MediaType.APPLICATION_JSON).content(body(3, null)))
                .andExpect(status().isUnauthorized());
        assertThat(venueRating()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Danh gia dang bi bao cao hoac da bi an khong sua, khong xoa duoc (409), giu nguyen du lieu")
    void lockedWhenReportedOrHidden() throws Exception {
        String id = write(playerToken, completedBooking(playerToken, 1), 5, "Goc");
        for (String locked : new String[] {"reported", "hidden"}) {
            jdbc.update("UPDATE venue_reviews SET status = ? WHERE id = ?::uuid", locked, id);
            assertThat(edit(playerToken, id, 409, body(1, "Doi")).get("code").asText()).isEqualTo("VENUE_REVIEW_STATE_INVALID");
            remove(playerToken, id, 409);
            assertThat(jdbc.queryForObject("SELECT comment FROM venue_reviews WHERE id = ?::uuid", String.class, id)).isEqualTo("Goc");
        }
        assertThat(edit(playerToken, id, 409, body(1, null)).get("message").asText()).contains("an");
    }

    @Test
    @DisplayName("Xoa danh gia: bien mat, diem trung binh va so danh gia cua san tinh lai, viet lai duoc cho cung don")
    void deletesOwnReview() throws Exception {
        String otherToken = loginAs(persistUser("reviewer-del", Role.PLAYER));
        UUID first = completedBooking(playerToken, 1);
        String firstId = write(playerToken, first, 5, null);
        write(otherToken, completedBooking(otherToken, 1), 3, null);
        assertThat(venueRating()).isEqualTo(4.0);

        remove(playerToken, firstId, 204);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM venue_reviews WHERE id = ?::uuid", Integer.class, firstId)).isZero();
        assertThat(venueRating()).isEqualTo(3.0);
        assertThat(venueReviewCount()).isEqualTo(1);
        remove(playerToken, firstId, 404);

        // Don nay duoc danh gia lai sau khi xoa.
        String again = write(playerToken, first, 1, "Viet lai");
        assertThat(again).isNotEqualTo(firstId);
        assertThat(venueReviewCount()).isEqualTo(2);
        assertThat(venueRating()).isEqualTo(2.0);
    }

    @Test
    @DisplayName("Xoa danh gia cuoi cung dua diem ve 0; nguoi khac khong xoa duoc; chua dang nhap bi tu choi")
    void deleteGuards() throws Exception {
        String id = write(playerToken, completedBooking(playerToken, 1), 4, null);
        String stranger = loginAs(persistUser("stranger-del", Role.PLAYER));
        remove(stranger, id, 404);
        assertThat(venueReviewCount()).isEqualTo(1);
        mockMvc.perform(delete("/api/v1/venue-reviews/" + id)).andExpect(status().isUnauthorized());

        remove(playerToken, id, 204);
        assertThat(venueRating()).isEqualTo(0.0);
        assertThat(venueReviewCount()).isZero();
    }
}
