package com.courtly.api.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.courtly.common.enums.ReviewStatus;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.review.PartnerReview;
import com.courtly.domain.review.PartnerReviewRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PartnerReviewControllerTest {

    private static final String PASSWORD = "Courtly@123";

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private UserRepository userRepository;
    @Autowired private RoleRepository roleRepository;
    @Autowired private MatchRepository matchRepository;
    @Autowired private PartnerReviewRepository reviewRepository;

    private User creator;
    private User opponent;
    private User outsider;
    private String creatorToken;
    private String opponentToken;
    private String outsiderToken;

    @BeforeEach
    void setUp() throws Exception {
        ensurePlayerRole();
        creator = persistPlayer("review-creator");
        opponent = persistPlayer("review-opponent");
        outsider = persistPlayer("review-outsider");
        creatorToken = loginAs(creator);
        opponentToken = loginAs(opponent);
        outsiderToken = loginAs(outsider);
    }

    @Test
    @DisplayName("2.2.43 cham tu 1 den 5 sao sau tran da xac nhan")
    void createsRatingForConfirmedMatchParticipant() throws Exception {
        UUID matchId = createCompletedMatch();

        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(matchId, opponent.getId(), 5, null)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.matchId").value(matchId.toString()))
                .andExpect(jsonPath("$.rating").value(5))
                .andExpect(jsonPath("$.comment").doesNotExist())
                .andExpect(jsonPath("$.reviewer.id").value(creator.getId().toString()))
                .andExpect(jsonPath("$.reviewer.fullName").value(creator.getFullName()))
                .andExpect(jsonPath("$.reviewer.email").doesNotExist())
                .andExpect(jsonPath("$.reviewer.phone").doesNotExist());

        PartnerReview saved = reviewRepository.findAll().stream()
                .filter(review -> review.getMatch().getId().equals(matchId))
                .findFirst().orElseThrow();
        assertThat(saved.getRating()).isEqualTo((short) 5);
        assertThat(saved.getStatus()).isEqualTo(ReviewStatus.VISIBLE);

        mockMvc.perform(get("/api/v1/matches/{matchId}", matchId)
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.team2[0].reviewedByCurrentUser").value(true));
    }

    @Test
    @DisplayName("2.2.43 yeu cau dang nhap va diem sao trong khoang hop le")
    void validatesAuthenticationAndRatingRange() throws Exception {
        UUID matchId = createCompletedMatch();

        mockMvc.perform(post("/api/v1/partner-reviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(matchId, opponent.getId(), 5, null)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(matchId, opponent.getId(), 0, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.rating").isNotEmpty());

        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(matchId, opponent.getId(), 6, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.rating").isNotEmpty());
    }

    @Test
    @DisplayName("2.2.43 chi nguoi tham gia danh gia nguoi cung tran da hoan tat")
    void validatesMatchStateAndParticipants() throws Exception {
        UUID scheduledMatchId = createMatch();
        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(scheduledMatchId, opponent.getId(), 4, null)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("MATCH_STATE_INVALID"));

        UUID completedMatchId = completeMatch(scheduledMatchId);
        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(outsiderToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(completedMatchId, opponent.getId(), 4, null)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("PARTNER_REVIEW_NOT_ALLOWED"));

        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(completedMatchId, outsider.getId(), 4, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reviewedUserId").isNotEmpty());

        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(completedMatchId, creator.getId(), 4, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reviewedUserId").isNotEmpty());
    }

    @Test
    @DisplayName("2.2.43 chan danh gia trung cung doi tac trong cung tran")
    void rejectsDuplicateReview() throws Exception {
        UUID matchId = createCompletedMatch();
        String payload = reviewPayload(matchId, opponent.getId(), 4, null);
        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON).content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PARTNER_REVIEW_DUPLICATE"));
    }

    @Test
    @DisplayName("2.2.44 luu nhan xet da cat khoang trang va cho phep noi dung rong")
    void normalizesOptionalComment() throws Exception {
        UUID firstMatchId = createCompletedMatch();
        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(firstMatchId, opponent.getId(), 5,
                                "  Than thien va choi rat dep.  ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").value("Than thien va choi rat dep."));

        UUID secondMatchId = createCompletedMatch();
        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(opponentToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(secondMatchId, creator.getId(), 4, "   ")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.comment").doesNotExist());
    }

    @Test
    @DisplayName("2.2.44 gioi han nhan xet toi da 1000 ky tu")
    void rejectsCommentOverMaximumLength() throws Exception {
        UUID matchId = createCompletedMatch();
        mockMvc.perform(post("/api/v1/partner-reviews")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reviewPayload(matchId, opponent.getId(), 5, "a".repeat(1001))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.comment").isNotEmpty());
    }

    @Test
    @DisplayName("2.2.45 xem phan trang diem trung binh va chi danh gia cong khai")
    void listsOnlyVisibleReviewsWithAggregateAndNoSensitiveData() throws Exception {
        UUID matchId = createCompletedMatch();
        persistReview(creator, opponent, matchId, 5, "Rat dung gio", ReviewStatus.VISIBLE,
                Instant.now().minus(1, ChronoUnit.HOURS));
        persistReview(outsider, opponent, null, 3, "Da bi an", ReviewStatus.HIDDEN,
                Instant.now().minus(2, ChronoUnit.HOURS));
        User anotherReviewer = persistPlayer("review-another");
        persistReview(anotherReviewer, opponent, null, 3, null, ReviewStatus.VISIBLE,
                Instant.now().minus(3, ChronoUnit.HOURS));
        User reportedReviewer = persistPlayer("review-reported");
        persistReview(reportedReviewer, opponent, null, 1, "Dang cho kiem duyet",
                ReviewStatus.REPORTED, Instant.now().minus(4, ChronoUnit.HOURS));

        mockMvc.perform(get("/api/v1/players/{playerId}/partner-reviews", opponent.getId())
                        .header("Authorization", bearer(creatorToken))
                        .param("page", "0").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(4.0))
                .andExpect(jsonPath("$.totalReviews").value(2))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].comment").value("Rat dung gio"))
                .andExpect(jsonPath("$.content[0].reviewer.fullName").value(creator.getFullName()))
                .andExpect(jsonPath("$.content[0].reviewer.email").doesNotExist())
                .andExpect(jsonPath("$.content[0].reviewer.phone").doesNotExist());
    }

    @Test
    @DisplayName("2.2.45 xu ly danh sach rong nguoi choi khong ton tai va phan trang sai")
    void handlesEmptyNotFoundAndInvalidPagination() throws Exception {
        mockMvc.perform(get("/api/v1/players/{playerId}/partner-reviews", creator.getId())
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.averageRating").value(0))
                .andExpect(jsonPath("$.totalReviews").value(0))
                .andExpect(jsonPath("$.content").isEmpty());

        mockMvc.perform(get("/api/v1/players/{playerId}/partner-reviews", UUID.randomUUID())
                        .header("Authorization", bearer(creatorToken)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/players/{playerId}/partner-reviews", creator.getId())
                        .header("Authorization", bearer(creatorToken)).param("size", "101"))
                .andExpect(status().isBadRequest());
    }

    private UUID createCompletedMatch() throws Exception {
        return completeMatch(createMatch());
    }

    private UUID createMatch() throws Exception {
        JsonNode payload = objectMapper.readTree("{}");
        var object = (com.fasterxml.jackson.databind.node.ObjectNode) payload;
        object.putNull("bookingId");
        object.putNull("venueId");
        object.put("matchType", "single");
        object.put("scheduledAt", Instant.now().minus(1, ChronoUnit.DAYS).toString());
        object.putArray("team1UserIds").add(creator.getId().toString());
        object.putArray("team2UserIds").add(opponent.getId().toString());
        String response = mockMvc.perform(post("/api/v1/matches")
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(object)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private UUID completeMatch(UUID matchId) throws Exception {
        mockMvc.perform(put("/api/v1/matches/{matchId}/result", matchId)
                        .header("Authorization", bearer(creatorToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"games":[
                                  {"gameNo":1,"team1Score":21,"team2Score":18},
                                  {"gameNo":2,"team1Score":21,"team2Score":16}
                                ]}
                                """))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/matches/{matchId}/confirm", matchId)
                        .header("Authorization", bearer(opponentToken)))
                .andExpect(status().isOk());
        return matchId;
    }

    private String reviewPayload(UUID matchId, UUID reviewedUserId, int rating, String comment)
            throws Exception {
        var payload = objectMapper.createObjectNode();
        payload.put("matchId", matchId.toString());
        payload.put("reviewedUserId", reviewedUserId.toString());
        payload.put("rating", rating);
        if (comment != null) {
            payload.put("comment", comment);
        }
        return objectMapper.writeValueAsString(payload);
    }

    private void persistReview(User reviewer, User reviewed, UUID matchId, int rating,
                               String comment, ReviewStatus status, Instant createdAt) {
        PartnerReview review = new PartnerReview();
        review.setReviewer(reviewer);
        review.setReviewedUser(reviewed);
        review.setMatch(matchId == null ? null : matchRepository.findById(matchId).orElseThrow());
        review.setRating((short) rating);
        review.setComment(comment);
        review.setStatus(status);
        review.setCreatedAt(createdAt);
        reviewRepository.saveAndFlush(review);
    }

    private void ensurePlayerRole() {
        if (roleRepository.findByCode(Role.PLAYER).isEmpty()) {
            Role role = new Role(Role.PLAYER, "Nguoi choi", null);
            role.setId(UUID.randomUUID());
            roleRepository.saveAndFlush(role);
        }
    }

    private User persistPlayer(String prefix) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setFullName("Nguoi Choi " + prefix);
        user.setEmail(prefix + "-" + UUID.randomUUID() + "@example.com");
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setStatus(UserStatus.ACTIVE);
        user.getUserRoles().add(new UserRole(
                user, roleRepository.findByCode(Role.PLAYER).orElseThrow()));
        user.setPlayerProfile(new PlayerProfile(user));
        return userRepository.saveAndFlush(user);
    }

    private String loginAs(User user) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emailOrPhone":"%s","password":"%s"}
                                """.formatted(user.getEmail(), PASSWORD)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("accessToken").asText();
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
