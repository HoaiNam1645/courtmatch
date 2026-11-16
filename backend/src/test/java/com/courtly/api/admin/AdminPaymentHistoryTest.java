package com.courtly.api.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.CommissionSettlement;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.50 - Xem lich su thanh toan hoa hong cua chu san. */
class AdminPaymentHistoryTest extends AdminFinanceTestBase {

    private static final String PATH = "/api/v1/admin/finance/commission-payments";

    private JsonNode list(String... params) throws Exception {
        var request = get(PATH).header("Authorization", bearer(adminToken));
        for (int i = 0; i < params.length; i += 2) {
            request = request.param(params[i], params[i + 1]);
        }
        return objectMapper.readTree(mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private void history() throws Exception {
        CommissionSettlement settlement = settlement(owner, "30000", "10000", "partial");
        commission(owner, venue, "due", "30000", 3, settlement);
        commission(owner, venue, "due", "8000", 3, null);
        BookingCommission owed = commission(owner, venue, "settled", "0", 3, null);
        jdbc.update("UPDATE booking_commissions SET courtly_owed = 4000, voucher_discount = 4000 WHERE id = ?", owed.getId());
        jdbc.update("UPDATE court_owner_profiles SET commission_credit = 2500, restriction_level = 1 WHERE user_id = ?", owner.getId());
        UUID confirmedOne = report(owner, settlement, "10000");
        UUID confirmedTwo = report(owner, null, "5000");
        UUID rejected = report(owner, null, "9000");
        UUID pending = report(owner, settlement, "7000");
        jdbc.update("UPDATE commission_payment_reports SET status = 'confirmed', reviewed_by = ?, reviewed_at = now(), paid_on = current_date - 10, "
                + "courtly_offset_amount = 1000 WHERE id = ?", admin.getId(), confirmedOne);
        jdbc.update("UPDATE commission_payment_reports SET status = 'confirmed', reviewed_by = ?, reviewed_at = now(), recorded_by = ?, "
                + "paid_on = current_date - 3 WHERE id = ?", admin.getId(), admin.getId(), confirmedTwo);
        jdbc.update("UPDATE commission_payment_reports SET status = 'rejected', reviewed_by = ?, reviewed_at = now(), review_note = 'Sai so tien' WHERE id = ?",
                admin.getId(), rejected);
        assertThat(pending).isNotNull();
        // Du lieu cua chu san khac khong lan vao.
        commission(otherOwner, otherVenue, "due", "99000", 3, null);
        UUID other = report(otherOwner, null, "4000");
        jdbc.update("UPDATE commission_payment_reports SET status = 'confirmed' WHERE id = ?", other);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Loc theo chu san tra tong quan: da xac nhan, dang cho, cong no, so du, Courtly no, muc han che; khong loc thi khong co tong quan")
    void ownerSummary() throws Exception {
        history();
        JsonNode body = list("ownerId", owner.getId().toString());
        JsonNode summary = body.get("ownerSummary");
        assertThat(summary.get("ownerId").asText()).isEqualTo(owner.getId().toString());
        assertThat(summary.get("ownerName").asText()).isEqualTo(owner.getFullName());
        assertThat(summary.get("confirmedAmount").decimalValue()).isEqualByComparingTo("15000");
        assertThat(summary.get("pendingAmount").decimalValue()).isEqualByComparingTo("7000");
        assertThat(summary.get("outstanding").decimalValue()).isEqualByComparingTo("38000");
        assertThat(summary.get("credit").decimalValue()).isEqualByComparingTo("2500");
        assertThat(summary.get("owedByCourtly").decimalValue()).isEqualByComparingTo("4000");
        assertThat(summary.get("restrictionLevel").asInt()).isEqualTo(1);

        // Tong quan khong doi theo bo loc trang thai / ngay, con tong cua tap loc thi doi.
        JsonNode rejectedOnly = list("ownerId", owner.getId().toString(), "status", "rejected");
        assertThat(rejectedOnly.get("ownerSummary").get("confirmedAmount").decimalValue()).isEqualByComparingTo("15000");
        assertThat(rejectedOnly.get("totals").get("amount").decimalValue()).isEqualByComparingTo("9000");

        JsonNode everyone = list();
        assertThat(everyone.has("ownerSummary")).isFalse();
    }

    @Test
    @DisplayName("Lich su cua chu san: day du ban ghi, cho xac nhan len dau, ghi nhan boi admin / bu tru / ly do tu choi, loc theo ngay va trang thai")
    void historyRows() throws Exception {
        history();
        String ownerId = owner.getId().toString();
        JsonNode all = list("ownerId", ownerId);
        assertThat(all.get("content")).hasSize(4);
        assertThat(all.get("content").get(0).get("status").asText()).isEqualTo("pending_review");
        assertThat(all.get("totals").get("amount").decimalValue()).isEqualByComparingTo("31000");
        assertThat(all.get("statusCounts").get("confirmed").asLong()).isEqualTo(2);
        assertThat(all.get("statusCounts").get("rejected").asLong()).isEqualTo(1);

        JsonNode confirmed = list("ownerId", ownerId, "status", "confirmed");
        assertThat(confirmed.get("content")).hasSize(2);
        JsonNode recorded = null;
        JsonNode offsetOne = null;
        for (JsonNode row : confirmed.get("content")) {
            if (row.get("recordedByAdmin").asBoolean()) {
                recorded = row;
            }
            if (row.get("courtlyOffsetAmount").decimalValue().signum() > 0) {
                offsetOne = row;
            }
        }
        assertThat(recorded).isNotNull();
        assertThat(recorded.get("settlement")).isNull();
        assertThat(offsetOne).isNotNull();
        assertThat(offsetOne.get("courtlyOffsetAmount").decimalValue()).isEqualByComparingTo("1000");
        assertThat(offsetOne.get("settlement").get("status").asText()).isEqualTo("partial");

        JsonNode rejected = list("ownerId", ownerId, "status", "rejected");
        assertThat(rejected.get("content").get(0).get("reviewNote").asText()).isEqualTo("Sai so tien");
        assertThat(rejected.get("content").get(0).get("reviewedByName").asText()).isEqualTo(admin.getFullName());

        String eightDaysAgo = java.time.LocalDate.now(VN).minusDays(8).toString();
        assertThat(list("ownerId", ownerId, "status", "confirmed", "from", eightDaysAgo).get("content")).hasSize(1);
        assertThat(list("ownerId", ownerId, "status", "confirmed", "to", eightDaysAgo).get("content")).hasSize(1);
    }

    @Test
    @DisplayName("Chu san chua co thanh toan nao: tong quan tinh dung, danh sach rong")
    void ownerWithoutPayments() throws Exception {
        commission(otherOwner, otherVenue, "due", "12000", 3, null);
        JsonNode body = list("ownerId", otherOwner.getId().toString());
        assertThat(body.get("content")).isEmpty();
        assertThat(body.get("totals").get("count").asLong()).isZero();
        JsonNode summary = body.get("ownerSummary");
        assertThat(summary.get("confirmedAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(summary.get("pendingAmount").decimalValue()).isEqualByComparingTo("0");
        assertThat(summary.get("outstanding").decimalValue()).isEqualByComparingTo("12000");
        assertThat(summary.get("restrictionLevel").asInt()).isZero();
    }
}
