package com.courtly.api.admin.dto;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 2.2.16 / 2.2.19 - danh sach phien ban model AI, model co the nhap va trang thai AI service. */
public final class AdminAiModelResponses {

    private AdminAiModelResponses() {
    }

    /**
     * @param metrics       tom tat: chi so chinh cua model va baseline tren cung tap kiem thu
     * @param gatePasses    dat dieu kien kich hoat (2.2.15); null voi baseline
     * @param gateReasons   chi so chua dat
     * @param kind          partner | team
     */
    public record Model(UUID id, String kind, String modelType, String modelVersion, String status, Instant trainedAt,
                        Instant activatedAt, Map<String, Object> metrics, Boolean gatePasses, List<String> gateReasons,
                        boolean canActivate, boolean canRollback) {
    }

    public record Importable(String kind, String modelType, String modelVersion, Instant trainedAt, Boolean gatePasses) {
    }

    /** @param source ai | baseline: nguon diem dang dung cho loai nay */
    public record Current(String kind, String source, String modelVersion) {
    }

    public record Service(boolean configured, boolean reachable, Map<String, Object> loaded) {
    }

    public record ListResponse(List<Model> models, List<Importable> importable, List<Current> current, Service service) {
    }
}
