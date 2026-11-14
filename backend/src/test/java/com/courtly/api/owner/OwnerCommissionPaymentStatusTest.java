package com.courtly.api.owner;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.finance.CommissionPaymentReport;
import com.courtly.domain.finance.CommissionSettlement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 2.3.25 - Xem trang thai thanh toan cong no. */
class OwnerCommissionPaymentStatusTest extends OwnerFinanceTestBase {

    private static final String PATH = "/api/v1/owner/commission-payments";

    @Test
    @DisplayName("Chua gui lan nao: moi so bang 0, khong co lan gui gan nhat")
    void nothingYet() throws Exception {
        mockMvc.perform(get(PATH + "/status").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(0))
                .andExpect(jsonPath("$.pendingAmount").value(0))
                .andExpect(jsonPath("$.pendingCount").value(0))
                .andExpect(jsonPath("$.confirmedAmount").value(0))
                .andExpect(jsonPath("$.rejectedCount").value(0))
                .andExpect(jsonPath("$.lastPayment").doesNotExist())
                .andExpect(jsonPath("$.unpaidSettlements", hasSize(0)));
    }

    @Test
    @DisplayName("Tong hop theo trang thai: dang cho, da xac nhan, bi tu choi; lan gui gan nhat")
    void totalsByStatus() throws Exception {
        commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
        LocalDate today = LocalDate.now();
        report(owner, null, "5000", "pending_review", "P1", today, Instant.now().minusSeconds(300));
        report(owner, null, "7000", "pending_review", "P2", today, Instant.now().minusSeconds(200));
        report(owner, null, "20000", "confirmed", "C1", today.minusDays(5), Instant.now().minusSeconds(900));
        report(owner, null, "3000", "rejected", "R1", today.minusDays(2), Instant.now().minusSeconds(600));
        CommissionPaymentReport latest = report(owner, null, "1000", "rejected", "R2", today, Instant.now());

        mockMvc.perform(get(PATH + "/status").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalOutstanding").value(18000))
                .andExpect(jsonPath("$.pendingAmount").value(12000))
                .andExpect(jsonPath("$.pendingCount").value(2))
                .andExpect(jsonPath("$.confirmedAmount").value(20000))
                .andExpect(jsonPath("$.confirmedCount").value(1))
                .andExpect(jsonPath("$.rejectedCount").value(2))
                .andExpect(jsonPath("$.lastPayment.id").value(latest.getId().toString()))
                .andExpect(jsonPath("$.lastPayment.status").value("rejected"));
    }

    @Test
    @DisplayName("Ky chua thanh toan xong: con phai tra, dang cho xac nhan va so con co the gui; ky da tra du khong hien")
    void unpaidSettlements() throws Exception {
        LocalDate today = LocalDate.now();
        CommissionSettlement paid = settlement(owner, today.minusDays(90), today.minusDays(61), "paid", "100", "100", "100");
        CommissionSettlement partial = settlement(owner, today.minusDays(60), today.minusDays(31), "partial", "40000", "40000", "10000");
        CommissionSettlement pending = settlement(owner, today.minusDays(30), today.minusDays(1), "pending_review", "50000", "50000", "0");
        report(owner, pending, "50000", "pending_review", "P1", today, Instant.now());
        report(owner, partial, "5000", "pending_review", "P2", today, Instant.now());
        report(owner, partial, "9000", "rejected", "R1", today, Instant.now());

        mockMvc.perform(get(PATH + "/status").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.unpaidSettlements", hasSize(2)))
                .andExpect(jsonPath("$.unpaidSettlements[0].id").value(pending.getId().toString()))
                .andExpect(jsonPath("$.unpaidSettlements[0].remainingAmount").value(50000))
                .andExpect(jsonPath("$.unpaidSettlements[0].pendingAmount").value(50000))
                .andExpect(jsonPath("$.unpaidSettlements[0].payableAmount").value(0))
                .andExpect(jsonPath("$.unpaidSettlements[1].id").value(partial.getId().toString()))
                .andExpect(jsonPath("$.unpaidSettlements[1].remainingAmount").value(30000))
                .andExpect(jsonPath("$.unpaidSettlements[1].pendingAmount").value(5000))
                .andExpect(jsonPath("$.unpaidSettlements[1].payableAmount").value(25000));
    }

    @Test
    @DisplayName("Chi tiet lan gui: trang thai, ly do tu choi va ky doi soat")
    void detail() throws Exception {
        LocalDate today = LocalDate.now();
        CommissionSettlement settlement = settlement(owner, today.minusDays(30), today.minusDays(1), "open", "9000", "9000", "0");
        CommissionPaymentReport rejected = report(owner, settlement, "9000", "rejected", "R1", today, Instant.now());
        rejected.setReviewNote("Khong thay giao dich tren sao ke");
        rejected.setReviewedAt(Instant.now());
        paymentReportRepository.saveAndFlush(rejected);

        mockMvc.perform(get(PATH + "/" + rejected.getId()).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"))
                .andExpect(jsonPath("$.reviewNote").value("Khong thay giao dich tren sao ke"))
                .andExpect(jsonPath("$.settlementId").value(settlement.getId().toString()))
                .andExpect(jsonPath("$.referenceCode").value("R1"));
    }

    @Test
    @DisplayName("Chi tiet cua chu khac / khong ton tai 404; id sai 400; chu khac thay so 0; nguoi choi 403; chua dang nhap 401")
    void accessControl() throws Exception {
        CommissionPaymentReport mine = report(owner, null, "1000", "pending_review", "P1", LocalDate.now(), Instant.now());
        String otherToken = loginAs(persistOwner("other"));
        mockMvc.perform(get(PATH + "/" + mine.getId()).header("Authorization", bearer(otherToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(ownerToken))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/abc").header("Authorization", bearer(ownerToken))).andExpect(status().isBadRequest());
        mockMvc.perform(get(PATH + "/status").header("Authorization", bearer(otherToken)))
                .andExpect(jsonPath("$.pendingAmount").value(0)).andExpect(jsonPath("$.lastPayment").doesNotExist());
        mockMvc.perform(get(PATH + "/status").header("Authorization", bearer(playerToken))).andExpect(status().isForbidden());
        mockMvc.perform(get(PATH + "/status")).andExpect(status().isUnauthorized());
        mockMvc.perform(post(PATH + "/status").header("Authorization", bearer(ownerToken))).andExpect(status().is4xxClientError());
    }
}
