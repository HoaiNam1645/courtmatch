package com.courtly.service.ai;

import com.courtly.common.enums.ModelStatus;
import com.courtly.common.enums.ModelType;
import com.courtly.domain.matching.AiModel;
import com.courtly.domain.matching.AiModelRepository;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Giu AI service nap dung model dang hoat dong: nap khi khoi dong va tu nap lai (toi da 30 giay mot lan) khi AI service bao chua nap model
 * (vi du vua khoi dong lai). Mot yeu cau van dung baseline neu AI chua san sang.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiModelSync {

    static final long MIN_INTERVAL_MS = 30_000;
    private static final List<ModelType> AI_TYPES = List.of(ModelType.CATBOOST_RANKER, ModelType.CATBOOST_TEAM_RANKER);

    private final AiModelRepository modelRepository;
    private final AiServiceClient client;
    private final AtomicLong lastAttempt = new AtomicLong(0);

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        syncAll(true);
    }

    /** Nap cac model dang hoat dong vao AI service; {@code force} bo qua gioi han tan suat. */
    public void syncAll(boolean force) {
        long now = System.currentTimeMillis();
        long previous = lastAttempt.get();
        if (!client.usable() || (!force && now - previous < MIN_INTERVAL_MS) || !lastAttempt.compareAndSet(previous, now)) {
            return;
        }
        for (ModelType type : AI_TYPES) {
            modelRepository.findFirstByModelTypeAndStatus(type, ModelStatus.ACTIVE).ifPresent(this::load);
        }
    }

    private void load(AiModel model) {
        try {
            client.loadModel(model.getModelType(), model.getModelVersion(), model.getArtifactChecksum());
            log.info("Da nap model {} {} vao AI service", model.getModelType().getValue(), model.getModelVersion());
        } catch (AiUnavailableException exception) {
            log.warn("Khong nap duoc model {} vao AI service: {}", model.getModelVersion(), exception.reason());
        }
    }
}
