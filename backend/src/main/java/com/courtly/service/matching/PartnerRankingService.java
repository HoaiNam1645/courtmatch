package com.courtly.service.matching;

import com.courtly.common.enums.ModelStatus;
import com.courtly.common.enums.ModelType;
import com.courtly.domain.matching.AiModel;
import com.courtly.domain.matching.AiModelRepository;
import com.courtly.service.ai.AiModelSync;
import com.courtly.service.ai.AiServiceClient;
import com.courtly.service.ai.AiUnavailableException;
import java.math.BigDecimal;
import java.math.RoundingMode;
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
 * Cham diem cac ung vien DA QUA BO LOC bang model AI (2.2.28) va kiem tra lai ket qua (2.2.18).
 *
 * <p>Co model dang hoat dong va AI tra dung han thi nguon diem la {@code ai}; nguoc lai (khong co model, AI tat/loi/qua han,
 * ket qua sai) thi dung baseline va ghi ly do. AI khong bao gio them hoac bot ung vien: ket qua khong khop tap da gui bi coi la loi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PartnerRankingService {

    public record Input(UUID userId, Map<String, Object> features) {
    }

    public record Scored(BigDecimal totalScore, BigDecimal confidence, List<String> reasonCodes) {
    }

    /** {@code model} la model AI khi source = ai; khi baseline thi la null (noi goi dung model baseline). */
    public record Outcome(String source, AiModel model, Map<UUID, Scored> scores, String fallbackReason) {
        public boolean ai() {
            return "ai".equals(source);
        }
    }

    private final AiModelRepository modelRepository;
    private final AiServiceClient client;
    private final AiModelSync sync;

    public Outcome rank(UUID requesterId, List<Input> inputs) {
        Optional<AiModel> active = modelRepository.findFirstByModelTypeAndStatus(ModelType.CATBOOST_RANKER, ModelStatus.ACTIVE);
        if (active.isEmpty()) {
            return baseline("no_active_model");
        }
        if (inputs.isEmpty()) {
            return baseline("no_candidates");
        }
        List<AiServiceClient.RankCandidate> body = inputs.stream()
                .map(input -> new AiServiceClient.RankCandidate(input.userId().toString(), input.features())).toList();
        AiServiceClient.RankResponse response;
        try {
            response = client.rankPartners(requesterId.toString(), body);
        } catch (AiUnavailableException exception) {
            if ("http_503".equals(exception.reason()) || "connection".equals(exception.reason())) {
                sync.syncAll(false);
            }
            log.warn("AI khong kha dung cho lan tim nay ({}), dung baseline", exception.reason());
            return baseline(exception.reason());
        }
        AiModel model = active.get();
        if (response.modelVersion() == null || !response.modelVersion().equals(model.getModelVersion())) {
            sync.syncAll(false);
            return baseline("model_mismatch");
        }
        Map<UUID, Scored> scores = validate(inputs, response);
        if (scores == null) {
            return baseline("invalid_result");
        }
        return new Outcome("ai", model, scores, null);
    }

    private static Outcome baseline(String reason) {
        return new Outcome("baseline", null, Map.of(), reason);
    }

    /** Ket qua phai la HOAN VI dung tap ung vien da gui, diem 0-100, do tin cay 0-1; sai thi tra null (coi AI loi). */
    private static Map<UUID, Scored> validate(List<Input> inputs, AiServiceClient.RankResponse response) {
        if (response.results() == null || response.results().size() != inputs.size()) {
            return null;
        }
        Set<UUID> expected = new HashSet<>();
        inputs.forEach(input -> expected.add(input.userId()));
        Map<UUID, Scored> scores = new LinkedHashMap<>();
        for (AiServiceClient.Ranked ranked : response.results()) {
            UUID id;
            try {
                id = UUID.fromString(ranked.userId());
            } catch (RuntimeException exception) {
                return null;
            }
            if (!expected.contains(id) || scores.containsKey(id) || ranked.totalScore() == null || ranked.confidence() == null) {
                return null;
            }
            double score = ranked.totalScore().doubleValue();
            double confidence = ranked.confidence().doubleValue();
            if (!Double.isFinite(score) || score < 0 || score > 100 || !Double.isFinite(confidence) || confidence < 0 || confidence > 1) {
                return null;
            }
            List<String> reasons = ranked.reasons() == null ? List.of()
                    : ranked.reasons().stream().map(AiServiceClient.Reason::code).filter(code -> code != null).limit(3).toList();
            scores.put(id, new Scored(ranked.totalScore().setScale(2, RoundingMode.HALF_UP),
                    ranked.confidence().setScale(4, RoundingMode.HALF_UP), reasons));
        }
        return scores.keySet().equals(expected) ? scores : null;
    }

    /** Dat ten dac trung dung schema cua model (features.py). */
    public static Map<String, Object> features() {
        return new HashMap<>();
    }
}
