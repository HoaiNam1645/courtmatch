package com.courtly.api.owner;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.finance.CommissionSettlement;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.26 - Xem lich su thanh toan hoa hong. */
class OwnerCommissionPaymentHistoryTest extends OwnerFinanceTestBase {

    private static final String PATH = "/api/v1/owner/commission-payments";

    @Test
    @DisplayName("Chua co lich su: danh sach rong, du trang thai dem 0")
    void empty() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalAmount").value(0))
                .andExpect(jsonPath("$.statusCounts.pending_review").value(0))
                .andExpect(jsonPath("$.statusCounts.confirmed").value(0))
                .andExpect(jsonPath("$.statusCounts.rejected").value(0));
    }

    @Test
    @DisplayName("Lich su moi nhat truoc, kem ky doi soat, trang thai va ly do tu choi")
    void newestFirst() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        CommissionSettlement settlement = settlement(owner, today.minusDays(30), today.minusDays(1), "open", "9000", "9000", "0");
        report(owner, null, "5000", "confirmed", "OLD", today.minusDays(10), Instant.now().minusSeconds(3000));
        var rejected = report(owner, settlement, "9000", "rejected", "MID", today.minusDays(2), Instant.now().minusSeconds(2000));
        rejected.setReviewNote("Sai so tien");
        paymentReportRepository.saveAndFlush(rejected);
        report(owner, null, "1000", "pending_review", "NEW", today, Instant.now().minusSeconds(1000));

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(3)))
                .andExpect(jsonPath("$.content[0].referenceCode").value("NEW"))
                .andExpect(jsonPath("$.content[1].referenceCode").value("MID"))
                .andExpect(jsonPath("$.content[1].reviewNote").value("Sai so tien"))
                .andExpect(jsonPath("$.content[1].periodFrom").value(settlement.getPeriodFrom().toString()))
                .andExpect(jsonPath("$.content[2].referenceCode").value("OLD"))
                .andExpect(jsonPath("$.totalAmount").value(15000))
                .andExpect(jsonPath("$.statusCounts.confirmed").value(1))
                .andExpect(jsonPath("$.statusCounts.rejected").value(1))
                .andExpect(jsonPath("$.statusCounts.pending_review").value(1));
    }

    @Test
    @DisplayName("Loc theo trang thai va khoang ngay thanh toan; so luong theo trang thai khong doi theo bo loc")
    void filters() throws Exception {
        LocalDate today = LocalDate.now(ZONE);
        report(owner, null, "5000", "confirmed", "A", today.minusDays(10), Instant.now().minusSeconds(300));
        report(owner, null, "2000", "confirmed", "B", today.minusDays(3), Instant.now().minusSeconds(200));
        report(owner, null, "700", "rejected", "C", today.minusDays(3), Instant.now().minusSeconds(100));

        mockMvc.perform(get(PATH).param("status", "confirmed").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalAmount").value(7000))
                .andExpect(jsonPath("$.statusCounts.rejected").value(1));
        mockMvc.perform(get(PATH).param("from", today.minusDays(5).toString()).param("to", today.toString())
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalAmount").value(2700));
        mockMvc.perform(get(PATH).param("from", today.minusDays(3).toString()).param("to", today.minusDays(3).toString())
                        .param("status", "rejected").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].referenceCode").value("C"));
        mockMvc.perform(get(PATH).param("size", "2").param("page", "1").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.totalAmount").value(7700));
    }

    @Test
    @DisplayName("Tham so sai 400; chu san khac khong thay lich su; nguoi choi 403; chua dang nhap 401")
    void validationAndOwnership() throws Exception {
        report(owner, null, "5000", "confirmed", "A", LocalDate.now(ZONE), Instant.now());
        mockMvc.perform(get(PATH).param("status", "xyz").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        mockMvc.perform(get(PATH).param("from", "2026-11-10").param("to", "2026-11-01").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.to").exists());
        mockMvc.perform(get(PATH).param("from", "10-11-2026").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("size", "0").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH).param("page", "-1").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());

        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(get(PATH).header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.content", hasSize(0))).andExpect(jsonPath("$.totalAmount").value(0));
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH).header("Authorization", bearer(ownerToken)).contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
    }
}
