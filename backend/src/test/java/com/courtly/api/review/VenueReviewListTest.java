package com.courtly.api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.Role;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.80 - xem danh sach danh gia cua mot san (cong khai, co phan trang). */
class VenueReviewListTest extends VenueReviewTestBase {

    @jakarta.persistence.PersistenceContext private jakarta.persistence.EntityManager entityManager;

    private JsonNode list(String token, String query) throws Exception {
        var request = get("/api/v1/venues/" + venue.getSlug() + "/reviews" + query);
        if (token != null) request = request.header("Authorization", bearer(token));
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode summary() throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/venues/" + venue.getSlug() + "/reviews/summary"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    /** Viet danh gia qua API roi dat moc tao de sap xep on dinh. */
    private String write(String token, int rating, String comment, int minutesAgo) throws Exception {
        String id = review(token, completedBooking(token, 1), 201, body(rating, comment)).get("id").asText();
        setTimes(id, minutesAgo, minutesAgo);
        return id;
    }

    /** Dat moc tao/sua tinh bang phut truoc; tam tat trigger tu cap nhat updated_at (hoan lai khi rollback giao dich test). */
    private void setTimes(String id, int createdMinutesAgo, int updatedMinutesAgo) {
        jdbc.execute("ALTER TABLE venue_reviews DISABLE TRIGGER vr_set_updated_at");
        jdbc.update("UPDATE venue_reviews SET created_at = now() - (? || ' minutes')::interval, updated_at = now() - (? || ' minutes')::interval WHERE id = ?::uuid",
                createdMinutesAgo, updatedMinutesAgo, id);
        jdbc.execute("ALTER TABLE venue_reviews ENABLE TRIGGER vr_set_updated_at");
        entityManager.clear(); // JDBC da doi du lieu, bo ban sao cu trong bo nho
    }

    @Test
    @DisplayName("Chi hien danh gia visible, moi nhat truoc, khong lo email, nguoi xem chua dang nhap thi mine = false")
    void listsVisibleReviewsNewestFirst() throws Exception {
        String a = loginAs(persistUser("list-a", Role.PLAYER));
        String b = loginAs(persistUser("list-b", Role.PLAYER));
        String c = loginAs(persistUser("list-c", Role.PLAYER));
        String d = loginAs(persistUser("list-d", Role.PLAYER));
        String older = write(a, 5, "Cu nhat", 30);
        String newer = write(b, 4, "Moi hon", 10);
        String reported = write(c, 1, "Bi bao cao", 5);
        String hidden = write(d, 2, "Bi an", 3);
        jdbc.update("UPDATE venue_reviews SET status = 'reported' WHERE id = ?::uuid", reported);
        jdbc.update("UPDATE venue_reviews SET status = 'hidden' WHERE id = ?::uuid", hidden);

        JsonNode page = list(null, "");
        assertThat(page.get("totalElements").asLong()).isEqualTo(2);
        assertThat(page.get("content").get(0).get("id").asText()).isEqualTo(newer);
        assertThat(page.get("content").get(1).get("id").asText()).isEqualTo(older);
        JsonNode first = page.get("content").get(0);
        assertThat(first.get("reviewerName").asText()).startsWith("Nguoi Dung list-b");
        assertThat(first.get("comment").asText()).isEqualTo("Moi hon");
        assertThat(first.get("mine").asBoolean()).isFalse();
        assertThat(first.get("edited").asBoolean()).isFalse();
        assertThat(first.has("email")).isFalse();
        assertThat(first.has("userId")).isFalse();
        assertThat(page.toString()).doesNotContain("@example.com");
    }

    @Test
    @DisplayName("Nguoi dang nhap thay mine = true tren danh gia cua chinh minh, danh gia cua nguoi khac van false")
    void marksOwnReview() throws Exception {
        String a = loginAs(persistUser("mine-a", Role.PLAYER));
        String b = loginAs(persistUser("mine-b", Role.PLAYER));
        String ownId = write(a, 5, "Cua a", 20);
        write(b, 3, "Cua b", 10);

        for (JsonNode item : list(a, "").get("content")) {
            assertThat(item.get("mine").asBoolean()).isEqualTo(item.get("id").asText().equals(ownId));
        }
        for (JsonNode item : list(null, "").get("content")) assertThat(item.get("mine").asBoolean()).isFalse();
        // Token sai thi bi tu choi nhu moi API khac.
        mockMvc.perform(get("/api/v1/venues/" + venue.getSlug() + "/reviews").header("Authorization", "Bearer not-a-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Danh gia da sua hien nhan edited; luu cung luc (duoi 1 giay) thi khong tinh la da sua")
    void flagsEditedReviews() throws Exception {
        String a = loginAs(persistUser("edit-a", Role.PLAYER));
        String id = write(a, 5, "Goc", 60);
        assertThat(list(null, "").get("content").get(0).get("edited").asBoolean()).isFalse();
        setTimes(id, 60, 55);
        assertThat(list(null, "").get("content").get(0).get("edited").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("Loc theo so sao va phan trang; so sao la bi tu choi")
    void filtersByRatingAndPaginates() throws Exception {
        for (int i = 0; i < 3; i++) write(loginAs(persistUser("page-" + i, Role.PLAYER)), 5, "Nam sao " + i, 10 + i);
        write(loginAs(persistUser("page-x", Role.PLAYER)), 2, "Hai sao", 1);

        assertThat(list(null, "").get("totalElements").asLong()).isEqualTo(4);
        JsonNode five = list(null, "?rating=5");
        assertThat(five.get("totalElements").asLong()).isEqualTo(3);
        five.get("content").forEach(item -> assertThat(item.get("rating").asInt()).isEqualTo(5));
        assertThat(list(null, "?rating=2").get("content").get(0).get("comment").asText()).isEqualTo("Hai sao");
        assertThat(list(null, "?rating=4").get("content")).isEmpty();

        JsonNode second = list(null, "?size=2&page=1");
        assertThat(second.get("content")).hasSize(2);
        assertThat(second.get("totalPages").asInt()).isEqualTo(2);
        assertThat(list(null, "?rating=5&size=2&page=1").get("content")).hasSize(1);

        mockMvc.perform(get("/api/v1/venues/" + venue.getSlug() + "/reviews?rating=6")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/venues/" + venue.getSlug() + "/reviews?rating=0")).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/v1/venues/" + venue.getSlug() + "/reviews?size=51")).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Tong hop: diem trung binh va phan bo 1-5 sao chi tinh danh gia hien thi; san chua co danh gia thi 0")
    void summarisesVisibleReviews() throws Exception {
        JsonNode empty = summary();
        assertThat(empty.get("total").asLong()).isZero();
        assertThat(empty.get("average").asDouble()).isEqualTo(0.0);
        assertThat(empty.get("distribution")).hasSize(5);
        assertThat(empty.get("distribution").get("3").asLong()).isZero();

        int[] ratings = {5, 5, 4, 2};
        for (int i = 0; i < ratings.length; i++) write(loginAs(persistUser("sum-" + i, Role.PLAYER)), ratings[i], null, 10 + i);
        String hidden = write(loginAs(persistUser("sum-hidden", Role.PLAYER)), 1, null, 1);
        jdbc.update("UPDATE venue_reviews SET status = 'hidden' WHERE id = ?::uuid", hidden);

        JsonNode result = summary();
        assertThat(result.get("total").asLong()).isEqualTo(4);
        assertThat(result.get("average").asDouble()).isEqualTo(4.0);
        assertThat(result.get("distribution").get("5").asLong()).isEqualTo(2);
        assertThat(result.get("distribution").get("4").asLong()).isEqualTo(1);
        assertThat(result.get("distribution").get("2").asLong()).isEqualTo(1);
        assertThat(result.get("distribution").get("1").asLong()).isZero();
    }

    @Test
    @DisplayName("San khong ton tai tra 404 cho ca danh sach lan tong hop")
    void unknownVenue() throws Exception {
        mockMvc.perform(get("/api/v1/venues/" + UUID.randomUUID() + "/reviews")).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/venues/khong-co-san-nay/reviews/summary")).andExpect(status().isNotFound());
    }
}
