package com.courtly.api.ai;

import com.courtly.common.enums.ModelStatus;
import com.courtly.common.enums.ModelType;
import com.courtly.domain.matching.AiModel;
import com.courtly.domain.matching.AiModelRepository;
import java.time.Instant;
import java.util.Map;

/** Dung model AI trong DB va AI service gia dung chung cho cac test AI. */
final class AiTestSupport {

    static final StubAiServer STUB = new StubAiServer();

    private AiTestSupport() {
    }

    /** Dang ky cac thuoc tinh ket noi toi AI service gia (goi tu @DynamicPropertySource). */
    static void register(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("courtly.ai.enabled", () -> "true");
        registry.add("courtly.ai.base-url", () -> "http://127.0.0.1:" + STUB.port());
        registry.add("courtly.ai.token", () -> StubAiServer.TOKEN);
        registry.add("courtly.ai.timeout-ms", () -> "700");
        registry.add("courtly.ai.connect-timeout-ms", () -> "500");
    }

    /** Model AI dang hoat dong (loai chuyen thanh archived neu da co), rollback cung test. */
    static AiModel activeModel(AiModelRepository repository, ModelType type, String version, boolean gatePasses) {
        repository.findFirstByModelTypeAndStatus(type, ModelStatus.ACTIVE).ifPresent(existing -> {
            existing.setStatus(ModelStatus.ARCHIVED);
            repository.saveAndFlush(existing);
        });
        AiModel model = repository.findAll().stream()
                .filter(item -> item.getModelType() == type && version.equals(item.getModelVersion())).findFirst().orElseGet(AiModel::new);
        model.setModelType(type);
        model.setModelVersion(version);
        model.setStatus(ModelStatus.ACTIVE);
        model.setArtifactPath(version + "/model.cbm");
        model.setArtifactChecksum("a".repeat(64));
        model.setTrainedAt(Instant.now());
        model.setActivatedAt(Instant.now());
        model.setMetricsJson(Map.of("gate", Map.of("passes", gatePasses)));
        return repository.saveAndFlush(model);
    }
}
