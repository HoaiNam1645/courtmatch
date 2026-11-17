package com.courtly.api.doubleteam;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.common.enums.Gender;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.PlayerAvailabilitySlot;
import com.courtly.domain.account.PlayerAvailabilitySlotRepository;
import com.courtly.domain.account.PlayerPreferredLocation;
import com.courtly.domain.account.PlayerPreferredLocationRepository;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** 2.3.63 - 2.3.69: chon 4 nguoi, suc manh, tuong thich, chia doi, can bang va de xuat toi uu. */
class DoubleTeamTest extends OwnerApiTestBase {

    private static final String PATH = "/api/v1/double-teams/suggestions";

    @Autowired private PlayerStatisticsRepository statisticsRepository;
    @Autowired private PlayerPreferredLocationRepository locationRepository;
    @Autowired private PlayerAvailabilitySlotRepository availabilityRepository;
    @Autowired private JdbcTemplate jdbc;
    @PersistenceContext private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        ensureRoles();
    }

    private User player(String prefix, Gender gender, String rating, int matches) {
        User user = persistUser(prefix, Role.PLAYER);
        user.getPlayerProfile().setGender(gender);
        user.getPlayerProfile().setPlayingStyle(PlayingStyle.BALANCED);
        userRepository.saveAndFlush(user);
        PlayerStatistics statistics = new PlayerStatistics(user);
        statistics.setRatingScore(new BigDecimal(rating));
        statistics.setTotalMatches(matches);
        statisticsRepository.saveAndFlush(statistics);
        return user;
    }

    private String body(String type, User... others) {
        List<String> ids = new ArrayList<>();
        for (User other : others) ids.add("\"" + other.getId() + "\"");
        return "{\"matchType\":\"" + type + "\",\"playerIds\":[" + String.join(",", ids) + "]}";
    }

    private JsonNode suggest(User requester, String body) throws Exception {
        String response = mockMvc.perform(post(PATH).header("Authorization", bearer(loginAs(requester)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private JsonNode fail(User requester, String body, int expected) throws Exception {
        String response = mockMvc.perform(post(PATH).header("Authorization", bearer(loginAs(requester)))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().is(expected)).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response);
    }

    private static JsonNode playerOf(JsonNode result, User user) {
        for (JsonNode p : result.get("players")) if (p.get("id").asText().equals(user.getId().toString())) return p;
        throw new AssertionError("player missing " + user.getId());
    }

    @Test
    @DisplayName("Doi nam: 4 nguoi -> 3 phuong an, sap xep tu can bang nhat, chi phuong an dau duoc de xuat, moi doi co requester o doi 1")
    void mensProducesThreeOrderedOptions() throws Exception {
        User a = player("dt-a", Gender.MALE, "1300.00", 30);
        User b = player("dt-b", Gender.MALE, "1200.00", 30);
        User c = player("dt-c", Gender.MALE, "1100.00", 30);
        User d = player("dt-d", Gender.MALE, "1000.00", 30);

        JsonNode result = suggest(a, body("mens", b, c, d));
        assertThat(result.get("matchType").asText()).isEqualTo("mens");
        assertThat(result.get("scoringSource").asText()).isEqualTo("baseline");
        assertThat(result.get("players")).hasSize(4);
        assertThat(result.get("pairs")).hasSize(6);
        JsonNode options = result.get("options");
        assertThat(options).hasSize(3);

        double previous = -1;
        for (int i = 0; i < 3; i++) {
            JsonNode option = options.get(i);
            assertThat(option.get("rank").asInt()).isEqualTo(i + 1);
            assertThat(option.get("recommended").asBoolean()).isEqualTo(i == 0);
            assertThat(option.get("team1").get("playerIds").get(0).asText()).isEqualTo(a.getId().toString());
            double gap = option.get("balanceScore").asDouble();
            assertThat(gap).isGreaterThanOrEqualTo(previous);
            previous = gap;
            double diff = Math.abs(option.get("team1").get("strength").asDouble() - option.get("team2").get("strength").asDouble());
            assertThat(gap).isEqualTo(diff, org.assertj.core.data.Offset.offset(0.011));
        }
        // A+B (1250) gap voi C+D (1050) la lon nhat nen phai xep cuoi; A+D va B+C gan nhu bang nhau nen xep dau.
        JsonNode worst = options.get(2);
        assertThat(worst.get("balanceLabel").asText()).isEqualTo("Chênh lệch");
        assertThat(worst.get("team1").get("playerIds").get(1).asText()).isEqualTo(b.getId().toString());
        assertThat(options.get(0).get("balanceLabel").asText()).isEqualTo("Rất cân bằng");
        assertThat(options.get(0).get("team1").get("playerIds").get(1).asText()).isEqualTo(d.getId().toString());
        assertThat(options.get(0).get("reason").asText()).contains("lệch").contains("khả năng thắng");
        assertThat(options.get(0).get("team1WinProbability").asDouble()).isBetween(45.0, 55.0);
        assertThat(worst.get("team1WinProbability").asDouble()).isGreaterThan(70.0);
    }

    @Test
    @DisplayName("Suc manh doi = trung binh suc manh hai nguoi + (tuong thich-50)/50*10; tuong thich cap la trung binh co trong so cua cac thanh phan co du lieu")
    void teamStrengthFormula() throws Exception {
        User a = player("dt-f1", Gender.FEMALE, "1300.00", 30);
        User b = player("dt-f2", Gender.FEMALE, "1100.00", 30);
        User c = player("dt-f3", Gender.FEMALE, "1200.00", 30);
        User d = player("dt-f4", Gender.FEMALE, "1000.00", 30);
        JsonNode result = suggest(a, body("womens", b, c, d));

        for (JsonNode option : result.get("options")) {
            for (String teamKey : List.of("team1", "team2")) {
                JsonNode team = option.get(teamKey);
                double avg = 0;
                for (JsonNode id : team.get("playerIds")) {
                    avg += playerByIdStrength(result, id.asText());
                }
                avg /= 2;
                double expected = avg + (team.get("compatibility").asDouble() - 50.0) / 50.0 * 10.0;
                assertThat(team.get("strength").asDouble()).isEqualTo(expected, org.assertj.core.data.Offset.offset(0.011));
            }
        }
        // Khong co lich ranh/vi tri nen hai thanh phan do bi bo; tuong thich = (0.4*ky nang + 0.2*phong cach)/0.6.
        for (JsonNode pair : result.get("pairs")) {
            assertThat(absent(pair, "timeScore")).isTrue();
            assertThat(absent(pair, "locationScore")).isTrue();
            double expected = (0.4 * pair.get("skillScore").asDouble() + 0.2 * pair.get("styleScore").asDouble()) / 0.6;
            assertThat(pair.get("totalScore").asDouble()).isEqualTo(expected, org.assertj.core.data.Offset.offset(0.011));
            assertThat(pair.get("styleScore").asDouble()).isEqualTo(100.0);
        }
    }

    private static boolean absent(JsonNode node, String key) {
        return node.get(key) == null || node.get(key).isNull();
    }

    private static double playerByIdStrength(JsonNode result, String id) {
        for (JsonNode p : result.get("players")) if (p.get("id").asText().equals(id)) return p.get("strength").asDouble();
        throw new AssertionError(id);
    }

    @Test
    @DisplayName("Nguoi duoi 5 tran bi keo ve muc khoi diem va danh dau Tam tinh; do tin cay tang theo so tran")
    void provisionalPlayersArePulledToStart() throws Exception {
        User a = player("dt-p1", Gender.MALE, "1400.00", 2);
        User b = player("dt-p2", Gender.MALE, "1400.00", 5);
        User c = player("dt-p3", Gender.MALE, "800.00", 0);
        User d = player("dt-p4", Gender.MALE, "1100.00", 40);
        JsonNode result = suggest(a, body("mens", b, c, d));

        JsonNode pa = playerOf(result, a);
        assertThat(pa.get("provisional").asBoolean()).isTrue();
        assertThat(pa.get("ratingScore").asDouble()).isEqualTo(1400.0);
        assertThat(pa.get("strength").asDouble()).isEqualTo(1160.0);
        assertThat(pa.get("confidence").asDouble()).isEqualTo(0.10);
        JsonNode pb = playerOf(result, b);
        assertThat(pb.get("provisional").asBoolean()).isFalse();
        assertThat(pb.get("strength").asDouble()).isEqualTo(1400.0);
        assertThat(pb.get("confidence").asDouble()).isEqualTo(0.25);
        assertThat(playerOf(result, c).get("strength").asDouble()).isEqualTo(1000.0);
        assertThat(playerOf(result, c).get("confidence").asDouble()).isZero();
        assertThat(playerOf(result, d).get("confidence").asDouble()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Doi nam nu: chi 2 phuong an, moi doi gom mot nam mot nu")
    void mixedHasTwoOptionsWithOneOfEach() throws Exception {
        User a = player("dt-m1", Gender.MALE, "1300.00", 20);
        User b = player("dt-m2", Gender.FEMALE, "1200.00", 20);
        User c = player("dt-m3", Gender.MALE, "1100.00", 20);
        User d = player("dt-m4", Gender.FEMALE, "1000.00", 20);
        JsonNode result = suggest(a, body("mixed", b, c, d));

        assertThat(result.get("options")).hasSize(2);
        for (JsonNode option : result.get("options")) {
            for (String key : List.of("team1", "team2")) {
                List<String> genders = new ArrayList<>();
                for (JsonNode id : option.get(key).get("playerIds")) genders.add(playerOf(result, id.asText()));
                assertThat(genders).containsExactlyInAnyOrder("male", "female");
            }
        }
        // Doi 1 luon la requester + mot nu (b hoac d).
        assertThat(result.get("options").get(0).get("team1").get("playerIds").get(0).asText()).isEqualTo(a.getId().toString());
    }

    private static String playerOf(JsonNode result, String id) {
        for (JsonNode p : result.get("players")) if (p.get("id").asText().equals(id)) return p.get("gender").asText();
        throw new AssertionError(id);
    }

    @Test
    @DisplayName("Tuong thich cap dung ca vi tri va lich ranh khi co: gan nhau + trung lich cao hon xa nhau + khac lich")
    void pairCompatibilityUsesLocationAndAvailability() throws Exception {
        User a = player("dt-l1", Gender.MALE, "1200.00", 20);
        User near = player("dt-l2", Gender.MALE, "1200.00", 20);
        User far = player("dt-l3", Gender.MALE, "1200.00", 20);
        User other = player("dt-l4", Gender.MALE, "1200.00", 20);
        pin(a, 16.0609, 108.2237, 10);
        pin(near, 16.0650, 108.2250, 10);
        pin(far, 16.2500, 108.4500, 10);
        slot(a, 2, 17, 21);
        slot(near, 2, 17, 21);
        slot(far, 5, 8, 10);

        JsonNode result = suggest(a, body("mens", near, far, other));
        JsonNode pairNear = pair(result, a, near);
        JsonNode pairFar = pair(result, a, far);
        assertThat(pairNear.get("timeScore").asDouble()).isEqualTo(100.0);
        assertThat(pairNear.get("locationScore").asDouble()).isGreaterThan(90.0);
        assertThat(pairFar.get("timeScore").asDouble()).isZero();
        assertThat(pairFar.get("locationScore").asDouble()).isZero();
        assertThat(pairNear.get("totalScore").asDouble()).isGreaterThan(pairFar.get("totalScore").asDouble());
        // other khong co du lieu nao: thanh phan thieu bi bo, khong bi tinh 0.
        assertThat(absent(pair(result, a, other), "timeScore")).isTrue();
        assertThat(absent(pair(result, a, other), "locationScore")).isTrue();
    }

    private static JsonNode pair(JsonNode result, User first, User second) {
        for (JsonNode pair : result.get("pairs")) {
            List<String> ids = List.of(pair.get("playerIds").get(0).asText(), pair.get("playerIds").get(1).asText());
            if (ids.contains(first.getId().toString()) && ids.contains(second.getId().toString())) return pair;
        }
        throw new AssertionError("pair missing");
    }

    private void pin(User user, double lat, double lng, double radius) {
        PlayerPreferredLocation location = new PlayerPreferredLocation();
        location.setId(UUID.randomUUID());
        location.setUser(user);
        location.setLabel("Khu vuc test");
        location.setLatitude(BigDecimal.valueOf(lat));
        location.setLongitude(BigDecimal.valueOf(lng));
        location.setRadiusKm(BigDecimal.valueOf(radius));
        locationRepository.saveAndFlush(location);
    }

    private void slot(User user, int day, int from, int to) {
        PlayerAvailabilitySlot slot = new PlayerAvailabilitySlot();
        slot.setId(UUID.randomUUID());
        slot.setUser(user);
        slot.setDayOfWeek((short) day);
        slot.setStartTime(LocalTime.of(from, 0));
        slot.setEndTime(LocalTime.of(to, 0));
        availabilityRepository.saveAndFlush(slot);
    }

    @Test
    @DisplayName("De xuat duoc luu theo batch (3 dong, rank 1..3, chi dong 1 recommended) va xem lai dung bang GET; nguoi khac nhan 404")
    void persistsAndReloads() throws Exception {
        User a = player("dt-s1", Gender.MALE, "1300.00", 20);
        User b = player("dt-s2", Gender.MALE, "1250.00", 20);
        User c = player("dt-s3", Gender.MALE, "1100.00", 20);
        User d = player("dt-s4", Gender.MALE, "1000.00", 20);
        JsonNode created = suggest(a, body("mens", b, c, d));
        String batchId = created.get("batchId").asText();

        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM double_team_suggestions WHERE batch_id = ?::uuid AND requested_by = ? AND match_type = 'mens' AND scoring_source = 'baseline'",
                Integer.class, batchId, a.getId())).isEqualTo(3);
        assertThat(jdbc.queryForList("SELECT rank_no FROM double_team_suggestions WHERE batch_id = ?::uuid AND recommended", Integer.class, batchId)).containsExactly(1);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM double_team_suggestions WHERE batch_id = ?::uuid AND balance_score IS NOT NULL AND team1_strength IS NOT NULL AND team2_strength IS NOT NULL AND detail_json IS NOT NULL",
                Integer.class, batchId)).isEqualTo(3);
        entityManager.clear();

        String reloaded = mockMvc.perform(get(PATH + "/" + batchId).header("Authorization", bearer(loginAs(a))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode again = objectMapper.readTree(reloaded);
        assertThat(again.get("options")).hasSize(3);
        assertThat(again.get("players")).hasSize(4);
        assertThat(again.get("pairs")).hasSize(6);
        assertThat(again.get("modelVersion").asText()).isEqualTo(created.get("modelVersion").asText());
        assertThat(again.get("options").get(0).get("balanceScore").asDouble()).isEqualTo(created.get("options").get(0).get("balanceScore").asDouble());
        assertThat(again.get("options").get(0).get("reason").asText()).isEqualTo(created.get("options").get(0).get("reason").asText());

        mockMvc.perform(get(PATH + "/" + batchId).header("Authorization", bearer(loginAs(b)))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/" + UUID.randomUUID()).header("Authorization", bearer(loginAs(a)))).andExpect(status().isNotFound());
        mockMvc.perform(get(PATH + "/" + batchId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Kiem tra nguoi choi: khac gioi tinh loai doi, thieu gioi tinh, bi khoa, khong ton tai, trung lap, tu chon chinh minh; moi loi bao theo tung nguoi")
    void validatesPlayers() throws Exception {
        User a = player("dt-v1", Gender.MALE, "1200.00", 10);
        User b = player("dt-v2", Gender.MALE, "1200.00", 10);
        User c = player("dt-v3", Gender.MALE, "1200.00", 10);
        User female = player("dt-v4", Gender.FEMALE, "1200.00", 10);
        User noGender = player("dt-v5", null, "1200.00", 10);
        User locked = player("dt-v6", Gender.MALE, "1200.00", 10);
        locked.setStatus(UserStatus.INACTIVE);
        userRepository.saveAndFlush(locked);
        User owner = persistUser("dt-v-owner", Role.OWNER);

        assertThat(fail(a, body("mens", b, c, female), 400).get("fieldErrors").get(female.getId().toString()).asText()).isEqualTo("gender_mismatch");
        assertThat(fail(a, body("womens", b, c, female), 400).get("fieldErrors").get(a.getId().toString()).asText()).isEqualTo("gender_mismatch");
        assertThat(fail(a, body("mens", b, c, noGender), 400).get("fieldErrors").get(noGender.getId().toString()).asText()).isEqualTo("missing_gender");
        assertThat(fail(a, body("mens", b, c, locked), 400).get("fieldErrors").get(locked.getId().toString()).asText()).isEqualTo("inactive");
        assertThat(fail(a, body("mens", b, c, owner), 400).get("fieldErrors").get(owner.getId().toString()).asText()).isEqualTo("not_player");
        assertThat(fail(a, body("mens", b, b, c), 400).get("fieldErrors").get(b.getId().toString()).asText()).isEqualTo("duplicate");
        assertThat(fail(a, body("mens", a, b, c), 400).get("fieldErrors").get(a.getId().toString()).asText()).isEqualTo("self");
        UUID ghost = UUID.randomUUID();
        String ghostBody = "{\"matchType\":\"mens\",\"playerIds\":[\"" + b.getId() + "\",\"" + c.getId() + "\",\"" + ghost + "\"]}";
        assertThat(fail(a, ghostBody, 400).get("fieldErrors").get(ghost.toString()).asText()).isEqualTo("not_found");
        // Nhieu nguoi sai cung luc: bao het.
        JsonNode both = fail(a, body("mens", female, noGender, c), 400).get("fieldErrors");
        assertThat(both.has(female.getId().toString()) || both.has(noGender.getId().toString())).isTrue();
        // Nguoi gui khong phai nguoi choi.
        assertThat(fail(owner, body("mens", a, b, c), 400).get("fieldErrors").get(owner.getId().toString()).asText()).isEqualTo("not_player");
        // Doi nam nu phai dung 2 nam 2 nu.
        User d = player("dt-v7", Gender.FEMALE, "1200.00", 10);
        assertThat(fail(a, body("mixed", b, c, d), 400).get("fieldErrors").get("matchType").asText()).isEqualTo("mixed_needs_two_each");
        // Khong tao ban ghi nao khi loi.
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM double_team_suggestions WHERE requested_by = ?", Integer.class, a.getId())).isZero();
    }

    @Test
    @DisplayName("Dau vao sai hinh dang: thieu/thua nguoi, loai doi la, khong dang nhap")
    void validatesShape() throws Exception {
        User a = player("dt-w1", Gender.MALE, "1200.00", 10);
        User b = player("dt-w2", Gender.MALE, "1200.00", 10);
        fail(a, body("mens", b), 400);
        fail(a, "{\"matchType\":\"mens\"}", 400);
        fail(a, "{\"playerIds\":[]}", 400);
        fail(a, "{\"matchType\":\"triples\",\"playerIds\":[\"" + b.getId() + "\"]}", 400);
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body("mens", b))).andExpect(status().isUnauthorized());
    }
}
