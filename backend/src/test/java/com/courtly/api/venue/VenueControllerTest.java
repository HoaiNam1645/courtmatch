package com.courtly.api.venue;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.ApprovalStatus;
import com.courtly.common.enums.BlockStatus;
import com.courtly.common.enums.ReviewStatus;
import com.courtly.common.enums.CourtStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.enums.VenueStatus;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.review.VenueReview;
import com.courtly.domain.review.VenueReviewRepository;
import com.courtly.domain.venue.Court;
import com.courtly.domain.venue.CourtBlock;
import com.courtly.domain.venue.CourtBlockRepository;
import com.courtly.domain.venue.CourtPriceRule;
import com.courtly.domain.venue.Venue;
import com.courtly.domain.venue.VenueOperatingHours;
import com.courtly.domain.venue.VenueRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Kiem thu 2.1.13 tim kiem, 2.1.14 quanh vi tri, 2.1.15 - 2.1.17 danh sach tren ban do,
 * 2.1.18 chi tiet san, cung cac bo loc 2.1.19 - 2.1.21.
 *
 * <p>Test tu tao san rieng voi ten ngau nhien nen khong phu thuoc du lieu seeder,
 * va duoc rollback sau moi test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VenueControllerTest {

    private static final String VENUES = "/api/v1/venues";

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Toa do o Hai Chau, Da Nang. */
    private static final double BASE_LAT = 16.0609;
    private static final double BASE_LNG = 108.2237;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private VenueRepository venueRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private CourtBlockRepository courtBlockRepository;

    @Autowired
    private VenueReviewRepository venueReviewRepository;

    private String uniqueTag;
    private String nearSlug;

    @BeforeEach
    void seedVenues() {
        uniqueTag = "kt" + UUID.randomUUID().toString().substring(0, 8);
        User owner = persistOwner();

        // San gan: ngay tai toa do goc, gia thap.
        nearSlug = persistVenue(owner, "San Cầu Lông Gần " + uniqueTag, "Hải Châu",
                "12 Nguyễn Chí Thanh, Hải Châu", BASE_LAT, BASE_LNG,
                new BigDecimal("4.80"), 90_000).getSlug();

        // San xa: cach khoang 9 km, gia cao.
        persistVenue(owner, "San Cầu Lông Xa " + uniqueTag, "Liên Chiểu",
                "66 Nguyễn Lương Bằng, Liên Chiểu", 16.0747, 108.1497,
                new BigDecimal("4.20"), 200_000);
    }

    // --- 2.1.13 Tim kiem --------------------------------------------------

    @Test
    @DisplayName("Khong dang nhap van xem duoc danh sach san")
    void searchIsPublic() throws Exception {
        mockMvc.perform(get(VENUES).param("q", uniqueTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Phan hoi co du truong phan trang")
    void searchReturnsPageMetadata() throws Exception {
        mockMvc.perform(get(VENUES).param("q", uniqueTag).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @DisplayName("Tim theo ten khong dau van ra ket qua co dau")
    void searchIgnoresDiacritics() throws Exception {
        mockMvc.perform(get(VENUES).param("q", "cau long gan " + uniqueTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].slug").value(nearSlug));
    }

    @Test
    @DisplayName("Tim theo quan")
    void searchByDistrict() throws Exception {
        mockMvc.perform(get(VENUES).param("q", uniqueTag).param("district", "Liên Chiểu"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].district").value("Liên Chiểu"));
    }

    @Test
    @DisplayName("Tim theo dia chi")
    void searchByAddress() throws Exception {
        mockMvc.perform(get(VENUES).param("q", "nguyen luong bang"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.district == 'Liên Chiểu')]").isNotEmpty());
    }

    @Test
    @DisplayName("Khong tim thay tra ve danh sach rong chu khong phai loi")
    void searchReturnsEmptyPage() throws Exception {
        mockMvc.perform(get(VENUES).param("q", "khongcosannaotenthenay"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.content").isEmpty());
    }

    @Test
    @DisplayName("The san co so san con, khong phai so san con trong")
    void summaryExposesCourtCount() throws Exception {
        mockMvc.perform(get(VENUES).param("q", uniqueTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].courtCount").value(1))
                .andExpect(jsonPath("$.content[0].minPricePerHour").isNotEmpty());
    }

    // --- 2.1.14, 2.1.20 Quanh vi tri --------------------------------------

    @Test
    @DisplayName("Co toa do thi chi tra san trong ban kinh va kem khoang cach")
    void searchNearbyFiltersByRadius() throws Exception {
        mockMvc.perform(get(VENUES)
                        .param("q", uniqueTag)
                        .param("lat", String.valueOf(BASE_LAT))
                        .param("lng", String.valueOf(BASE_LNG))
                        .param("radiusKm", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].slug").value(nearSlug))
                .andExpect(jsonPath("$.content[0].distanceKm").value(0.0));
    }

    @Test
    @DisplayName("Noi rong ban kinh thi lay them san xa, sap theo khoang cach tang dan")
    void searchNearbySortsByDistance() throws Exception {
        mockMvc.perform(get(VENUES)
                        .param("q", uniqueTag)
                        .param("lat", String.valueOf(BASE_LAT))
                        .param("lng", String.valueOf(BASE_LNG))
                        .param("radiusKm", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].slug").value(nearSlug));
    }

    @Test
    @DisplayName("Khong gui toa do thi distanceKm bo trong")
    void distanceIsAbsentWithoutCoordinates() throws Exception {
        mockMvc.perform(get(VENUES).param("q", uniqueTag))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].distanceKm").doesNotExist());
    }

    @Test
    @DisplayName("Chi gui lat ma thieu lng tra 400")
    void rejectsIncompleteCoordinatePair() throws Exception {
        mockMvc.perform(get(VENUES).param("lat", String.valueOf(BASE_LAT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.coordinatePairComplete").isNotEmpty());
    }

    @Test
    @DisplayName("Vi do ngoai khoang tra 400")
    void rejectsLatitudeOutOfRange() throws Exception {
        mockMvc.perform(get(VENUES).param("lat", "999").param("lng", "105"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.lat").isNotEmpty());
    }

    @Test
    @DisplayName("Tham so sai kieu tra 400 va khong lo ten class Java")
    void rejectsNonNumericParamWithoutLeakingInternals() throws Exception {
        String body = mockMvc.perform(get(VENUES).param("lat", "abc").param("lng", "105"))
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain("java.lang");
    }

    // --- 2.1.21 Loc gia ---------------------------------------------------

    @Test
    @DisplayName("Loc gia toi da chi giu san co bang gia phu hop")
    void filtersByMaxPrice() throws Exception {
        mockMvc.perform(get(VENUES).param("q", uniqueTag).param("maxPricePerHour", "100000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].slug").value(nearSlug));
    }

    // --- Phan trang -------------------------------------------------------

    @Test
    @DisplayName("size vuot 100 tra 400")
    void rejectsPageSizeAboveLimit() throws Exception {
        mockMvc.perform(get(VENUES).param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.size").isNotEmpty());
    }

    // --- 2.1.19 Khu vuc ---------------------------------------------------

    @Test
    @DisplayName("Danh sach khu vuc lay tu du lieu, khong khai bao cung")
    void listsDistrictsFromData() throws Exception {
        mockMvc.perform(get(VENUES + "/districts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.district == 'Hải Châu')]").isNotEmpty())
                .andExpect(jsonPath("$[?(@.district == 'Liên Chiểu')]").isNotEmpty());
    }

    // --- 2.1.22 Loc theo tinh trang con trong -----------------------------

    @Test
    @DisplayName("San bi khoa het gio hom nay thi khong nam trong ket qua 'chi hien san con trong'")
    void filtersOutFullyBlockedVenue() throws Exception {
        Venue blocked = venueRepository.findBySlug(nearSlug).orElseThrow();
        // Mo cua ca ngay de cua so con lai cua hom nay luon khac rong, du chay test luc nao.
        openAllDay(blocked);
        blockWholeDay(blocked);

        mockMvc.perform(get(VENUES).param("q", uniqueTag))
                .andExpect(jsonPath("$.totalElements").value(2));

        mockMvc.perform(get(VENUES).param("q", uniqueTag).param("availableOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == '" + nearSlug + "')]").isEmpty());
    }

    @Test
    @DisplayName("San con khung gio trong thi van nam trong ket qua")
    void keepsVenueWithFreeSlots() throws Exception {
        Venue open = venueRepository.findBySlug(nearSlug).orElseThrow();
        openAllDay(open);

        mockMvc.perform(get(VENUES).param("q", uniqueTag).param("availableOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.slug == '" + nearSlug + "')]").isNotEmpty());
    }

    // --- 2.1.23 Danh gia san ----------------------------------------------

    @Test
    @DisplayName("Danh gia tra ve ten nguoi viet, khong lo email")
    void listsReviewsWithoutLeakingContact() throws Exception {
        Venue target = venueRepository.findBySlug(nearSlug).orElseThrow();
        User reviewer = persistOwner();

        VenueReview review = new VenueReview();
        review.setId(UUID.randomUUID());
        review.setVenue(target);
        review.setUser(reviewer);
        review.setRating((short) 5);
        review.setComment("San sach, den sang.");
        review.setStatus(ReviewStatus.VISIBLE);
        venueReviewRepository.saveAndFlush(review);

        String body = mockMvc.perform(get(VENUES + "/" + nearSlug + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].rating").value(5))
                .andExpect(jsonPath("$.content[0].reviewerName").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(body).doesNotContain(reviewer.getEmail());
    }

    @Test
    @DisplayName("Danh gia bi an khong hien ra")
    void hiddenReviewIsNotListed() throws Exception {
        Venue target = venueRepository.findBySlug(nearSlug).orElseThrow();

        VenueReview review = new VenueReview();
        review.setId(UUID.randomUUID());
        review.setVenue(target);
        review.setUser(persistOwner());
        review.setRating((short) 1);
        review.setStatus(ReviewStatus.HIDDEN);
        venueReviewRepository.saveAndFlush(review);

        mockMvc.perform(get(VENUES + "/" + nearSlug + "/reviews"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    // --- 2.1.18 Chi tiet san ----------------------------------------------

    @Test
    @DisplayName("Xem chi tiet bang slug")
    void getDetailBySlug() throws Exception {
        mockMvc.perform(get(VENUES + "/" + nearSlug))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(nearSlug))
                .andExpect(jsonPath("$.courts.length()").value(1))
                .andExpect(jsonPath("$.courts[0].priceRules.length()").value(1))
                .andExpect(jsonPath("$.minPricePerHour").value(90000))
                .andExpect(jsonPath("$.latitude").isNotEmpty());
    }

    @Test
    @DisplayName("Xem chi tiet bang id cung duoc")
    void getDetailById() throws Exception {
        UUID id = venueRepository.findBySlug(nearSlug).orElseThrow().getId();

        mockMvc.perform(get(VENUES + "/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.slug").value(nearSlug));
    }

    @Test
    @DisplayName("San khong ton tai tra 404")
    void getDetailReturnsNotFound() throws Exception {
        mockMvc.perform(get(VENUES + "/khong-co-san-nay"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("San chua duyet khong lo ra ngoai")
    void pendingVenueIsHidden() throws Exception {
        Venue pending = venueRepository.findBySlug(nearSlug).orElseThrow();
        pending.setApprovalStatus(ApprovalStatus.PENDING);
        venueRepository.saveAndFlush(pending);

        mockMvc.perform(get(VENUES + "/" + nearSlug))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(VENUES).param("q", uniqueTag))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // --- Tien ich ----------------------------------------------------------

    /** Mo cua 00:00 - 23:59 moi ngay de cua so hom nay luon khac rong. */
    private void openAllDay(Venue target) {
        target.getOperatingHours().clear();
        for (int day = 1; day <= 7; day++) {
            VenueOperatingHours hours = new VenueOperatingHours();
            hours.setId(UUID.randomUUID());
            hours.setVenue(target);
            hours.setDayOfWeek((short) day);
            hours.setOpenTime(LocalTime.MIDNIGHT);
            hours.setCloseTime(LocalTime.of(23, 59));
            target.getOperatingHours().add(hours);
        }
        venueRepository.saveAndFlush(target);
    }

    /** Khoa moi san con cua dia diem tu bay gio den het ngay. */
    private void blockWholeDay(Venue target) {
        Instant endOfDay = LocalDate.now(ZONE).atTime(23, 59).atZone(ZONE).toInstant();
        target.getCourts().forEach(item -> {
            CourtBlock block = new CourtBlock();
            block.setId(UUID.randomUUID());
            block.setCourt(item);
            block.setStartTime(Instant.now().minusSeconds(3600));
            block.setEndTime(endOfDay);
            block.setReason("Kiem thu");
            block.setStatus(BlockStatus.ACTIVE);
            courtBlockRepository.saveAndFlush(block);
        });
    }

    private User persistOwner() {
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setFullName("Chu San Kiem Thu");
        owner.setEmail("owner-" + UUID.randomUUID() + "@example.com");
        owner.setStatus(UserStatus.ACTIVE);
        return userRepository.save(owner);
    }

    private Venue persistVenue(User owner, String name, String district, String address,
                               double latitude, double longitude, BigDecimal rating, long pricePerHour) {
        Venue venue = new Venue();
        venue.setId(UUID.randomUUID());
        venue.setOwner(owner);
        venue.setName(name);
        venue.setSlug("kt-" + UUID.randomUUID());
        venue.setAddress(address);
        venue.setDistrict(district);
        venue.setProvince("Da Nang");
        venue.setLatitude(BigDecimal.valueOf(latitude));
        venue.setLongitude(BigDecimal.valueOf(longitude));
        venue.setStatus(VenueStatus.ACTIVE);
        venue.setApprovalStatus(ApprovalStatus.APPROVED);
        venue.setAverageRating(rating);

        Court court = new Court();
        court.setId(UUID.randomUUID());
        court.setVenue(venue);
        court.setName("San A");
        court.setCourtCode("A");
        court.setStatus(CourtStatus.ACTIVE);

        CourtPriceRule rule = new CourtPriceRule();
        rule.setId(UUID.randomUUID());
        rule.setCourt(court);
        rule.setStartTime(LocalTime.of(5, 0));
        rule.setEndTime(LocalTime.of(23, 0));
        rule.setPricePerHour(BigDecimal.valueOf(pricePerHour));
        rule.setStatus(ActiveStatus.ACTIVE);
        court.getPriceRules().add(rule);
        venue.getCourts().add(court);

        // flush ngay de trigger sinh cot location (PostGIS) truoc khi test truy van khoang cach.
        return venueRepository.saveAndFlush(venue);
    }
}
