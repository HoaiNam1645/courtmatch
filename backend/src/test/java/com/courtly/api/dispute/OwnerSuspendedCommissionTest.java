package com.courtly.api.dispute;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.BookingStatus;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.97 - hoa hong dang tam hoan do tranh chap. */
class OwnerSuspendedCommissionTest extends BookingDisputeTestBase {

    private JsonNode call(String token, int expected) throws Exception {
        var request = get("/api/v1/owner/commissions/suspended");
        if (token != null) request = request.header("Authorization", bearer(token));
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse().getContentAsString());
    }

    private UUID commission(UUID bookingId, String status, int amount) {
        jdbc.update("""
                INSERT INTO booking_commissions (booking_id, owner_id, gross_amount, fee_type, fee_value, commission_amount, status, due_at)
                VALUES (?, ?, 180000, 'fixed', ?, ?, ?, now() + interval '7 days')
                """, bookingId, owner.getId(), amount, amount, status);
        entityManager.clear();
        return bookingId;
    }

    @Test
    @DisplayName("Rong khi khong co hoa hong tam hoan; chi tra khoan suspended cua chinh chu san, kem tong va bao cao dang xu ly")
    void listsOnlyOwnSuspended() throws Exception {
        JsonNode empty = call(ownerToken, 200);
        assertThat(empty.get("items")).isEmpty();
        assertThat(empty.get("totalAmount").decimalValue()).isEqualByComparingTo("0");

        UUID held = booking(playerToken, BookingStatus.COMPLETED, 1);
        UUID plain = booking(playerToken, BookingStatus.COMPLETED, 2);
        UUID other = booking(playerToken, BookingStatus.COMPLETED, 3);
        String disputeId = dispute(playerToken, held, 201, "venue_closed", "San dong cua khi toi den luc 18h").get("id").asText();
        commission(held, "suspended", 20000);
        commission(plain, "due", 15000);
        commission(other, "suspended", 5000);
        jdbc.update("UPDATE booking_commissions SET owner_id = ? WHERE booking_id = ?", persistOwner("other-suspended").getId(), other);
        entityManager.clear();

        JsonNode result = call(ownerToken, 200);
        assertThat(result.get("items")).hasSize(1);
        JsonNode item = result.get("items").get(0);
        assertThat(item.get("bookingId").asText()).isEqualTo(held.toString());
        assertThat(item.get("commissionAmount").decimalValue()).isEqualByComparingTo("20000");
        assertThat(item.get("disputeId").asText()).isEqualTo(disputeId);
        assertThat(item.get("suspendedAt").asText()).isNotBlank();
        assertThat(result.get("totalAmount").decimalValue()).isEqualByComparingTo("20000");

        call(null, 401);
        call(playerToken, 403);
    }
}
