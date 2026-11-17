package com.courtly.service.matching;

import com.courtly.api.doubleteam.dto.DoubleTeamRequest;
import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse;
import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse.Option;
import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse.Pair;
import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse.Player;
import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse.Team;
import com.courtly.common.enums.DoubleTeamType;
import com.courtly.common.enums.Gender;
import com.courtly.common.enums.ModelStatus;
import com.courtly.common.enums.ModelType;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.PlayerAvailabilitySlot;
import com.courtly.domain.account.PlayerAvailabilitySlotRepository;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.matching.AiModel;
import com.courtly.domain.matching.AiModelRepository;
import com.courtly.domain.matching.DoubleTeamSuggestion;
import com.courtly.domain.matching.DoubleTeamSuggestionRepository;
import com.courtly.domain.matching.PlayerDistanceRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chia 4 nguoi choi thanh hai doi can bang (2.3.63 - 2.3.69).
 *
 * <p>Chua co AI service nen cham diem bang cong thuc baseline (nguon cham diem luon ghi la baseline).
 */
@Service
@RequiredArgsConstructor
public class DoubleTeamService {

    static final int PROVISIONAL_MATCH_LIMIT = 5;
    /** So tran de do tin cay cua Elo dat 100%. */
    static final int FULL_CONFIDENCE_MATCHES = 20;
    /** Tuong thich cap 100 / 0 cong / tru toi da bay nhieu diem Elo vao suc manh doi. */
    static final double COMPATIBILITY_ADJUSTMENT = 10.0;
    static final double VERY_BALANCED_MAX_GAP = 25.0;
    static final double BALANCED_MAX_GAP = 75.0;
    private static final BigDecimal BASELINE_START = PlayerStatistics.DEFAULT_RATING;
    private static final String BASELINE_VERSION = "baseline-doubles-v1";

    private final UserRepository userRepository;
    private final PlayerStatisticsRepository statisticsRepository;
    private final PlayerAvailabilitySlotRepository availabilitySlotRepository;
    private final PlayerDistanceRepository distanceRepository;
    private final AiModelRepository modelRepository;
    private final DoubleTeamSuggestionRepository suggestionRepository;
    private final ObjectMapper objectMapper;
    private final MatchingRunRecorder runRecorder;

    /** 2.3.63 - 2.3.69: kiem tra 4 nguoi, tinh va luu cac phuong an chia doi. */
    @Transactional
    public DoubleTeamSuggestionResponse suggest(UUID requesterId, DoubleTeamRequest input) {
        long startedAt = System.nanoTime();
        try {
            return computeAndSave(requesterId, input, startedAt);
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            runRecorder.recordFailure(MatchingRunRecorder.DOUBLE_TEAM, requesterId,
                    (System.nanoTime() - startedAt) / 1_000_000, e,
                    Map.of("matchType", String.valueOf(input.matchType().getValue())));
            throw e;
        }
    }

    private DoubleTeamSuggestionResponse computeAndSave(UUID requesterId, DoubleTeamRequest input, long startedAt) {
        List<UUID> ids = new ArrayList<>();
        ids.add(requesterId);
        ids.addAll(input.playerIds());
        Map<UUID, User> users = validatePlayers(requesterId, ids, input.matchType());

        Map<UUID, PlayerStatistics> stats = statisticsRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(PlayerStatistics::getUserId, s -> s));
        List<Player> players = ids.stream().map(id -> toPlayer(users.get(id), stats.get(id))).toList();
        Map<UUID, Player> playerById = players.stream().collect(Collectors.toMap(Player::id, p -> p));

        List<Pair> pairs = new ArrayList<>();
        Map<String, Pair> pairByKey = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            for (int j = i + 1; j < ids.size(); j++) {
                Pair pair = computePair(users.get(ids.get(i)), users.get(ids.get(j)), playerById);
                pairs.add(pair);
                pairByKey.put(key(ids.get(i), ids.get(j)), pair);
            }
        }

        List<Option> options = buildOptions(ids, input.matchType(), users, playerById, pairByKey);

        AiModel model = baselineModel();
        UUID batchId = UUID.randomUUID();
        Instant now = Instant.now();
        User requester = users.get(requesterId);
        List<DoubleTeamSuggestion> rows = new ArrayList<>();
        for (Option option : options) {
            DoubleTeamSuggestion row = new DoubleTeamSuggestion();
            row.setModel(model);
            row.setRequestedBy(requester);
            row.setBatchId(batchId);
            row.setMatchType(input.matchType());
            row.setRankNo((short) option.rank());
            row.setRecommended(option.recommended());
            row.setScoringSource("baseline");
            row.setTeam1Players(option.team1().playerIds());
            row.setTeam2Players(option.team2().playerIds());
            row.setTeam1Strength(option.team1().strength());
            row.setTeam2Strength(option.team2().strength());
            row.setBalanceScore(option.balanceScore());
            row.setDetailJson(toMap(new Detail(players, pairs, option)));
            rows.add(row);
        }
        suggestionRepository.saveAllAndFlush(rows);
        runRecorder.recordSuccess(batchId, MatchingRunRecorder.DOUBLE_TEAM, requesterId, model, "baseline",
                rows.size(), (System.nanoTime() - startedAt) / 1_000_000,
                Map.of("matchType", input.matchType().getValue()));
        return new DoubleTeamSuggestionResponse(batchId, input.matchType(), "baseline",
                model.getModelVersion(), rows.getFirst().getCreatedAt() != null ? rows.getFirst().getCreatedAt() : now,
                players, pairs, options);
    }

    /** 2.3.69 - xem lai mot de xuat cua chinh minh. */
    @Transactional(readOnly = true)
    public DoubleTeamSuggestionResponse get(UUID requesterId, UUID batchId) {
        List<DoubleTeamSuggestion> rows = suggestionRepository.findBatch(batchId, requesterId);
        if (rows.isEmpty()) {
            throw ApiException.notFound("Khong tim thay de xuat chia doi.");
        }
        DoubleTeamSuggestion first = rows.getFirst();
        Detail shared = objectMapper.convertValue(first.getDetailJson(), Detail.class);
        List<Option> options = rows.stream()
                .map(row -> objectMapper.convertValue(row.getDetailJson(), Detail.class).option())
                .toList();
        return new DoubleTeamSuggestionResponse(batchId, first.getMatchType(), first.getScoringSource(),
                first.getModel().getModelVersion(), first.getCreatedAt(), shared.players(), shared.pairs(), options);
    }

    // --- 2.3.63 ------------------------------------------------------------

    /** Ma loi tung nguoi: not_found, not_player, inactive, missing_gender, gender_mismatch, duplicate. */
    private Map<UUID, User> validatePlayers(UUID requesterId, List<UUID> ids, DoubleTeamType type) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (new LinkedHashSet<>(ids).size() != ids.size()) {
            for (UUID id : ids) {
                if (ids.indexOf(id) != ids.lastIndexOf(id)) {
                    errors.put(id.toString(), id.equals(requesterId) ? "self" : "duplicate");
                }
            }
            throw invalid(errors);
        }

        Map<UUID, User> users = new HashMap<>();
        Map<UUID, Gender> genders = new HashMap<>();
        for (UUID id : ids) {
            User user = userRepository.findById(id).orElse(null);
            if (user == null || isDatasetAccount(user)) {
                errors.put(id.toString(), "not_found");
                continue;
            }
            if (!userRepository.hasPlayerRole(id)) {
                errors.put(id.toString(), "not_player");
                continue;
            }
            if (user.getStatus() != UserStatus.ACTIVE) {
                errors.put(id.toString(), "inactive");
                continue;
            }
            PlayerProfile profile = user.getPlayerProfile();
            Gender gender = profile == null ? null : profile.getGender();
            if (gender == null) {
                errors.put(id.toString(), "missing_gender");
                continue;
            }
            users.put(id, user);
            genders.put(id, gender);
        }
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }

        long males = genders.values().stream().filter(g -> g == Gender.MALE).count();
        long females = genders.values().stream().filter(g -> g == Gender.FEMALE).count();
        for (UUID id : ids) {
            Gender gender = genders.get(id);
            boolean fits = switch (type) {
                case MENS -> gender == Gender.MALE;
                case WOMENS -> gender == Gender.FEMALE;
                case MIXED -> gender == Gender.MALE || gender == Gender.FEMALE;
            };
            if (!fits) errors.put(id.toString(), "gender_mismatch");
        }
        if (errors.isEmpty() && type == DoubleTeamType.MIXED && (males != 2 || females != 2)) {
            errors.put("matchType", "mixed_needs_two_each");
        }
        if (!errors.isEmpty()) {
            throw invalid(errors);
        }
        return users;
    }

    private static boolean isDatasetAccount(User user) {
        return user.getEmail() != null && user.getEmail().toLowerCase().endsWith("@bwf.example");
    }

    private static ApiException invalid(Map<String, String> errors) {
        return ApiException.badRequest(ErrorCode.DOUBLE_TEAM_PLAYER_INVALID,
                "Danh sach nguoi choi khong hop le de lap doi.", errors);
    }

    // --- 2.3.64 ------------------------------------------------------------

    private Player toPlayer(User user, PlayerStatistics stats) {
        BigDecimal rating = stats == null ? PlayerStatistics.DEFAULT_RATING : stats.getRatingScore();
        int matches = stats == null ? 0 : stats.getTotalMatches();
        BigDecimal winRate = stats == null ? BigDecimal.ZERO : stats.getWinRate();
        // Nguoi duoi 5 tran: keo Elo ve muc khoi diem theo ti le so tran, khong de mot vai tran thoi phong.
        double weight = Math.min(1.0, matches / (double) PROVISIONAL_MATCH_LIMIT);
        BigDecimal strength = BASELINE_START.add(rating.subtract(BASELINE_START)
                .multiply(BigDecimal.valueOf(weight))).setScale(2, RoundingMode.HALF_UP);
        BigDecimal confidence = BigDecimal.valueOf(Math.min(1.0, matches / (double) FULL_CONFIDENCE_MATCHES))
                .setScale(2, RoundingMode.HALF_UP);
        return new Player(user.getId(), user.getFullName(), user.getAvatarUrl(),
                user.getPlayerProfile().getGender().getValue(),
                rating.setScale(2, RoundingMode.HALF_UP), matches, matches < PROVISIONAL_MATCH_LIMIT,
                winRate.setScale(2, RoundingMode.HALF_UP), strength, confidence);
    }

    // --- 2.3.65 ------------------------------------------------------------

    private Pair computePair(User first, User second, Map<UUID, Player> players) {
        BigDecimal skill = PartnerSearchService.skillSimilarity(
                players.get(first.getId()).ratingScore(), players.get(second.getId()).ratingScore());
        PlayingStyle firstStyle = first.getPlayerProfile().getPlayingStyle();
        PlayingStyle secondStyle = second.getPlayerProfile().getPlayingStyle();
        BigDecimal style = PartnerSearchService.styleCompatibility(firstStyle, secondStyle);

        List<PlayerAvailabilitySlot> firstSlots = availabilitySlotRepository.findAllByUserIds(List.of(first.getId()));
        List<PlayerAvailabilitySlot> secondSlots = availabilitySlotRepository.findAllByUserIds(List.of(second.getId()));
        BigDecimal time = PartnerSearchService.weeklyOverlap(firstSlots, secondSlots);

        BigDecimal location = null;
        PlayerDistanceRepository.PairDistance distance = distanceRepository.nearestDistance(first.getId(), second.getId());
        if (distance != null && distance.getDistanceKm() != null && distance.getRadiusKm() != null) {
            location = PartnerSearchService.locationCompatibility(distance.getDistanceKm(), distance.getRadiusKm());
        }

        // Thanh phan thieu du lieu bi bo, trong so cua cac thanh phan con lai duoc chia lai.
        double weighted = 0.4 * skill.doubleValue() + 0.2 * style.doubleValue();
        double weights = 0.6;
        if (time != null) { weighted += 0.2 * time.doubleValue(); weights += 0.2; }
        if (location != null) { weighted += 0.2 * location.doubleValue(); weights += 0.2; }
        BigDecimal total = BigDecimal.valueOf(weighted / weights).setScale(2, RoundingMode.HALF_UP);
        return new Pair(List.of(first.getId(), second.getId()), skill, style, time, location, total);
    }

    // --- 2.3.66 - 2.3.69 ----------------------------------------------------

    private List<Option> buildOptions(List<UUID> ids, DoubleTeamType type, Map<UUID, User> users,
                                      Map<UUID, Player> players, Map<String, Pair> pairs) {
        UUID a = ids.get(0);
        List<UUID[]> partners = new ArrayList<>();
        for (int i = 1; i < 4; i++) {
            UUID partner = ids.get(i);
            List<UUID> others = ids.stream().filter(id -> !id.equals(a) && !id.equals(partner)).toList();
            partners.add(new UUID[]{partner, others.get(0), others.get(1)});
        }
        // Doi nam nu: moi doi phai gom mot nam va mot nu.
        if (type == DoubleTeamType.MIXED) {
            partners.removeIf(p -> gender(users, a) == gender(users, p[0]));
        }

        List<Draft> drafts = partners.stream().map(p -> {
            List<UUID> team1 = List.of(a, p[0]);
            List<UUID> team2 = List.of(p[1], p[2]);
            Team t1 = team(team1, players, pairs);
            Team t2 = team(team2, players, pairs);
            BigDecimal gap = t1.strength().subtract(t2.strength()).abs().setScale(2, RoundingMode.HALF_UP);
            return new Draft(t1, t2, gap);
        }).sorted(Comparator.comparing(Draft::gap)
                .thenComparing(d -> d.team1().compatibility().add(d.team2().compatibility()), Comparator.reverseOrder()))
                .toList();

        List<Option> options = new ArrayList<>();
        Draft best = drafts.getFirst();
        for (int i = 0; i < drafts.size(); i++) {
            Draft d = drafts.get(i);
            BigDecimal probability = winProbability(d.team1().strength(), d.team2().strength());
            options.add(new Option(i + 1, i == 0, d.team1(), d.team2(), d.gap(), label(d.gap()), probability,
                    reason(i == 0, d, best, probability)));
        }
        return options;
    }

    private static Gender gender(Map<UUID, User> users, UUID id) {
        return users.get(id).getPlayerProfile().getGender();
    }

    /** 2.3.67 - suc manh doi = trung binh suc manh hai nguoi, dieu chinh nhe theo tuong thich cua cap. */
    private static Team team(List<UUID> members, Map<UUID, Player> players, Map<String, Pair> pairs) {
        double average = members.stream().mapToDouble(id -> players.get(id).strength().doubleValue()).average().orElse(0);
        BigDecimal compatibility = pairs.get(key(members.get(0), members.get(1))).totalScore();
        double adjustment = (compatibility.doubleValue() - 50.0) / 50.0 * COMPATIBILITY_ADJUSTMENT;
        BigDecimal strength = BigDecimal.valueOf(average + adjustment).setScale(2, RoundingMode.HALF_UP);
        return new Team(members, strength, compatibility);
    }

    /** 2.3.68 - nhan do can bang theo do chenh suc manh. */
    static String label(BigDecimal gap) {
        if (gap.doubleValue() <= VERY_BALANCED_MAX_GAP) return "Rất cân bằng";
        if (gap.doubleValue() <= BALANCED_MAX_GAP) return "Cân bằng";
        return "Chênh lệch";
    }

    /** Xac suat doi 1 thang theo cong thuc Elo, tinh theo phan tram. */
    static BigDecimal winProbability(BigDecimal team1, BigDecimal team2) {
        double expected = 1.0 / (1.0 + Math.pow(10.0, (team2.doubleValue() - team1.doubleValue()) / 400.0));
        return BigDecimal.valueOf(expected * 100.0).setScale(1, RoundingMode.HALF_UP);
    }

    private static String reason(boolean best, Draft draft, Draft bestDraft, BigDecimal probability) {
        if (best) {
            return "Hai đội chỉ lệch %s điểm sức mạnh (đội 1 có %s%% khả năng thắng), điểm tương thích của hai đội là %s và %s."
                    .formatted(draft.gap().stripTrailingZeros().toPlainString(), probability.toPlainString(),
                            draft.team1().compatibility().toPlainString(), draft.team2().compatibility().toPlainString());
        }
        return "Hai đội lệch %s điểm sức mạnh, nhiều hơn phương án tốt nhất %s điểm."
                .formatted(draft.gap().stripTrailingZeros().toPlainString(),
                        draft.gap().subtract(bestDraft.gap()).stripTrailingZeros().toPlainString());
    }

    private static String key(UUID first, UUID second) {
        return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
    }

    private record Draft(Team team1, Team team2, BigDecimal gap) {
    }

    /** Du lieu luu trong detail_json: phan dung chung cua batch va phuong an cua dong do. */
    private record Detail(List<Player> players, List<Pair> pairs, Option option) {
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> toMap(Detail detail) {
        return objectMapper.convertValue(detail, Map.class);
    }

    private AiModel baselineModel() {
        return modelRepository.findFirstByModelTypeAndStatus(ModelType.DOUBLE_TEAM_RANKER, ModelStatus.ACTIVE)
                .orElseGet(() -> {
                    AiModel model = new AiModel();
                    model.setModelType(ModelType.DOUBLE_TEAM_RANKER);
                    model.setModelVersion(BASELINE_VERSION);
                    model.setStatus(ModelStatus.ACTIVE);
                    model.setTrainedAt(Instant.now());
                    model.setActivatedAt(Instant.now());
                    model.setParametersJson(Map.of(
                            "provisionalMatchLimit", PROVISIONAL_MATCH_LIMIT,
                            "compatibilityAdjustment", COMPATIBILITY_ADJUSTMENT,
                            "veryBalancedMaxGap", VERY_BALANCED_MAX_GAP,
                            "balancedMaxGap", BALANCED_MAX_GAP));
                    return modelRepository.save(model);
                });
    }
}
