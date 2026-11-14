package com.courtly.api.owner;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.finance.CommissionPaymentReport;
import com.courtly.domain.finance.CommissionPaymentReportRepository;
import com.courtly.domain.finance.CommissionSettlement;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.24 - Gui thong tin thanh toan cong no cho Courtly. */
class OwnerCommissionPaymentSubmitTest extends OwnerFinanceTestBase {

    private static final String PATH = "/api/v1/owner/commission-payments";

    @Autowired private CommissionPaymentReportRepository reportRepository;

    private String body(String settlementId, String amount, String paidOn, String reference, String proof) {
        return """
                {"settlementId": %s, "amount": %s, "paidOn": %s, "referenceCode": %s, "proofUrl": %s, "note": "Chuyen khoan"}
                """.formatted(settlementId == null ? "null" : "\"" + settlementId + "\"", amount,
                paidOn == null ? "null" : "\"" + paidOn + "\"", reference == null ? "null" : "\"" + reference + "\"",
                proof == null ? "null" : "\"" + proof + "\"");
    }

    private org.springframework.test.web.servlet.ResultActions submit(String token, String json) throws Exception {
        return mockMvc.perform(post(PATH).header("Authorization", bearer(token)).contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String today() {
        return LocalDate.now(ZONE).toString();
    }

    @Test
    @DisplayName("Tra chung vao cong no: tao bao cao cho xac nhan, cong no chua giam; khong vuot so con phai tra")
    void generalPayment() throws Exception {
        commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
        commission(court, "due", Instant.now().minus(1, ChronoUnit.DAYS));   // tong cong no 36.000

        submit(ownerToken, body(null, "20000", today(), "FT001", "https://example.com/bill.png"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending_review"))
                .andExpect(jsonPath("$.amount").value(20000))
                .andExpect(jsonPath("$.referenceCode").value("FT001"))
                .andExpect(jsonPath("$.proofUrl").value("https://example.com/bill.png"))
                .andExpect(jsonPath("$.settlementId").doesNotExist());
        mockMvc.perform(get("/api/v1/owner/commission-debt").header("Authorization", bearer(ownerToken)))
                .andExpect(jsonPath("$.totalOutstanding").value(36000));

        submit(ownerToken, body(null, "20000", today(), "FT002", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.amount").exists());
        submit(ownerToken, body(null, "16000", today(), "FT003", null)).andExpect(status().isCreated());
        submit(ownerToken, body(null, "1000", today(), "FT004", null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMISSION_PAYMENT_INVALID"));
    }

    @Test
    @DisplayName("Khong co cong no thi khong gui duoc")
    void noDebt() throws Exception {
        submit(ownerToken, body(null, "1000", today(), "FT001", null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("COMMISSION_PAYMENT_INVALID"));
    }

    @Test
    @DisplayName("Tra theo ky doi soat: ky chuyen sang cho xac nhan; so tien toi da la phan con lai tru phan dang cho")
    void settlementPayment() throws Exception {
        LocalDate day = LocalDate.now();
        CommissionSettlement open = settlement(owner, day.minusDays(30), day.minusDays(1), "overdue", "40000", "40000", "10000");

        submit(ownerToken, body(open.getId().toString(), "10000", today(), "FT001", null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.settlementId").value(open.getId().toString()))
                .andExpect(jsonPath("$.periodFrom").value(open.getPeriodFrom().toString()));
        CommissionSettlement reloaded = settlementRepository.findById(open.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("pending_review", reloaded.getStatus());
        org.junit.jupiter.api.Assertions.assertEquals("FT001", reloaded.getPaymentReference());
        org.junit.jupiter.api.Assertions.assertNotNull(reloaded.getOwnerSubmittedAt());

        submit(ownerToken, body(open.getId().toString(), "20001", today(), "FT002", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.amount").exists());
        submit(ownerToken, body(open.getId().toString(), "20000", today(), "FT003", null)).andExpect(status().isCreated());
        submit(ownerToken, body(open.getId().toString(), "1000", today(), "FT004", null)).andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Ky da thanh toan hoac dang tranh chap: 409; ky khong ton tai hoac cua chu khac: 404")
    void settlementState() throws Exception {
        LocalDate day = LocalDate.now();
        CommissionSettlement paid = settlement(owner, day.minusDays(90), day.minusDays(61), "paid", "100", "100", "100");
        CommissionSettlement disputed = settlement(owner, day.minusDays(60), day.minusDays(31), "disputed", "50000", "50000", "0");
        CommissionSettlement others = settlement(persistOwner("other"), day.minusDays(30), day.minusDays(1), "open", "50000", "50000", "0");

        submit(ownerToken, body(paid.getId().toString(), "50", today(), "FT001", null)).andExpect(status().isConflict());
        submit(ownerToken, body(disputed.getId().toString(), "1000", today(), "FT002", null)).andExpect(status().isConflict());
        submit(ownerToken, body(others.getId().toString(), "1000", today(), "FT003", null)).andExpect(status().isNotFound());
        submit(ownerToken, body(UUID.randomUUID().toString(), "1000", today(), "FT004", null)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Kiem tra du lieu: thieu truong, so tien sai, ngay tuong lai / qua xa, ma ngan, link bang chung khong hop le")
    void validation() throws Exception {
        commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
        submit(ownerToken, "{}").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists())
                .andExpect(jsonPath("$.fieldErrors.paidOn").exists())
                .andExpect(jsonPath("$.fieldErrors.referenceCode").exists());
        submit(ownerToken, body(null, "0", today(), "FT001", null)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.amount").exists());
        submit(ownerToken, body(null, "-5", today(), "FT001", null)).andExpect(status().isBadRequest());
        submit(ownerToken, body(null, "1000.123", today(), "FT001", null)).andExpect(status().isBadRequest());
        submit(ownerToken, body(null, "1000", LocalDate.now(ZONE).plusDays(1).toString(), "FT001", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.paidOn").exists());
        submit(ownerToken, body(null, "1000", LocalDate.now(ZONE).minusDays(400).toString(), "FT001", null))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.paidOn").exists());
        submit(ownerToken, body(null, "1000", today(), "AB", null)).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.referenceCode").exists());
        submit(ownerToken, body(null, "1000", today(), "FT001", "javascript:alert(1)")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors.proofUrl").exists());
        submit(ownerToken, body(null, "1000", today(), "FT001", "ftp://x.com/a.png")).andExpect(status().isBadRequest());
        submit(ownerToken, "{\"amount\": \"abc\"}").andExpect(status().isBadRequest());
        submit(ownerToken, "{\"settlementId\": \"x\", \"amount\": 1}").andExpect(status().isBadRequest());
        org.junit.jupiter.api.Assertions.assertTrue(reportRepository.findAll().stream()
                .noneMatch(report -> report.getOwner().getId().equals(owner.getId())), "Du lieu sai khong duoc tao bao cao");
    }

    @Test
    @DisplayName("Ma tham chieu khong duoc dung lai (khong phan biet hoa thuong); bao cao bi tu choi thi dung lai duoc")
    void duplicateReference() throws Exception {
        commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
        submit(ownerToken, body(null, "1000", today(), "Ft-100", null)).andExpect(status().isCreated());
        submit(ownerToken, body(null, "1000", today(), "FT-100", null))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.fieldErrors.referenceCode").exists());

        CommissionPaymentReport first = reportRepository.findAll().stream()
                .filter(report -> report.getOwner().getId().equals(owner.getId())).findFirst().orElseThrow();
        first.setStatus("rejected");
        reportRepository.saveAndFlush(first);
        submit(ownerToken, body(null, "1000", today(), "FT-100", null)).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Chu san khac co the dung cung ma tham chieu; nguoi choi 403; chua dang nhap 401")
    void accessControl() throws Exception {
        commission(court, "due", Instant.now().plus(3, ChronoUnit.DAYS));
        submit(ownerToken, body(null, "1000", today(), "SAME-1", null)).andExpect(status().isCreated());

        com.courtly.domain.account.User other = persistOwner("other");
        String otherToken = loginAs(other);
        // Chu san khac khong co cong no nen khong gui duoc, bat ke ma tham chieu.
        submit(otherToken, body(null, "1000", today(), "SAME-1", null)).andExpect(status().isConflict());
        submit(playerToken, body(null, "1000", today(), "X-1", null)).andExpect(status().isForbidden());
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
    }
}
