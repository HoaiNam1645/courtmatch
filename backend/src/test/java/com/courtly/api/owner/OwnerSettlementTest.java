package com.courtly.api.owner;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.CommissionSettlement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.23 - Xem cac ky doi soat hoa hong. */
class OwnerSettlementTest extends OwnerFinanceTestBase {

    private static final String PATH = "/api/v1/owner/settlements";

    @Test
    @DisplayName("Chua co ky doi soat: danh sach rong, du cac trang thai dem 0")
    void empty() throws Exception {
        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.statusCounts.open").value(0))
                .andExpect(jsonPath("$.statusCounts.paid").value(0))
                .andExpect(jsonPath("$.statusCounts.disputed").value(0));
    }

    @Test
    @DisplayName("Liet ke ky moi nhat truoc kem so tien con phai tra va so booking")
    void listsNewestFirst() throws Exception {
        LocalDate today = LocalDate.now();
        CommissionSettlement older = settlement(owner, today.minusDays(60), today.minusDays(31), "paid", "50000", "50000", "50000");
        CommissionSettlement newer = settlement(owner, today.minusDays(30), today.minusDays(1), "partial", "36000", "40000", "15000");
        BookingCommission commission = commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
        commission.setSettlement(newer);
        commissionRepository.saveAndFlush(commission);

        mockMvc.perform(get(PATH).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value(newer.getId().toString()))
                .andExpect(jsonPath("$.content[0].status").value("partial"))
                .andExpect(jsonPath("$.content[0].amountDue").value(40000))
                .andExpect(jsonPath("$.content[0].amountPaid").value(15000))
                .andExpect(jsonPath("$.content[0].remainingAmount").value(25000))
                .andExpect(jsonPath("$.content[0].bookingCount").value(1))
                .andExpect(jsonPath("$.content[1].id").value(older.getId().toString()))
                .andExpect(jsonPath("$.content[1].remainingAmount").value(0))
                .andExpect(jsonPath("$.content[1].bookingCount").value(0))
                .andExpect(jsonPath("$.statusCounts.paid").value(1))
                .andExpect(jsonPath("$.statusCounts.partial").value(1));
    }

    @Test
    @DisplayName("Loc theo trang thai va phan trang; so tien tra thua khong lam con phai tra am")
    void filterAndPaging() throws Exception {
        LocalDate today = LocalDate.now();
        settlement(owner, today.minusDays(90), today.minusDays(61), "paid", "1000", "1000", "1000");
        settlement(owner, today.minusDays(60), today.minusDays(31), "overdue", "2000", "2000", "0");
        settlement(owner, today.minusDays(30), today.minusDays(1), "paid", "3000", "3000", "3500");

        mockMvc.perform(get(PATH).param("status", "paid").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].remainingAmount").value(0))
                .andExpect(jsonPath("$.statusCounts.overdue").value(1));
        mockMvc.perform(get(PATH).param("size", "2").param("page", "1").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Chi tiet ky: thong tin ky va cac booking thuoc ky; ky khong co booking van xem duoc")
    void detail() throws Exception {
        LocalDate today = LocalDate.now();
        CommissionSettlement settlement = settlement(owner, today.minusDays(30), today.minusDays(1), "open", "36000", "36000", "0");
        for (int i = 0; i < 2; i++) {
            BookingCommission commission = commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
            commission.setSettlement(settlement);
            commissionRepository.saveAndFlush(commission);
        }
        CommissionSettlement emptyOne = settlement(owner, today.minusDays(60), today.minusDays(31), "open", "0", "0", "0");

        mockMvc.perform(get(PATH + "/" + settlement.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.id").value(settlement.getId().toString()))
                .andExpect(jsonPath("$.settlement.bookingCount").value(2))
                .andExpect(jsonPath("$.totalBookings").value(2))
                .andExpect(jsonPath("$.bookings", hasSize(2)))
                .andExpect(jsonPath("$.bookings[0].commissionAmount").value(18000))
                .andExpect(jsonPath("$.bookings[0].venueName").value("San Tai Chinh"));
        mockMvc.perform(get(PATH + "/" + emptyOne.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookings", hasSize(0)));
    }

    @Test
    @DisplayName("Tham so sai 400; ky cua chu khac 404; chu khac khong thay ky cua minh; nguoi choi 403; chua dang nhap 401")
    void validationAndOwnership() throws Exception {
        CommissionSettlement settlement = settlement(owner, LocalDate.now().minusDays(30), LocalDate.now().minusDays(1), "open", "1", "1", "0");
        mockMvc.perform(get(PATH).param("status", "xyz").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.status").exists());
        mockMvc.perform(get(PATH).param("size", "101").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH + "/not-a-uuid").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());

        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(get(PATH).header("Authorization", bearer(otherToken))).andExpect(jsonPath("$.content", hasSize(0)));
        mockMvc.perform(get(PATH + "/" + settlement.getId()).header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(ownerToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH).header("Authorization", bearer(ownerToken))).andExpect(status().isMethodNotAllowed());
    }
}
