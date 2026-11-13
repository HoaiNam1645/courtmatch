package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.payment.Payment;
import com.courtly.domain.payment.PaymentRepository;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Venue;
import java.time.LocalTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.2 - Xem va cap nhat ho so chu san duy nhat. */
class OwnerProfileControllerTest extends OwnerApiTestBase {

    private static final String PROFILE = "/api/v1/owner/profile";

    @Autowired private AuditLogRepository auditLogRepository;
    @Autowired private PaymentRepository paymentRepository;

    private User owner;
    private String ownerToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-profile");
        owner.setPhone("09" + String.valueOf(Math.abs(UUID.randomUUID().getMostSignificantBits())).substring(0, 8));
        userRepository.saveAndFlush(owner);
        ownerToken = loginAs(owner);
    }

    @Test
    @DisplayName("Xem ho so: co thong tin lien he, ngan hang, trang thai xac minh; khong lo du lieu nhay cam")
    void getProfile() throws Exception {
        mockMvc.perform(get(PROFILE).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value(owner.getFullName()))
                .andExpect(jsonPath("$.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.businessName").value("Ho kinh doanh owner-profile"))
                .andExpect(jsonPath("$.bankAccountNo").value("0359111001"))
                .andExpect(jsonPath("$.verificationStatus").value("verified"))
                .andExpect(jsonPath("$.identityNumber").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("Cap nhat ho so: chuan hoa so dien thoai, in hoa ten chu TK, ghi audit; khong doi email va trang thai xac minh")
    void updateProfile() throws Exception {
        mockMvc.perform(put(PROFILE)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Tran Van Chu","phone":"+84 935 111 222",
                                 "businessName":"Cong ty San Hai Chau","taxCode":"0401234567",
                                 "bankName":"Vietcombank","bankAccountNo":"0041 0000 1234",
                                 "bankAccountName":"tran van chu",
                                 "email":"doi-email@example.com","verificationStatus":"pending"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Tran Van Chu"))
                .andExpect(jsonPath("$.phone").value("0935111222"))
                .andExpect(jsonPath("$.bankAccountNo").value("004100001234"))
                .andExpect(jsonPath("$.bankAccountName").value("TRAN VAN CHU"))
                .andExpect(jsonPath("$.email").value(owner.getEmail()))
                .andExpect(jsonPath("$.verificationStatus").value("verified"));

        CourtOwnerProfile saved = ownerProfileRepository.findById(owner.getId()).orElseThrow();
        assertThat(saved.getBankName()).isEqualTo("Vietcombank");
        assertThat(saved.getTaxCode()).isEqualTo("0401234567");
        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("owner.profile.update");
            assertThat(log.getEntityId()).isEqualTo(owner.getId());
            assertThat(log.getOldData()).containsEntry("bankName", "MB Bank");
            assertThat(log.getNewData()).containsEntry("bankName", "Vietcombank");
        });
    }

    @Test
    @DisplayName("Cap nhat ho so: bat buoc cac truong, sai dinh dang so tai khoan / ma so thue / so dien thoai")
    void updateValidation() throws Exception {
        mockMvc.perform(put(PROFILE)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"  ","phone":"","businessName":"","bankName":"",
                                 "bankAccountNo":"","bankAccountName":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.fullName").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.businessName").exists())
                .andExpect(jsonPath("$.fieldErrors.bankName").exists())
                .andExpect(jsonPath("$.fieldErrors.bankAccountNo").exists())
                .andExpect(jsonPath("$.fieldErrors.bankAccountName").exists());

        mockMvc.perform(put(PROFILE)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("0935111222").replace("\"0359111001\"", "\"12AB\"")
                                .replace("\"taxCode\":\"\"", "\"taxCode\":\"123\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.bankAccountNo").exists())
                .andExpect(jsonPath("$.fieldErrors.taxCode").exists());

        // Qua lop cu phap nhung khong phai so di dong Viet Nam.
        mockMvc.perform(put(PROFILE)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("0123456789")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());
    }

    @Test
    @DisplayName("So dien thoai da thuoc tai khoan khac thi tra 409")
    void duplicatePhone() throws Exception {
        User other = persistUser("other", Role.PLAYER);
        other.setPhone("0935999888");
        userRepository.saveAndFlush(other);

        mockMvc.perform(put(PROFILE)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("0935 999 888")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PHONE_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.fieldErrors.phone").exists());

        // Giu nguyen so cua chinh minh thi khong bi coi la trung.
        mockMvc.perform(put(PROFILE)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(owner.getPhone())))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Tai khoan ngan hang moi chi ap dung cho booking duoc chap nhan sau khi luu")
    void bankChangeOnlyAffectsNewPayments() throws Exception {
        Venue venue = persistVenue(owner, "San Ho So Chu San");
        User player = persistUser("player", Role.PLAYER);
        String playerToken = loginAs(player);

        String firstBooking = createBooking(playerToken, venue.getCourts().getFirst(), nextMonday(), LocalTime.of(8, 0));
        accept(firstBooking);

        mockMvc.perform(put(PROFILE)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("0935111222").replace("MB Bank", "Techcombank")
                                .replace("\"0359111001\"", "\"19033334444\"")))
                .andExpect(status().isOk());

        String secondBooking = createBooking(playerToken, venue.getCourts().getFirst(), nextMonday(), LocalTime.of(14, 0));
        accept(secondBooking);

        Payment oldPayment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(UUID.fromString(firstBooking)).orElseThrow();
        Payment newPayment = paymentRepository.findFirstByBookingIdOrderByCreatedAtDesc(UUID.fromString(secondBooking)).orElseThrow();
        assertThat(oldPayment.getBankName()).isEqualTo("MB Bank");
        assertThat(oldPayment.getBankAccountNo()).isEqualTo("0359111001");
        assertThat(newPayment.getBankName()).isEqualTo("Techcombank");
        assertThat(newPayment.getBankAccountNo()).isEqualTo("19033334444");
    }

    @Test
    @DisplayName("Chu san con mat khau tam va nguoi choi deu khong xem duoc ho so chu san")
    void accessGate() throws Exception {
        owner.setPasswordChangeRequired(true);
        userRepository.saveAndFlush(owner);
        mockMvc.perform(get(PROFILE).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));
        mockMvc.perform(put(PROFILE)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("0935111222")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PASSWORD_CHANGE_REQUIRED"));

        User player = persistUser("player", Role.PLAYER);
        mockMvc.perform(get(PROFILE).header("Authorization", bearer(loginAs(player))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mockMvc.perform(get(PROFILE))
                .andExpect(status().isUnauthorized());
    }

    private void accept(String bookingId) throws Exception {
        mockMvc.perform(post("/api/v1/bookings/" + bookingId + "/accept")
                        .header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
    }

    private static String validBody(String phone) {
        return """
                {"fullName":"Nguyen Huu Thang","phone":"%s","businessName":"Ho kinh doanh","taxCode":"",
                 "bankName":"MB Bank","bankAccountNo":"0359111001","bankAccountName":"NGUYEN HUU THANG"}
                """.formatted(phone);
    }
}
