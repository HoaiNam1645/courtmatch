package com.courtly.api.owner;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.booking.Booking;
import com.courtly.domain.finance.BookingCommission;
import com.courtly.domain.finance.CommissionSettlement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.22 - Xem lich su doi soat booking. */
class OwnerBookingReconciliationTest extends OwnerFinanceTestBase {

    private String path(UUID bookingId) {
        return "/api/v1/owner/bookings/" + bookingId + "/reconciliation";
    }

    @Test
    @DisplayName("Hoa hong chua doi soat: chi co su kien ghi nhan, khong co ky doi soat")
    void recordedOnly() throws Exception {
        BookingCommission commission = commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
        Booking booking = commission.getBooking();
        mockMvc.perform(get(path(booking.getId())).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bookingCode").value(booking.getBookingCode()))
                .andExpect(jsonPath("$.commission.status").value("due"))
                .andExpect(jsonPath("$.commission.commissionAmount").value(18000))
                .andExpect(jsonPath("$.commission.overdue").value(false))
                .andExpect(jsonPath("$.settlement").doesNotExist())
                .andExpect(jsonPath("$.events", hasSize(1)))
                .andExpect(jsonPath("$.events[0].type").value("recorded"))
                .andExpect(jsonPath("$.events[0].note").value("Hoa hong thu nghiem"));
    }

    @Test
    @DisplayName("Da gan ky va da doi soat: du cac su kien theo thu tu thoi gian")
    void settledInPeriod() throws Exception {
        BookingCommission commission = commission(court, "settled", Instant.now().minus(2, ChronoUnit.DAYS));
        CommissionSettlement settlement = settlement(owner, LocalDate.now().minusDays(30), LocalDate.now().minusDays(1),
                "paid", "18000", "18000", "18000");
        commission.setSettlement(settlement);
        commission.setSettledAt(Instant.now().plusSeconds(60));
        commissionRepository.saveAndFlush(commission);

        mockMvc.perform(get(path(commission.getBooking().getId())).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.settlement.status").value("paid"))
                .andExpect(jsonPath("$.settlement.periodFrom").value(settlement.getPeriodFrom().toString()))
                .andExpect(jsonPath("$.events", hasSize(3)))
                .andExpect(jsonPath("$.events[0].type").value("recorded"))
                .andExpect(jsonPath("$.events[1].type").value("assigned_to_settlement"))
                .andExpect(jsonPath("$.events[2].type").value("settled"));
    }

    @Test
    @DisplayName("Tam hoan, dieu chinh, mien va qua han duoc the hien")
    void otherStatuses() throws Exception {
        BookingCommission suspended = commission(court, "suspended", Instant.now().minus(1, ChronoUnit.DAYS));
        BookingCommission adjusted = commission(court, "adjusted", Instant.now().minus(2, ChronoUnit.DAYS));
        BookingCommission waived = commission(court, "waived", Instant.now().plus(2, ChronoUnit.DAYS));

        mockMvc.perform(get(path(suspended.getBooking().getId())).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.events[1].type").value("suspended"))
                .andExpect(jsonPath("$.commission.overdue").value(false));
        mockMvc.perform(get(path(adjusted.getBooking().getId())).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.events[1].type").value("adjusted"))
                .andExpect(jsonPath("$.commission.overdue").value(true));
        mockMvc.perform(get(path(waived.getBooking().getId())).header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.events[1].type").value("waived"));
    }

    @Test
    @DisplayName("Booking chua co hoa hong: commission null, khong co su kien")
    void noCommissionYet() throws Exception {
        String bookingId = createBooking(playerToken, court, nextMonday(), java.time.LocalTime.of(6, 0));
        mockMvc.perform(get(path(UUID.fromString(bookingId))).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commission").doesNotExist())
                .andExpect(jsonPath("$.settlement").doesNotExist())
                .andExpect(jsonPath("$.events", hasSize(0)));
    }

    @Test
    @DisplayName("Booking cua chu khac 404; khong ton tai 404; id sai dinh dang 400; nguoi choi 403; chua dang nhap 401")
    void accessControl() throws Exception {
        BookingCommission commission = commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
        UUID bookingId = commission.getBooking().getId();
        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(get(path(bookingId)).header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(path(UUID.randomUUID())).header("Authorization", bearer(ownerToken))).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/owner/bookings/abc/reconciliation").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(path(bookingId)).header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(path(bookingId))).andExpect(status().isUnauthorized());
        mockMvc.perform(post(path(bookingId)).header("Authorization", bearer(ownerToken))).andExpect(status().isMethodNotAllowed());
    }
}
