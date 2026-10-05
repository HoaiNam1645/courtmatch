package com.courtly.seed;

import com.courtly.common.GeoSupport;
import com.courtly.common.enums.AlgorithmType;
import com.courtly.common.enums.ConnectionStatus;
import com.courtly.common.enums.PartnerRequestStatus;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.common.enums.RunStatus;
import com.courtly.common.enums.SuggestionStatus;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.matching.AlgorithmRun;
import com.courtly.domain.matching.AlgorithmRunRepository;
import com.courtly.domain.matching.DoubleTeamSuggestion;
import com.courtly.domain.matching.DoubleTeamSuggestionRepository;
import com.courtly.domain.matching.PartnerRequest;
import com.courtly.domain.matching.PartnerRequestRepository;
import com.courtly.domain.matching.PartnerSuggestion;
import com.courtly.domain.matching.PartnerSuggestionRepository;
import com.courtly.domain.matching.PlayerCluster;
import com.courtly.domain.matching.PlayerClusterRepository;
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
 * Buoc 7: player_match_profiles, algorithm_runs, player_clusters, partner_suggestions,
 * partner_requests, player_connections, double_team_suggestions.
 *
 * <p>Day la ket qua mau cua Sprint 2 duoc viet san de co du lieu doi chieu;
 * seeder khong chay K-Means, DBSCAN hay thuat toan ghep cap nao.
 */
@Slf4j
@Component
@Order(7)
@RequiredArgsConstructor
public class MatchingSeeder implements Seeder {

    private final PlayerMatchProfileRepository matchProfileRepository;
    private final AlgorithmRunRepository algorithmRunRepository;
    private final PlayerClusterRepository clusterRepository;
    private final PartnerSuggestionRepository suggestionRepository;
    private final PartnerRequestRepository requestRepository;
    private final PlayerConnectionRepository connectionRepository;
    private final DoubleTeamSuggestionRepository doubleTeamRepository;
    private final UserRepository userRepository;

    private static final String KMEANS_RUN = "run-kmeans-01";
    private static final String PARTNER_RUN = "run-partner-01";
    private static final String DOUBLE_TEAM_RUN = "run-double-team-01";

    /** Vector dac trung cua tung nguoi choi, tong hop san tu ho so va thong ke. */
    private record ProfileSeed(String playerKey, String skillScore, String winRate, int totalMatches,
                               double latitude, double longitude, int radiusKm,
                               PlayingStyle playingStyle, String clusterLabel, String distanceToCentroid) {
    }

    private static final List<ProfileSeed> PROFILES = List.of(
            new ProfileSeed("player-01", "1372.00", "25.00", 4, 16.0609, 108.2237, 7,
                    PlayingStyle.ATTACKING, "cluster_1", "0.4120"),
            new ProfileSeed("player-02", "1180.00", "50.00", 2, 16.0655, 108.1858, 7,
                    PlayingStyle.BALANCED, "cluster_0", "0.2870"),
            new ProfileSeed("player-03", "1704.00", "66.67", 3, 16.0837, 108.2372, 7,
                    PlayingStyle.ATTACKING, "cluster_2", "0.1950"),
            new ProfileSeed("player-04", "832.00", "0.00", 2, 16.0042, 108.2522, 7,
                    PlayingStyle.DEFENSIVE, "cluster_3", "0.3310"),
            new ProfileSeed("player-05", "1258.00", "100.00", 2, 16.0747, 108.1497, 7,
                    PlayingStyle.BALANCED, "cluster_0", "0.5040"),
            new ProfileSeed("player-06", "1467.00", "100.00", 3, 16.0615, 108.224500, 7,
                    PlayingStyle.ATTACKING, "cluster_1", "0.2260"),
            new ProfileSeed("player-07", "1150.00", "50.00", 2, 16.0192, 108.1995, 7,
                    PlayingStyle.DEFENSIVE, "cluster_0", "0.6180"),
            new ProfileSeed("player-08", "872.00", "0.00", 2, 16.065, 108.18650, 7,
                    PlayingStyle.BALANCED, "cluster_3", "0.2740"));

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
                    "Cung cum ky nang, cung quan Hai Chau, cung phong cach tan cong"),
            new SuggestionSeed("player-01", "player-03", "78.00", "62.00", "88.00", "95.00", "79.60",
                    "Trinh do cao hon mot bac, cung phong cach tan cong"),
            new SuggestionSeed("player-02", "player-05", "94.00", "84.00", "88.00", "98.00", "91.20",
                    "Cung cum ky nang va cung phong cach balanced"),
            new SuggestionSeed("player-04", "player-08", "95.00", "88.00", "88.00", "70.00", "86.40",
                    "Cung cum nguoi moi choi, khu vuc gan nhau"),
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
        return "match profiles + clustering + partner matching";
    }

    @Override
    @Transactional
    public void seed() {
        if (algorithmRunRepository.count() > 0) {
            log.info("  [matching] da co du lieu, bo qua");
            return;
        }

        seedMatchProfiles();

        AlgorithmRun kmeansRun = seedRun(KMEANS_RUN, AlgorithmType.KMEANS, "v1.0", 7,
                Map.of("k", 4, "max_iter", 300, "random_state", 42, "features",
                        List.of("skill_score", "win_rate", "total_matches", "availability_score")),
                Map.of("silhouette", 0.62, "davies_bouldin", 0.81, "inertia", 18.47, "n_clusters", 4));
        seedClusters(kmeansRun);

        AlgorithmRun partnerRun = seedRun(PARTNER_RUN, AlgorithmType.PARTNER_MATCHING, "v1.0", 1,
                Map.of("weights", Map.of("skill", 0.35, "location", 0.25, "time", 0.25, "style", 0.15),
                        "top_n", 20),
                Map.of("candidates_evaluated", 72, "suggestions_created", SUGGESTIONS.size()));
        seedSuggestions(partnerRun);

        seedRequestsAndConnections();

        AlgorithmRun doubleTeamRun = seedRun(DOUBLE_TEAM_RUN, AlgorithmType.DOUBLE_TEAM_MATCHING, "v1.0", 3,
                Map.of("pool_size", 4, "objective", "minimize_strength_gap"),
                Map.of("combinations_evaluated", 3, "best_balance_score", 12.5));
        seedDoubleTeams(doubleTeamRun);

        log.info("  [matching] {} ho so, 3 lan chay, {} goi y, {} yeu cau ghep cap",
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
            profile.setClusterLabel(seed.clusterLabel());
            matchProfileRepository.save(profile);
        }
    }

    private AlgorithmRun seedRun(String key, AlgorithmType type, String version, int daysAgo,
                                 Map<String, Object> parameters, Map<String, Object> metrics) {
        Instant startedAt = Instant.now().minus(daysAgo, ChronoUnit.DAYS);

        AlgorithmRun run = new AlgorithmRun();
        run.setId(SeedIds.of("algorithm-run:" + key));
        run.setAlgorithmType(type);
        run.setAlgorithmVersion(version);
        run.setParametersJson(parameters);
        run.setMetricsJson(metrics);
        run.setStartedAt(startedAt);
        run.setFinishedAt(startedAt.plusSeconds(42));
        run.setStatus(RunStatus.SUCCESS);
        return algorithmRunRepository.save(run);
    }

    private void seedClusters(AlgorithmRun run) {
        for (ProfileSeed seed : PROFILES) {
            PlayerCluster cluster = new PlayerCluster();
            cluster.setId(SeedIds.of("player-cluster:" + run.getId() + ":" + seed.playerKey()));
            cluster.setRun(run);
            cluster.setUser(player(seed.playerKey()));
            cluster.setClusterLabel(seed.clusterLabel());
            cluster.setDistanceToCentroid(new BigDecimal(seed.distanceToCentroid()));
            cluster.setCreatedAt(run.getFinishedAt());
            clusterRepository.save(cluster);
        }
    }

    private void seedSuggestions(AlgorithmRun run) {
        int index = 0;
        for (SuggestionSeed seed : SUGGESTIONS) {
            PartnerSuggestion suggestion = new PartnerSuggestion();
            suggestion.setId(SeedIds.of("partner-suggestion:" + index++));
            suggestion.setRun(run);
            suggestion.setUser(player(seed.userKey()));
            suggestion.setSuggestedUser(player(seed.suggestedKey()));
            suggestion.setSkillScore(new BigDecimal(seed.skill()));
            suggestion.setLocationScore(new BigDecimal(seed.location()));
            suggestion.setTimeScore(new BigDecimal(seed.time()));
            suggestion.setStyleScore(new BigDecimal(seed.style()));
            suggestion.setTotalScore(new BigDecimal(seed.total()));
            suggestion.setReasonJson(Map.of(
                    "summary", seed.reason(),
                    "skill_gap", new BigDecimal(seed.skill()),
                    "shared_slots", List.of("Thu 3 18:00", "Thu 5 18:00")));
            suggestion.setStatus(SuggestionStatus.ACTIVE);
            suggestion.setCreatedAt(run.getFinishedAt());
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

    private void seedDoubleTeams(AlgorithmRun run) {
        DoubleTeamSuggestion suggestion = new DoubleTeamSuggestion();
        suggestion.setId(SeedIds.of("double-team-suggestion:01"));
        suggestion.setRun(run);
        suggestion.setTeam1Players(List.of(
                player("player-01").getId(), player("player-04").getId()));
        suggestion.setTeam2Players(List.of(
                player("player-06").getId(), player("player-08").getId()));
        suggestion.setTeam1Strength(new BigDecimal("2204.00"));
        suggestion.setTeam2Strength(new BigDecimal("2339.00"));
        suggestion.setBalanceScore(new BigDecimal("135.00"));
        suggestion.setCreatedAt(run.getFinishedAt());
        doubleTeamRepository.save(suggestion);

        DoubleTeamSuggestion alternative = new DoubleTeamSuggestion();
        alternative.setId(SeedIds.of("double-team-suggestion:02"));
        alternative.setRun(run);
        alternative.setTeam1Players(List.of(
                player("player-01").getId(), player("player-08").getId()));
        alternative.setTeam2Players(List.of(
                player("player-06").getId(), player("player-04").getId()));
        alternative.setTeam1Strength(new BigDecimal("2244.00"));
        alternative.setTeam2Strength(new BigDecimal("2299.00"));
        alternative.setBalanceScore(new BigDecimal("55.00"));
        alternative.setCreatedAt(run.getFinishedAt());
        doubleTeamRepository.save(alternative);
    }

    private User player(String key) {
        return userRepository.findById(SeedIds.of("user:" + key))
                .orElseThrow(() -> new IllegalStateException("Thieu nguoi choi '" + key + "'"));
    }
}
