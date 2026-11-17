package com.courtly.service.matching;

import com.courtly.common.enums.ModelStatus;
import com.courtly.common.enums.ModelType;
import com.courtly.domain.matching.AiModel;
import com.courtly.domain.matching.AiModelRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ghi nhat ky moi lan tim doi tac / chia doi vao matching_runs (2.3.70 - 2.3.76).
 *
 * <p>Nhat ky la du lieu de do chat luong, khong duoc lam hong nghiep vu: lan that bai duoc ghi
 * trong transaction rieng de khong bi rollback cung yeu cau loi.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MatchingRunRecorder {

    public static final String PARTNER_SEARCH = "partner_search";
    public static final String DOUBLE_TEAM = "double_team";
    static final String PARTNER_BASELINE_VERSION = "baseline-partner-v1";

    private static final int MAX_ERROR_LENGTH = 300;

    private final JdbcTemplate jdbc;
    private final AiModelRepository modelRepository;
    private final ObjectMapper objectMapper;

    /** Ghi mot lan chay thanh cong; {@code runId} null thi tu sinh. Tra ve id da ghi. */
    public UUID recordSuccess(UUID runId, String type, UUID userId, AiModel model, String source,
                              int resultCount, long durationMs, Map<String, Object> params) {
        UUID id = runId == null ? UUID.randomUUID() : runId;
        insert(id, type, userId, model == null ? null : model.getId(), source, resultCount,
                durationMs, "success", null, params);
        return id;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String type, UUID userId, long durationMs, Throwable error,
                              Map<String, Object> params) {
        try {
            String message = error.getClass().getSimpleName() + ": " + error.getMessage();
            insert(UUID.randomUUID(), type, userId, null, "baseline", 0, durationMs, "failed",
                    message.length() > MAX_ERROR_LENGTH ? message.substring(0, MAX_ERROR_LENGTH) : message,
                    params);
        } catch (RuntimeException ex) {
            // Khong de loi ghi nhat ky che mat loi goc cua yeu cau.
            log.warn("Khong ghi duoc nhat ky that bai cua {}", type, ex);
        }
    }

    /** Model baseline dang hoat dong cho tim doi tac; tao khi chua co. */
    @Transactional
    public AiModel partnerBaselineModel() {
        return modelRepository.findFirstByModelTypeAndStatus(ModelType.BASELINE_RANKER, ModelStatus.ACTIVE)
                .orElseGet(() -> {
                    AiModel model = new AiModel();
                    model.setModelType(ModelType.BASELINE_RANKER);
                    model.setModelVersion(PARTNER_BASELINE_VERSION);
                    model.setStatus(ModelStatus.ACTIVE);
                    model.setTrainedAt(Instant.now());
                    model.setActivatedAt(Instant.now());
                    model.setParametersJson(Map.of("weights", PartnerScoring.weights()));
                    return modelRepository.saveAndFlush(model);
                });
    }

    private void insert(UUID id, String type, UUID userId, UUID modelId, String source, int resultCount,
                        long durationMs, String status, String error, Map<String, Object> params) {
        jdbc.update("""
                INSERT INTO matching_runs (id, run_type, user_id, model_id, scoring_source, result_count,
                                           duration_ms, status, error_message, params_json)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
                """, id, type, userId, modelId, source, resultCount,
                (int) Math.min(durationMs, Integer.MAX_VALUE), status, error, toJson(params));
    }

    private String toJson(Map<String, Object> params) {
        if (params == null) return null;
        try {
            return objectMapper.writeValueAsString(params);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
