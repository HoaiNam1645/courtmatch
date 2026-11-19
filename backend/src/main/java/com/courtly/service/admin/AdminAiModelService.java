package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminAiModelResponses.Current;
import com.courtly.api.admin.dto.AdminAiModelResponses.Importable;
import com.courtly.api.admin.dto.AdminAiModelResponses.ListResponse;
import com.courtly.api.admin.dto.AdminAiModelResponses.Model;
import com.courtly.api.admin.dto.AdminAiModelResponses.Service;
import com.courtly.common.config.AiProperties;
import com.courtly.common.enums.ModelStatus;
import com.courtly.common.enums.ModelType;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.matching.AiModel;
import com.courtly.domain.matching.AiModelRepository;
import com.courtly.service.ai.AiServiceClient;
import com.courtly.service.ai.AiUnavailableException;
import com.courtly.service.audit.AuditService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.2.16 / 2.2.19 - nhap, kich hoat va quay lai phien ban model AI.
 *
 * <p>Nhap chi doc {@code metadata.json} trong thu muc artifacts cua AI service theo TEN PHIEN BAN (khong nhan duong dan tuy y) va kiem tra
 * checksum file .cbm. Kich hoat chi khi dat kiem thu 2.2.15 va AI service nap thu thanh cong dung phien ban + checksum; model dang hoat dong
 * cung loai chuyen archived trong cung giao dich (chi mot model active moi loai, rang buoc DB). Quay lai kich hoat lai mot model archived.
 */
@Component
@RequiredArgsConstructor
public class AdminAiModelService {

    static final String VIEW = "matching.view";
    static final String MANAGE = "matching.manage";
    private static final Pattern VERSION = Pattern.compile("^[A-Za-z0-9._-]{1,50}$");
    private static final Map<ModelType, String> KIND = Map.of(ModelType.CATBOOST_RANKER, "partner", ModelType.CATBOOST_TEAM_RANKER, "team");

    private final AdminAccessService accessService;
    private final AiModelRepository modelRepository;
    private final AiServiceClient client;
    private final AiProperties properties;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    @Transactional(readOnly = true)
    public ListResponse list(UUID adminId) {
        accessService.requirePermission(adminId, VIEW);
        List<AiModel> all = modelRepository.findAll().stream()
                .sorted(Comparator.comparing(AiModel::getModelType).thenComparing(AiModel::getTrainingStartedAt, Comparator.reverseOrder()))
                .toList();
        List<Model> models = all.stream().map(this::toModel).toList();
        List<Current> current = new ArrayList<>();
        for (Map.Entry<ModelType, String> entry : KIND.entrySet()) {
            AiModel active = all.stream().filter(m -> m.getModelType() == entry.getKey() && m.getStatus() == ModelStatus.ACTIVE).findFirst().orElse(null);
            current.add(active == null ? new Current(entry.getValue(), "baseline", null) : new Current(entry.getValue(), "ai", active.getModelVersion()));
        }
        return new ListResponse(models, importable(all), current, service());
    }

    @Transactional
    public Model importModel(UUID adminId, String modelType, String version) {
        User admin = accessService.requirePermission(adminId, MANAGE);
        ModelType type = aiType(modelType);
        Map<String, Object> metadata = readMetadata(version);
        if (!KIND.get(type).equals(metadata.get("kind"))) {
            throw invalid("Model " + version + " khong phai loai " + KIND.get(type) + ".");
        }
        Path file = dir(version).resolve("model.cbm");
        String checksum = sha256(file);
        if (!checksum.equals(metadata.get("checksum"))) {
            throw invalid("Checksum file model khong khop metadata.json.");
        }
        boolean exists = modelRepository.findAll().stream().anyMatch(m -> m.getModelType() == type && version.equals(m.getModelVersion()));
        if (exists) {
            throw ApiException.conflict(ErrorCode.AI_MODEL_INVALID, "Phien ban " + version + " da duoc nhap.", Map.of());
        }
        AiModel model = new AiModel();
        model.setModelType(type);
        model.setModelVersion(version);
        model.setStatus(ModelStatus.READY);
        model.setArtifactPath(version + "/model.cbm");
        model.setArtifactChecksum(checksum);
        model.setFeatureSchemaJson(map(metadata.get("schema")));
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (String key : List.of("parameters", "seed", "labelDefinition", "dataNote", "data", "versions", "bestIteration")) {
            if (metadata.containsKey(key)) parameters.put(key, metadata.get(key));
        }
        model.setParametersJson(parameters);
        Map<String, Object> metrics = new LinkedHashMap<>(map(metadata.get("metrics")));
        metrics.put("gate", metadata.get("gate"));
        model.setMetricsJson(metrics);
        Instant trained = metadata.get("trainedAt") == null ? Instant.now() : Instant.parse(String.valueOf(metadata.get("trainedAt")));
        model.setTrainedAt(trained);
        model.setTrainingStartedAt(trained);
        modelRepository.saveAndFlush(model);
        auditService.record(admin, "ai_model.import", "ai_models", model.getId(), null,
                Map.of("modelType", type.getValue(), "modelVersion", version, "checksum", checksum));
        return toModel(model);
    }

    @Transactional
    public Model activate(UUID adminId, UUID modelId) {
        return switchTo(adminId, modelId, false);
    }

    @Transactional
    public Model rollback(UUID adminId, UUID modelId) {
        return switchTo(adminId, modelId, true);
    }

    private Model switchTo(UUID adminId, UUID modelId, boolean rollback) {
        User admin = accessService.requirePermission(adminId, MANAGE);
        AiModel target = modelRepository.findById(modelId).orElseThrow(() -> ApiException.notFound("Khong tim thay model."));
        if (!KIND.containsKey(target.getModelType())) {
            throw invalid("Chi kich hoat duoc model AI, khong kich hoat baseline.");
        }
        if (target.getStatus() == ModelStatus.ACTIVE) {
            throw ApiException.conflict(ErrorCode.AI_MODEL_INVALID, "Model nay dang hoat dong.", Map.of());
        }
        if (target.getStatus() == ModelStatus.FAILED || target.getStatus() == ModelStatus.TRAINING) {
            throw ApiException.conflict(ErrorCode.AI_MODEL_INVALID, "Model o trang thai " + target.getStatus().getValue() + " khong the kich hoat.", Map.of());
        }
        if (rollback && target.getStatus() != ModelStatus.ARCHIVED) {
            throw ApiException.conflict(ErrorCode.AI_MODEL_INVALID, "Chi quay lai duoc phien ban da luu tru (archived).", Map.of());
        }
        if (!rollback && target.getStatus() != ModelStatus.READY) {
            throw ApiException.conflict(ErrorCode.AI_MODEL_INVALID, "Chi kich hoat duoc model o trang thai ready; dung quay lai cho phien ban archived.", Map.of());
        }
        List<String> reasons = gateReasons(target);
        if (!gatePasses(target)) {
            throw ApiException.conflict(ErrorCode.AI_MODEL_INVALID, "Model chua dat kiem thu so voi baseline nen khong the kich hoat.",
                    Map.of("gate", String.join("; ", reasons)));
        }
        // AI service phai nap thu thanh cong dung phien ban va checksum truoc khi doi trang thai.
        try {
            AiServiceClient.Loaded loaded = client.loadModel(target.getModelType(), target.getModelVersion(), target.getArtifactChecksum());
            if (!target.getModelVersion().equals(loaded.modelVersion()) || !target.getArtifactChecksum().equals(loaded.checksum())) {
                throw ApiException.conflict(ErrorCode.AI_SERVICE_UNAVAILABLE, "AI service nap sai phien ban hoac checksum.", Map.of());
            }
        } catch (AiUnavailableException exception) {
            throw ApiException.conflict(ErrorCode.AI_SERVICE_UNAVAILABLE,
                    "AI service khong nap duoc model (" + exception.reason() + "), chua doi trang thai.", Map.of());
        }
        AiModel previous = modelRepository.findFirstByModelTypeAndStatus(target.getModelType(), ModelStatus.ACTIVE).orElse(null);
        if (previous != null) {
            previous.setStatus(ModelStatus.ARCHIVED);
            modelRepository.saveAndFlush(previous);
        }
        target.setStatus(ModelStatus.ACTIVE);
        target.setActivatedAt(Instant.now());
        modelRepository.saveAndFlush(target);
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("modelType", target.getModelType().getValue());
        after.put("modelVersion", target.getModelVersion());
        after.put("previousVersion", previous == null ? null : previous.getModelVersion());
        auditService.record(admin, rollback ? "ai_model.rollback" : "ai_model.activate", "ai_models", target.getId(),
                previous == null ? null : Map.of("modelVersion", previous.getModelVersion()), after);
        return toModel(target);
    }

    // --- helpers -------------------------------------------------------------

    private Model toModel(AiModel model) {
        boolean ai = KIND.containsKey(model.getModelType());
        Boolean passes = ai ? gatePasses(model) : null;
        boolean canActivate = ai && model.getStatus() == ModelStatus.READY && Boolean.TRUE.equals(passes);
        boolean canRollback = ai && model.getStatus() == ModelStatus.ARCHIVED && Boolean.TRUE.equals(passes);
        return new Model(model.getId(), ai ? KIND.get(model.getModelType()) : model.getModelType() == ModelType.BASELINE_RANKER ? "partner" : "team",
                model.getModelType().getValue(), model.getModelVersion(), model.getStatus().getValue(), model.getTrainedAt(),
                model.getActivatedAt(), summary(model), passes, ai ? gateReasons(model) : List.of(), canActivate, canRollback);
    }

    /** Chi so chinh: model va baseline tren cung tap kiem thu (NDCG@10 voi xep hang doi tac; log-loss voi chia doi). */
    private static Map<String, Object> summary(AiModel model) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> metrics = model.getMetricsJson();
        if (metrics == null) return out;
        Object m = metrics.get("model");
        Object b = metrics.get("baseline");
        if (m instanceof Map<?, ?> mm && b instanceof Map<?, ?> bb) {
            Object mo = mm.get("overall");
            Object bo = bb.get("overall");
            if (mo instanceof Map<?, ?> mom && bo instanceof Map<?, ?> bom) {
                if (mom.containsKey("ndcg@10")) {
                    out.put("metric", "ndcg@10");
                    out.put("model", mom.get("ndcg@10"));
                    out.put("baseline", bom.get("ndcg@10"));
                    out.put("precision@10", mom.get("precision@10"));
                    out.put("recall@10", mom.get("recall@10"));
                    out.put("groups", mom.get("groups"));
                } else if (mom.containsKey("log_loss")) {
                    out.put("metric", "log_loss");
                    out.put("model", mom.get("log_loss"));
                    out.put("baseline", bom.get("log_loss"));
                    out.put("accuracy", mom.get("accuracy"));
                    out.put("samples", mom.get("samples"));
                }
            }
        }
        return out;
    }

    private static boolean gatePasses(AiModel model) {
        Object gate = model.getMetricsJson() == null ? null : model.getMetricsJson().get("gate");
        return gate instanceof Map<?, ?> g && Boolean.TRUE.equals(g.get("passes"));
    }

    private static List<String> gateReasons(AiModel model) {
        Object gate = model.getMetricsJson() == null ? null : model.getMetricsJson().get("gate");
        if (gate instanceof Map<?, ?> g && g.get("reasons") instanceof List<?> reasons) {
            return reasons.stream().map(String::valueOf).toList();
        }
        return List.of();
    }

    private List<Importable> importable(List<AiModel> imported) {
        Path root = Path.of(properties.artifactDir()).toAbsolutePath().normalize();
        List<Importable> out = new ArrayList<>();
        if (!Files.isDirectory(root)) return out;
        try (var stream = Files.list(root)) {
            for (Path directory : stream.filter(Files::isDirectory).sorted().toList()) {
                String version = directory.getFileName().toString();
                if (!VERSION.matcher(version).matches() || !Files.isRegularFile(directory.resolve("metadata.json"))) continue;
                Map<String, Object> metadata;
                try {
                    metadata = objectMapper.readValue(directory.resolve("metadata.json").toFile(), Map.class);
                } catch (IOException | RuntimeException exception) {
                    continue;
                }
                ModelType type = "partner".equals(metadata.get("kind")) ? ModelType.CATBOOST_RANKER
                        : "team".equals(metadata.get("kind")) ? ModelType.CATBOOST_TEAM_RANKER : null;
                if (type == null || imported.stream().anyMatch(m -> m.getModelType() == type && version.equals(m.getModelVersion()))) continue;
                Object gate = metadata.get("gate");
                Boolean passes = gate instanceof Map<?, ?> g ? Boolean.TRUE.equals(g.get("passes")) : null;
                Instant trained = metadata.get("trainedAt") == null ? null : Instant.parse(String.valueOf(metadata.get("trainedAt")));
                out.add(new Importable(KIND.get(type), type.getValue(), version, trained, passes));
            }
        } catch (IOException exception) {
            return out;
        }
        return out;
    }

    private Service service() {
        if (!client.usable()) {
            return new Service(false, false, Map.of());
        }
        try {
            Map<String, Object> health = client.health();
            Object models = health.get("models");
            return new Service(true, true, models instanceof Map<?, ?> m ? map(m) : Map.of());
        } catch (AiUnavailableException exception) {
            return new Service(true, false, Map.of());
        }
    }

    private Path dir(String version) {
        if (version == null || !VERSION.matcher(version).matches()) {
            throw invalid("Ten phien ban khong hop le.");
        }
        Path root = Path.of(properties.artifactDir()).toAbsolutePath().normalize();
        Path path = root.resolve(version).normalize();
        if (!path.startsWith(root)) {
            throw invalid("Ten phien ban khong hop le.");
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readMetadata(String version) {
        Path file = dir(version).resolve("metadata.json");
        if (!Files.isRegularFile(file)) {
            throw ApiException.notFound("Khong co artifact cho phien ban " + version + ".");
        }
        try {
            return objectMapper.readValue(file.toFile(), Map.class);
        } catch (IOException exception) {
            throw invalid("Khong doc duoc metadata.json cua " + version + ".");
        }
    }

    private static String sha256(Path file) {
        if (!Files.isRegularFile(file)) {
            throw ApiException.notFound("Khong co file model.cbm.");
        }
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | java.security.NoSuchAlgorithmException exception) {
            throw invalid("Khong doc duoc file model.");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private static ModelType aiType(String value) {
        for (ModelType type : KIND.keySet()) {
            if (type.getValue().equals(value)) return type;
        }
        throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Loai model khong hop le.", Map.of("modelType", "Chi nhan catboost_ranker hoac double_team_ranker"));
    }

    private static ApiException invalid(String message) {
        return ApiException.badRequest(ErrorCode.AI_MODEL_INVALID, message, Map.of());
    }
}
