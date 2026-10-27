package com.courtly.seed;

import com.courtly.common.GeoSupport;
import com.courtly.common.enums.ConnectionStatus;
import com.courtly.common.enums.ModelStatus;
import com.courtly.common.enums.ModelType;
import com.courtly.common.enums.PartnerRequestStatus;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.common.enums.SuggestionStatus;
import com.courtly.common.enums.SuggestionSource;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.matching.AiModel;
import com.courtly.domain.matching.AiModelRepository;
import com.courtly.domain.matching.DoubleTeamSuggestion;
import com.courtly.domain.matching.DoubleTeamSuggestionRepository;
import com.courtly.domain.matching.PartnerRequest;
import com.courtly.domain.matching.PartnerRequestRepository;
import com.courtly.domain.matching.PartnerSuggestion;
import com.courtly.domain.matching.PartnerSuggestionRepository;
import com.courtly.domain.matching.PlayerConnection;
import com.courtly.domain.matching.PlayerConnectionRepository;
import com.courtly.domain.matching.PlayerMatchProfile;
import com.courtly.domain.matching.PlayerMatchProfileRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buoc 7: player_match_profiles, ai_models, partner_suggestions,
 * partner_requests, player_connections, double_team_suggestions.
 *
 * <p>Day la ket qua mau cua Sprint 2 de doi chieu giao dien va schema. Seeder khong
 * huan luyen model; artifact CatBoost that do AI service tao ra.
 */
@Slf4j
@Component
@Order(7)
@RequiredArgsConstructor
public class MatchingSeeder implements Seeder {

    private final PlayerMatchProfileRepository matchProfileRepository;
    private final AiModelRepository aiModelRepository;
    private final PartnerSuggestionRepository suggestionRepository;
    private final PartnerRequestRepository requestRepository;
    private final PlayerConnectionRepository connectionRepository;
    private final DoubleTeamSuggestionRepository doubleTeamRepository;
    private final UserRepository userRepository;

    private static final String PARTNER_MODEL = "model-partner-catboost-01";
    private static final String DOUBLE_TEAM_MODEL = "model-double-team-01";

    /** Vector dac trung cua tung nguoi choi, tong hop san tu ho so va thong ke. */
    private record ProfileSeed(String playerKey, String skillScore, String winRate, int totalMatches,
                               double latitude, double longitude, int radiusKm,
                               PlayingStyle playingStyle) {
    }

    private static final List<ProfileSeed> PROFILES = List.of(
            new ProfileSeed("player-01", "1372.00", "25.00", 4, 16.0609, 108.2237, 7,
                    PlayingStyle.ATTACKING),
            new ProfileSeed("player-02", "1180.00", "50.00", 2, 16.0655, 108.1858, 7,
                    PlayingStyle.BALANCED),
            new ProfileSeed("player-03", "1704.00", "66.67", 3, 16.0837, 108.2372, 7,
                    PlayingStyle.ATTACKING),
            new ProfileSeed("player-04", "832.00", "0.00", 2, 16.0042, 108.2522, 7,
                    PlayingStyle.DEFENSIVE),
            new ProfileSeed("player-05", "1258.00", "100.00", 2, 16.0747, 108.1497, 7,
                    PlayingStyle.BALANCED),
            new ProfileSeed("player-06", "1467.00", "100.00", 3, 16.0615, 108.224500, 7,
                    PlayingStyle.ATTACKING),
            new ProfileSeed("player-07", "1150.00", "50.00", 2, 16.0192, 108.1995, 7,
                    PlayingStyle.DEFENSIVE),
            new ProfileSeed("player-08", "872.00", "0.00", 2, 16.065, 108.18650, 7,
                    PlayingStyle.BALANCED));

    /** Khung gio thuong ranh: thu trong tuan -> cac gio bat dau. */
    private static final Map<String, List<Integer>> AVAILABILITY = Map.of(
            "2", List.of(18, 19, 20),
            "4", List.of(18, 19, 20),
            "6", List.of(7, 8, 9, 10));

    private record SuggestionSeed(String userKey, String suggestedKey,
                                  String skill, String location, String time, String style,
                                  String total, String reason) {
    }

    private static final List<SuggestionSeed> SUGGESTIONS = List.of(
            new SuggestionSeed("player-01", "player-06", "92.00", "96.00", "88.00", "95.00", "92.80",
                    "Trinh do can bang, cung quan Hai Chau, cung phong cach tan cong"),
            new SuggestionSeed("player-01", "player-03", "78.00", "62.00", "88.00", "95.00", "79.60",
                    "Trinh do cao hon mot bac, cung phong cach tan cong"),
            new SuggestionSeed("player-02", "player-05", "94.00", "84.00", "88.00", "98.00", "91.20",
                    "Trinh do can bang va cung phong cach balanced"),
            new SuggestionSeed("player-04", "player-08", "95.00", "88.00", "88.00", "70.00", "86.40",
                    "Cung nhom trinh do nguoi moi, khu vuc gan nhau"),
            new SuggestionSeed("player-07", "player-02", "88.00", "58.00", "88.00", "72.00", "77.60",
                    "Ty le thang tuong duong, lech khu vuc 8 km"),
            new SuggestionSeed("player-06", "player-01", "92.00", "96.00", "88.00", "95.00", "92.80",
                    "Cung cum ky nang, cung quan Hai Chau, cung phong cach tan cong"));

    private record RequestSeed(String key, String requesterKey, String receiverKey,
                               PartnerRequestStatus status, int daysAgo, String message) {
    }

    private static final List<RequestSeed> REQUESTS = List.of(
            new RequestSeed("request-01", "player-01", "player-06", PartnerRequestStatus.ACCEPTED, 9,
                    "Chao ban, toi 19h thu 5 tuan nay minh danh doi nhe?"),
            new RequestSeed("request-02", "player-02", "player-05", PartnerRequestStatus.PENDING, 2,
                    "Minh tim ban danh doi nam nu cuoi tuan, ban co ranh khong?"),
            new RequestSeed("request-03", "player-04", "player-03", PartnerRequestStatus.REJECTED, 14,
                    "Ban co nhan tap cung nguoi moi khong?"));

    @Override
    public String name() {
        return "match profiles + AI partner ranking";
    }

    @Override
    @Transactional
    public void seed() {
        if (aiModelRepository.count() > 0) {
            log.info("  [matching] da co du lieu, bo qua");
            return;
        }

        seedMatchProfiles();

        AiModel partnerModel = seedModel(PARTNER_MODEL, ModelType.CATBOOST_RANKER,
                "courtly-partner-v1", 1, "models/courtly-partner-v1.cbm",
                Map.of("loss_function", "YetiRank", "iterations", 500, "depth", 8,
                        "random_seed", 42, "top_n", 20),
                Map.of("ndcg_at_10", 0.84, "precision_at_10", 0.76,
                        "recall_at_10", 0.71, "suggestions_created", SUGGESTIONS.size()));
        seedSuggestions(partnerModel);

        seedRequestsAndConnections();

        AiModel doubleTeamModel = seedModel(DOUBLE_TEAM_MODEL, ModelType.DOUBLE_TEAM_RANKER,
                "courtly-double-team-v1", 3, "models/courtly-double-team-v1.cbm",
                Map.of("pool_size", 4, "objective", "minimize_strength_gap"),
                Map.of("combinations_evaluated", 3, "best_balance_score", 12.5));
        seedDoubleTeams(doubleTeamModel);

        log.info("  [matching] {} ho so, 2 model, {} goi y, {} yeu cau ghep cap",
                PROFILES.size(), SUGGESTIONS.size(), REQUESTS.size());
    }

    private void seedMatchProfiles() {
        for (ProfileSeed seed : PROFILES) {
            User user = player(seed.playerKey());

            PlayerMatchProfile profile = new PlayerMatchProfile(user);
            profile.setSkillScore(new BigDecimal(seed.skillScore()));
            profile.setWinRate(new BigDecimal(seed.winRate()));
            profile.setTotalMatches(seed.totalMatches());
            profile.setPreferredLocation(GeoSupport.point(seed.latitude(), seed.longitude()));
            profile.setPreferredRadiusKm(BigDecimal.valueOf(seed.radiusKm()));
            profile.setAvailabilityScore(AVAILABILITY);
            profile.setPlayingStyle(seed.playingStyle());
            matchProfileRepository.save(profile);
        }
    }

    private AiModel seedModel(String key, ModelType type, String version, int daysAgo,
                              String artifactPath, Map<String, Object> parameters,
                              Map<String, Object> metrics) {
        Instant startedAt = Instant.now().minus(daysAgo, ChronoUnit.DAYS);

        AiModel model = new AiModel();
        model.setId(SeedIds.of("ai-model:" + key));
        model.setModelType(type);
        model.setModelVersion(version);
        model.setArtifactPath(artifactPath);
        model.setArtifactChecksum("sha256:seed-" + key);
        model.setFeatureSchemaJson(Map.of(
                "version", 1,
                "features", List.of("skill_score", "win_rate", "recent_form",
                        "location_score", "time_score", "style_score")));
        model.setParametersJson(parameters);
        model.setMetricsJson(metrics);
        model.setTrainingStartedAt(startedAt);
        model.setTrainedAt(startedAt.plusSeconds(42));
        model.setActivatedAt(startedAt.plusSeconds(60));
        model.setStatus(ModelStatus.ACTIVE);
        return aiModelRepository.save(model);
    }

    private void seedSuggestions(AiModel model) {
        int index = 0;
        for (SuggestionSeed seed : SUGGESTIONS) {
            PartnerSuggestion suggestion = new PartnerSuggestion();
            suggestion.setId(SeedIds.of("partner-suggestion:" + index++));
            suggestion.setModel(model);
            suggestion.setUser(player(seed.userKey()));
            suggestion.setSuggestedUser(player(seed.suggestedKey()));
            suggestion.setSkillScore(new BigDecimal(seed.skill()));
            suggestion.setLocationScore(new BigDecimal(seed.location()));
            suggestion.setTimeScore(new BigDecimal(seed.time()));
            suggestion.setStyleScore(new BigDecimal(seed.style()));
            suggestion.setTotalScore(new BigDecimal(seed.total()));
            suggestion.setConfidenceScore(new BigDecimal("0.8600"));
            suggestion.setScoringSource(SuggestionSource.AI);
            suggestion.setReasonJson(Map.of(
                    "summary", seed.reason(),
                    "skill_gap", new BigDecimal(seed.skill()),
                    "shared_slots", List.of("Thu 3 18:00", "Thu 5 18:00")));
            suggestion.setStatus(SuggestionStatus.ACTIVE);
            suggestion.setCreatedAt(model.getTrainedAt());
            suggestionRepository.save(suggestion);
        }
    }

    private void seedRequestsAndConnections() {
        for (RequestSeed seed : REQUESTS) {
            Instant createdAt = Instant.now().minus(seed.daysAgo(), ChronoUnit.DAYS);

            PartnerRequest request = new PartnerRequest();
            request.setId(SeedIds.of("partner-request:" + seed.key()));
            request.setRequester(player(seed.requesterKey()));
            request.setReceiver(player(seed.receiverKey()));
            request.setMessage(seed.message());
            request.setStatus(seed.status());
            request.setCreatedAt(createdAt);
            if (seed.status() != PartnerRequestStatus.PENDING) {
                request.setRespondedAt(createdAt.plus(3, ChronoUnit.HOURS));
            }
            requestRepository.save(request);

            // Yeu cau duoc chap nhan tao ket noi hai chieu (2.2.36).
            if (seed.status() == PartnerRequestStatus.ACCEPTED) {
                saveConnection(seed, request, seed.requesterKey(), seed.receiverKey());
                saveConnection(seed, request, seed.receiverKey(), seed.requesterKey());
            }
        }
    }

    private void saveConnection(RequestSeed seed, PartnerRequest request,
                                String userKey, String partnerKey) {
        PlayerConnection connection = new PlayerConnection();
        connection.setId(SeedIds.of("player-connection:" + userKey + ":" + partnerKey));
        connection.setUser(player(userKey));
        connection.setPartner(player(partnerKey));
        connection.setSourceRequest(request);
        connection.setStatus(ConnectionStatus.ACTIVE);
        connection.setCreatedAt(request.getRespondedAt());
        connectionRepository.save(connection);
    }

    private void seedDoubleTeams(AiModel model) {
        DoubleTeamSuggestion suggestion = new DoubleTeamSuggestion();
        suggestion.setId(SeedIds.of("double-team-suggestion:01"));
        suggestion.setModel(model);
        suggestion.setTeam1Players(List.of(
                player("player-01").getId(), player("player-04").getId()));
        suggestion.setTeam2Players(List.of(
                player("player-06").getId(), player("player-08").getId()));
        suggestion.setTeam1Strength(new BigDecimal("2204.00"));
        suggestion.setTeam2Strength(new BigDecimal("2339.00"));
        suggestion.setBalanceScore(new BigDecimal("135.00"));
        suggestion.setCreatedAt(model.getTrainedAt());
        doubleTeamRepository.save(suggestion);

        DoubleTeamSuggestion alternative = new DoubleTeamSuggestion();
        alternative.setId(SeedIds.of("double-team-suggestion:02"));
        alternative.setModel(model);
        alternative.setTeam1Players(List.of(
                player("player-01").getId(), player("player-08").getId()));
        alternative.setTeam2Players(List.of(
                player("player-06").getId(), player("player-04").getId()));
        alternative.setTeam1Strength(new BigDecimal("2244.00"));
        alternative.setTeam2Strength(new BigDecimal("2299.00"));
        alternative.setBalanceScore(new BigDecimal("55.00"));
        alternative.setCreatedAt(model.getTrainedAt());
        doubleTeamRepository.save(alternative);
    }

    private User player(String key) {
        return userRepository.findById(SeedIds.of("user:" + key))
                .orElseThrow(() -> new IllegalStateException("Thieu nguoi choi '" + key + "'"));
    }
}
