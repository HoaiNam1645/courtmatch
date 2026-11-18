package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.103 - thong ke bao cao theo san; loc danh sach bao cao theo san. */
class AdminDisputeVenueStatsTest extends DisputeFinanceTestBase {

    private JsonNode stats(String query, String token, int expected) throws Exception {
        return objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/booking-disputes/venue-stats" + query).header("Authorization", bearer(token)))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private JsonNode mine(JsonNode result) {
        for (JsonNode item : result.get("items")) {
            if (item.get("venueId").asText().equals(venue.getId().toString())) return item;
        }
        return null;
    }

    private String report(int endedDaysAgo, String refund) throws Exception {
        UUID bookingId = completed(endedDaysAgo);
        if (refund != null) payment(bookingId, "confirmed");
        return dispute(playerToken, bookingId, 201, "extra_charge", "Chu san thu them tien thue vot ngoai thoa thuan", refund).get("id").asText();
    }

    @Test
    @DisplayName("Dem bao cao theo trang thai, xu thua, ty le, tien hoan, tu xu ly; sap xep, tim, loc ngay; chi admin")
    void aggregates() throws Exception {
        String lost = report(1, "\"refundRequested\":true,\"refundAmount\":60000");
        answer(lost);
        adminPost("/api/v1/admin/booking-disputes/" + lost + "/refund-full", "{\"note\":\"Camera cho thay san dong cua\"}", 200);
        String rejected = report(2, null);
        answer(rejected);
        adminPost("/api/v1/admin/booking-disputes/" + rejected + "/reject", "{\"note\":\"Khong du bang chung\"}", 200);
        String auto = report(3, null);
        jdbc.update("UPDATE booking_disputes SET status = 'resolved', resolution = 'owner_no_response', handled_at = now(), handled_by = NULL WHERE id = ?", UUID.fromString(auto));
        report(4, null);
        entityManager.clear();

        JsonNode item = mine(stats("", adminToken, 200));
        assertThat(item).isNotNull();
        assertThat(item.get("total").asLong()).isEqualTo(4);
        assertThat(item.get("lost").asLong()).isEqualTo(2);
        assertThat(item.get("rejected").asLong()).isEqualTo(1);
        assertThat(item.get("awaiting").asLong()).isEqualTo(1);
        assertThat(item.get("autoResolved").asLong()).isEqualTo(1);
        assertThat(item.get("lostRate").asDouble()).isEqualTo(50.0);
        assertThat(item.get("refundAmount").decimalValue()).isEqualByComparingTo("60000");
        assertThat(item.get("lostInWindow").asLong()).isEqualTo(2);
        assertThat(stats("", adminToken, 200).get("suspendThreshold").asInt()).isEqualTo(3);

        assertThat(mine(stats("?q=" + venue.getName().substring(0, 4), adminToken, 200))).isNotNull();
        assertThat(mine(stats("?q=khong-co-san-nay", adminToken, 200))).isNull();
        assertThat(mine(stats("?from=2020-01-01&to=2020-01-31", adminToken, 200))).isNull();
        assertThat(mine(stats("?sort=rate", adminToken, 200))).isNotNull();
        stats("?sort=bad", adminToken, 400);
        stats("?from=2026-02-01&to=2026-01-01", adminToken, 400);
        stats("", ownerToken, 403);
        stats("", playerToken, 403);

        JsonNode filtered = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/booking-disputes?status=all&venueId=" + venue.getId()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(filtered.get("items")).hasSize(4);
        JsonNode none = objectMapper.readTree(mockMvc.perform(get("/api/v1/admin/booking-disputes?status=all&venueId=" + UUID.randomUUID()).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(none.get("items")).isEmpty();
    }
}
