package com.courtly.api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/** 2.3.77 - viet danh gia san sau khi choi. */
class VenueReviewCreateTest extends VenueReviewTestBase {

    @Test
    @DisplayName("Viet danh gia cho don da hoan thanh: luu 5 sao + nhan xet da cat khoang trang, cap nhat diem trung binh cua san")
    void createsReviewAndUpdatesVenueRating() throws Exception {
        UUID bookingId = completedBooking(playerToken, 1);
        JsonNode created = review(playerToken, bookingId, 201, body(5, "  San dep, chu san than thien  "));
        assertThat(created.get("rating").asInt()).isEqualTo(5);
        assertThat(created.get("comment").asText()).isEqualTo("San dep, chu san than thien");
        assertThat(created.get("status").asText()).isEqualTo("visible");
        assertThat(created.get("bookingId").asText()).isEqualTo(bookingId.toString());
        assertThat(created.get("venueId").asText()).isEqualTo(venue.getId().toString());
        assertThat(venueRating()).isEqualTo(5.0);
        assertThat(venueReviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("Diem trung binh tinh lai tu cac danh gia hien thi cua nhieu nguoi")
    void averagesAcrossReviewers() throws Exception {
        User other = persistUser("reviewer-2", Role.PLAYER);
        String otherToken = loginAs(other);
        review(playerToken, completedBooking(playerToken, 1), 201, body(5, null));
        review(otherToken, completedBooking(otherToken, 2), 201, body(2, "Tam duoc"));
        assertThat(venueRating()).isEqualTo(3.5);
        assertThat(venueReviewCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Nhan xet rong hoac chi khoang trang luu la null; khong nhan xet van viet duoc")
    void blankCommentIsNull() throws Exception {
        assertThat(review(playerToken, completedBooking(playerToken, 1), 201, body(4, "   ")).has("comment")).isFalse();
        assertThat(review(playerToken, completedBooking(playerToken, 1), 201, body(3, null)).has("comment")).isFalse();
    }

    @Test
    @DisplayName("Moi don chi danh gia mot lan")
    void oneReviewPerBooking() throws Exception {
        UUID bookingId = completedBooking(playerToken, 1);
        review(playerToken, bookingId, 201, body(5, null));
        JsonNode again = review(playerToken, bookingId, 409, body(1, "Viet lai"));
        assertThat(again.get("code").asText()).isEqualTo("VENUE_REVIEW_STATE_INVALID");
        assertThat(venueReviewCount()).isEqualTo(1);
        assertThat(venueRating()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("Don chua hoan thanh khong danh gia duoc")
    void bookingMustBeCompleted() throws Exception {
        String pending = createBooking(playerToken, court, nextMonday(), java.time.LocalTime.of(20, 0));
        JsonNode error = review(playerToken, UUID.fromString(pending), 409, body(5, null));
        assertThat(error.get("code").asText()).isEqualTo("VENUE_REVIEW_STATE_INVALID");
        assertThat(venueReviewCount()).isZero();
    }

    @Test
    @DisplayName("Don cua nguoi khac hoac khong ton tai tra 404, khong lo don co ton tai")
    void onlyOwnBooking() throws Exception {
        UUID bookingId = completedBooking(playerToken, 1);
        String stranger = loginAs(persistUser("stranger", Role.PLAYER));
        review(stranger, bookingId, 404, body(5, null));
        review(playerToken, UUID.randomUUID(), 404, body(5, null));
        assertThat(venueReviewCount()).isZero();
    }

    @Test
    @DisplayName("Chi trong 7 ngay ke tu luc hoan thanh: ngay thu 6 con duoc, ngay thu 8 qua han")
    void respectsTheReviewWindow() throws Exception {
        review(playerToken, completedBooking(playerToken, 6), 201, body(5, null));
        JsonNode late = review(playerToken, completedBooking(playerToken, 8), 409, body(5, null));
        assertThat(late.get("message").asText()).contains("7 ngay");
        assertThat(venueReviewCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("So sao phai tu 1 den 5, nhan xet toi da 1000 ky tu")
    void validatesInput() throws Exception {
        UUID bookingId = completedBooking(playerToken, 1);
        assertThat(review(playerToken, bookingId, 400, body(0, null)).get("fieldErrors").has("rating")).isTrue();
        assertThat(review(playerToken, bookingId, 400, body(6, null)).get("fieldErrors").has("rating")).isTrue();
        assertThat(review(playerToken, bookingId, 400, "{\"comment\":\"abc\"}").get("fieldErrors").has("rating")).isTrue();
        assertThat(review(playerToken, bookingId, 400, body(5, "x".repeat(1001))).get("fieldErrors").has("comment")).isTrue();
        review(playerToken, bookingId, 201, body(5, "x".repeat(1000)));
    }

    @Test
    @DisplayName("Chua dang nhap bi tu choi")
    void requiresLogin() throws Exception {
        mockMvc.perform(post("/api/v1/bookings/" + UUID.randomUUID() + "/venue-review")
                        .contentType(MediaType.APPLICATION_JSON).content(body(5, null)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Lich su dat san cho biet don nao con danh gia duoc, han cuoi va danh gia da viet")
    void bookingHistoryExposesReviewState() throws Exception {
        UUID fresh = completedBooking(playerToken, 1);
        UUID reviewed = completedBooking(playerToken, 2);
        UUID expired = completedBooking(playerToken, 9);
        String pending = createBooking(playerToken, court, nextMonday(), java.time.LocalTime.of(19, 0));
        review(playerToken, reviewed, 201, body(4, "Kha on"));

        JsonNode page = objectMapper.readTree(mockMvc.perform(get("/api/v1/bookings").header("Authorization", bearer(playerToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode freshItem = find(page, fresh.toString());
        assertThat(freshItem.get("canReview").asBoolean()).isTrue();
        assertThat(freshItem.has("review")).isFalse();
        assertThat(freshItem.get("reviewDeadline").asText()).isNotBlank();
        JsonNode reviewedItem = find(page, reviewed.toString());
        assertThat(reviewedItem.get("canReview").asBoolean()).isFalse();
        assertThat(reviewedItem.get("review").get("rating").asInt()).isEqualTo(4);
        assertThat(reviewedItem.get("review").get("status").asText()).isEqualTo("visible");
        assertThat(find(page, expired.toString()).get("canReview").asBoolean()).isFalse();
        JsonNode pendingItem = find(page, pending);
        assertThat(pendingItem.get("canReview").asBoolean()).isFalse();
        assertThat(pendingItem.has("reviewDeadline")).isFalse();
    }

    private static JsonNode find(JsonNode page, String id) {
        for (JsonNode item : page.get("content")) if (item.get("id").asText().equals(id)) return item;
        throw new AssertionError("booking " + id + " missing from history");
    }
}
