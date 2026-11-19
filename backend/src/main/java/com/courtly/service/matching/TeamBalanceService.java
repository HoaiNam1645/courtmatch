package com.courtly.service.matching;

import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse.Option;
import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse.Pair;
import com.courtly.api.doubleteam.dto.DoubleTeamSuggestionResponse.Player;
import com.courtly.common.enums.ModelStatus;
import com.courtly.common.enums.ModelType;
import com.courtly.domain.matching.AiModel;
import com.courtly.domain.matching.AiModelRepository;
import com.courtly.domain.matching.PlayerMatchProfile;
import com.courtly.domain.matching.PlayerMatchProfileRepository;
import com.courtly.service.ai.AiModelSync;
import com.courtly.service.ai.AiServiceClient;
import com.courtly.service.ai.AiUnavailableException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Du doan do can bang cua cac phuong an chia doi bang model AI (2.2.28 chia doi). Tat ca phuong an cua mot lan chia cung dung mot nguon
 * diem: AI loi/qua han/chua co model thi TOAN BO dung cong thuc baseline Elo (khong tron hai nguon).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamBalanceService {

    /** {@code probability} la xac suat doi 1 thang 0-1; {@code balance} 0-100, 100 = rat can bang. */
    public record Result(BigDecimal probability, BigDecimal balance) {
    }

    public record Outcome(String source, AiModel model, Map<Integer, Result> byRank, String fallbackReason) {
        public boolean ai() {
            return "ai".equals(source);
        }
    }

    private final AiModelRepository modelRepository;
    private final PlayerMatchProfileRepository profileRepository;
    private final AiServiceClient client;
    private final AiModelSync sync;

    public Outcome predict(List<Option> options, Map<UUID, Player> players, Map<String, Pair> pairs, boolean mixed) {
        Optional<AiModel> active = modelRepository.findFirstByModelTypeAndStatus(ModelType.CATBOOST_TEAM_RANKER, ModelStatus.ACTIVE)
                .filter(model -> model.getArtifactChecksum() != null);
        if (active.isEmpty()) {
            return baseline("no_active_model");
        }
        Map<UUID, PlayerMatchProfile> profiles = new HashMap<>();
        profileRepository.findAllById(players.keySet()).forEach(profile -> profiles.put(profile.getUserId(), profile));
        List<AiServiceClient.BalanceOption> body = new ArrayList<>();
        for (Option option : options) {
            body.add(new AiServiceClient.BalanceOption(String.valueOf(option.rank()),
                    features(option, players, pairs, profiles, mixed)));
        }
        AiServiceClient.BalanceResponse response;
        try {
            response = client.predictBalance(body);
        } catch (AiUnavailableException exception) {
            if ("http_503".equals(exception.reason()) || "connection".equals(exception.reason())) {
                sync.syncAll(false);
            }
            log.warn("AI chia doi khong kha dung ({}), dung baseline", exception.reason());
            return baseline(exception.reason());
        }
        if (response.modelVersion() == null || !response.modelVersion().equals(active.get().getModelVersion())) {
            sync.syncAll(false);
            return baseline("model_mismatch");
        }
        Map<Integer, Result> results = validate(options, response);
        return results == null ? baseline("invalid_result") : new Outcome("ai", active.get(), results, null);
    }

    private static Outcome baseline(String reason) {
        return new Outcome("baseline", null, Map.of(), reason);
    }

    private static Map<Integer, Result> validate(List<Option> options, AiServiceClient.BalanceResponse response) {
        if (response.results() == null || response.results().size() != options.size()) {
            return null;
        }
        Set<Integer> expected = new HashSet<>();
        options.forEach(option -> expected.add(option.rank()));
        Map<Integer, Result> out = new LinkedHashMap<>();
        for (AiServiceClient.Balanced item : response.results()) {
            int rank;
            try {
                rank = Integer.parseInt(item.optionId());
            } catch (RuntimeException exception) {
                return null;
            }
            if (!expected.contains(rank) || out.containsKey(rank) || item.team1WinProbability() == null || item.balance() == null) {
                return null;
            }
            double p = item.team1WinProbability().doubleValue();
            double balance = item.balance().doubleValue();
            if (!Double.isFinite(p) || p < 0 || p > 1 || !Double.isFinite(balance) || balance < 0 || balance > 100) {
                return null;
            }
            out.put(rank, new Result(item.team1WinProbability(), item.balance()));
        }
        return out.keySet().equals(expected) ? out : null;
    }

    /** Dac trung theo schema "team" (features.py): Elo goc cua tung nguoi, ty le thang da lam tron, do on dinh, tuong thich cap. */
    private static Map<String, Object> features(Option option, Map<UUID, Player> players, Map<String, Pair> pairs,
                                                Map<UUID, PlayerMatchProfile> profiles, boolean mixed) {
        Map<String, Object> f = new LinkedHashMap<>();
        double[] rating = new double[2];
        int[] team = {0, 1};
        List<List<UUID>> teams = List.of(option.team1().playerIds(), option.team2().playerIds());
        for (int t : team) {
            List<UUID> ids = teams.get(t);
            String prefix = "t" + (t + 1) + "_";
            double sum = 0;
            double smoothed = 0;
            int minMatches = Integer.MAX_VALUE;
            List<Double> stability = new ArrayList<>();
            List<Double> recency = new ArrayList<>();
            for (UUID id : ids) {
                Player player = players.get(id);
                PlayerMatchProfile profile = profiles.get(id);
                sum += player.ratingScore().doubleValue();
                smoothed += profile == null ? 50.0 : profile.getWinRate().doubleValue();
                minMatches = Math.min(minMatches, player.totalMatches());
                if (profile != null && profile.getPerformanceConsistency() != null) stability.add(profile.getPerformanceConsistency().doubleValue());
                if (profile != null && profile.getDataRecencyDays() != null) recency.add(profile.getDataRecencyDays().doubleValue());
            }
            rating[t] = sum / ids.size();
            f.put(prefix + "rating", rating[t]);
            f.put(prefix + "partner_gap", Math.abs(players.get(ids.get(0)).ratingScore().doubleValue() - players.get(ids.get(1)).ratingScore().doubleValue()));
            f.put(prefix + "win_rate", smoothed / ids.size());
            f.put(prefix + "min_matches", minMatches);
            f.put(prefix + "stability", stability.isEmpty() ? null : stability.stream().mapToDouble(Double::doubleValue).average().orElse(0));
            f.put(prefix + "pair_compat", pairCompat(pairs.get(PairKeys.of(ids.get(0), ids.get(1)))));
            f.put(prefix + "recency_days", recency.isEmpty() ? null : recency.stream().mapToDouble(Double::doubleValue).average().orElse(0));
        }
        f.put("rating_diff", rating[0] - rating[1]);
        f.put("is_mixed", mixed ? 1 : 0);
        return f;
    }

    /** Trung binh cac thanh phan co du lieu (phong cach, lich ranh, vi tri), giong pair_compat trong ai-service. */
    private static Double pairCompat(Pair pair) {
        List<BigDecimal> parts = new ArrayList<>();
        if (pair.styleScore() != null) parts.add(pair.styleScore());
        if (pair.timeScore() != null) parts.add(pair.timeScore());
        if (pair.locationScore() != null) parts.add(pair.locationScore());
        return parts.isEmpty() ? null : parts.stream().mapToDouble(BigDecimal::doubleValue).average().orElse(0);
    }

    /** Khoa cap khong phu thuoc thu tu, trung voi DoubleTeamService. */
    static final class PairKeys {
        private PairKeys() {
        }

        static String of(UUID first, UUID second) {
            return first.compareTo(second) < 0 ? first + ":" + second : second + ":" + first;
        }
    }
}
