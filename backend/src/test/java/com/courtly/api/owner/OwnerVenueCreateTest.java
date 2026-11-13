package com.courtly.api.owner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.system.AuditLogRepository;
import com.courtly.domain.venue.Venue;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** 2.3.3 - Them san cau long moi. */
class OwnerVenueCreateTest extends OwnerApiTestBase {

    private static final String VENUES = "/api/v1/owner/venues";

    @Autowired private AuditLogRepository auditLogRepository;

    private User owner;
    private String ownerToken;

    @BeforeEach
    void setUp() throws Exception {
        ensureRoles();
        owner = persistOwner("owner-venue");
        ownerToken = loginAs(owner);
    }

    @Test
    @DisplayName("Danh muc form: quan/huyen Da Nang, tien ich, loai san, mat san, khung toa do")
    void formOptions() throws Exception {
        mockMvc.perform(get("/api/v1/owner/venue-options").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.districts", hasItem("Hải Châu")))
                .andExpect(jsonPath("$.districts", hasItem("Hòa Vang")))
                .andExpect(jsonPath("$.courtTypes[*].value", hasItem("vip")))
                .andExpect(jsonPath("$.surfaceTypes[*].value", hasItem("wood")))
                .andExpect(jsonPath("$.bounds.minLatitude").value(15.90))
                .andExpect(jsonPath("$.maxCourts").value(30));
    }

    @Test
    @DisplayName("Them san: trang thai cho duyet, slug khong dau, chuan hoa so co dinh, co san con va tien ich, ghi audit")
    void createVenue() throws Exception {
        String body = validBody("Sân Cầu Lông Đà Nẵng Xanh " + UUID.randomUUID().toString().substring(0, 6));
        String response = mockMvc.perform(post(VENUES)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("pending"))
                .andExpect(jsonPath("$.approvalStatus").value("pending"))
                .andExpect(jsonPath("$.province").value("Da Nang"))
                .andExpect(jsonPath("$.phone").value("02363888777"))
                .andExpect(jsonPath("$.courts", hasSize(2)))
                .andExpect(jsonPath("$.courts[0].courtCode").value("A"))
                .andExpect(jsonPath("$.courts[0].status").value("active"))
                .andExpect(jsonPath("$.courts[1].courtType").value("vip"))
                .andExpect(jsonPath("$.services", hasSize(1)))
                .andExpect(jsonPath("$.services[0].code").value("parking"))
                .andReturn().getResponse().getContentAsString();

        UUID venueId = UUID.fromString(objectMapper.readTree(response).get("id").asText());
        Venue saved = venueRepository.findById(venueId).orElseThrow();
        assertThat(saved.getSlug()).startsWith("san-cau-long-da-nang-xanh-");
        assertThat(saved.getOwner().getId()).isEqualTo(owner.getId());
        assertThat(saved.getStatus()).isEqualTo(VenueStatus.PENDING);
        assertThat(saved.getApprovalStatus()).isEqualTo(ApprovalStatus.PENDING);
        assertThat(auditLogRepository.findAll()).anySatisfy(log -> {
            assertThat(log.getAction()).isEqualTo("venue.create");
            assertThat(log.getEntityId()).isEqualTo(venueId);
        });

        // San chua duyet thi nguoi choi khong thay.
        mockMvc.perform(get("/api/v1/venues/" + saved.getSlug()))
                .andExpect(status().isNotFound());

        // Danh sach san cua chu san co san moi, kem so san con.
        mockMvc.perform(get(VENUES).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(venueId.toString()))
                .andExpect(jsonPath("$.content[0].approvalStatus").value("pending"))
                .andExpect(jsonPath("$.content[0].courtCount").value(2));
    }

    @Test
    @DisplayName("Ten trung thi slug tu them hau to")
    void duplicateNameGetsUniqueSlug() throws Exception {
        String name = "San Trung Ten " + UUID.randomUUID().toString().substring(0, 6);
        String first = createAndReadSlug(name);
        String second = createAndReadSlug(name);
        assertThat(second).isEqualTo(first + "-2");
    }

    @Test
    @DisplayName("Bat buoc cac truong va it nhat mot san con")
    void requiredFields() throws Exception {
        mockMvc.perform(post(VENUES)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" ","address":"","district":"","phone":"","courts":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.name").exists())
                .andExpect(jsonPath("$.fieldErrors.address").exists())
                .andExpect(jsonPath("$.fieldErrors.district").exists())
                .andExpect(jsonPath("$.fieldErrors.latitude").exists())
                .andExpect(jsonPath("$.fieldErrors.phone").exists())
                .andExpect(jsonPath("$.fieldErrors.courts").exists());

        mockMvc.perform(post(VENUES)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("San Thieu Ten San Con").replace("\"name\":\"San A\"", "\"name\":\"\"")
                                .replace("\"courtCode\":\"a\"", "\"courtCode\":\"A B\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['courts[0].name']").exists())
                .andExpect(jsonPath("$.fieldErrors['courts[0].courtCode']").exists());
    }

    @Test
    @DisplayName("Ngoai Da Nang: sai quan/huyen hoac toa do o tinh khac deu bi tu choi")
    void outsideDaNang() throws Exception {
        mockMvc.perform(post(VENUES)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("San Ha Noi").replace("Hải Châu", "Cầu Giấy")
                                .replace("16.0609", "21.0285").replace("108.2237", "105.8542")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.district").exists())
                .andExpect(jsonPath("$.fieldErrors.latitude").exists());
    }

    @Test
    @DisplayName("Sai so dien thoai, tien ich khong ton tai, ma san con trung, loai san la")
    void businessValidation() throws Exception {
        mockMvc.perform(post(VENUES)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("San So Sai").replace("0236 3888 777", "0123456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.phone").exists());

        mockMvc.perform(post(VENUES)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("San Tien Ich La").replace("\"parking\"", "\"spa\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['services[0].code']").exists());

        mockMvc.perform(post(VENUES)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("San Trung Ma").replace("\"courtCode\":\"vip\"", "\"courtCode\":\"A\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['courts[1].courtCode']").exists());

        mockMvc.perform(post(VENUES)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("San Loai La").replace("\"courtType\":\"vip\"", "\"courtType\":\"luxury\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors['courts[1].courtType']").exists());

        assertThat(venueRepository.findByOwnerIdOrderByCreatedAtDesc(owner.getId(),
                org.springframework.data.domain.Pageable.unpaged()).getTotalElements()).isZero();
    }

    @Test
    @DisplayName("Danh sach chi gom san cua chinh chu san; nguoi choi khong goi duoc")
    void listIsScopedToOwner() throws Exception {
        User otherOwner = persistOwner("other-owner");
        persistVenue(otherOwner, "San Cua Chu Khac");
        persistVenue(owner, "San Cua Toi");

        mockMvc.perform(get(VENUES).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].name").value("San Cua Toi"))
                .andExpect(jsonPath("$.content[0].status").value("active"));

        mockMvc.perform(get(VENUES).param("size", "101").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isBadRequest());

        User player = persistUser("player", Role.PLAYER);
        mockMvc.perform(post(VENUES)
                        .header("Authorization", bearer(loginAs(player)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody("San Cua Nguoi Choi")))
                .andExpect(status().isForbidden());
    }

    private String createAndReadSlug(String name) throws Exception {
        String response = mockMvc.perform(post(VENUES)
                        .header("Authorization", bearer(ownerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validBody(name)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("slug").asText();
    }

    private static String validBody(String name) {
        return """
                {"name":"%s","description":"San moi xay","address":"12 Bach Dang","ward":"Thach Thang",
                 "district":"Hải Châu","latitude":16.0609,"longitude":108.2237,
                 "phone":"0236 3888 777","email":"san@example.com",
                 "services":[{"code":"parking","price":0,"note":"Mien phi xe may"}],
                 "courts":[
                   {"name":"San A","courtCode":"a","courtType":"standard","surfaceType":"vinyl","indoor":true},
                   {"name":"San VIP","courtCode":"vip","courtType":"vip","surfaceType":"wood","indoor":true}
                 ]}
                """.formatted(name);
    }
}
