package com.courtly.api.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 2.3.73 - hieu suat xu ly va chat luong model ghep cap. Hien chua co AI service rieng nen
 * {@code aiServiceActive} = false va moi lan cham deu dung baseline; thoi gian do tren lan chay that.
 */
public record AdminMatchingPerformanceResponse(LocalDate from, LocalDate to, boolean aiServiceActive,
                                               Thresholds thresholds, Summary partnerSearch, Summary doubleTeam,
                                               List<Day> daily, List<ModelQuality> models, List<Alert> alerts) {

    /** @param maxFailureRate phan tram; @param minAcceptanceRate phan tram; @param maxP95Ms mili giay */
    public record Thresholds(double minAcceptanceRate, double maxFailureRate, int maxModelAgeDays, int maxP95Ms) {
    }

    /**
     * @param baselineRate ty le lan chay dung baseline (khong co AI)
     * @param avgResults   so ket qua trung binh moi lan chay thanh cong
     */
    public record Summary(long runs, long failed, Double failureRate, Long p50Ms, Long p95Ms, Long maxMs,
                          long baselineRuns, Double baselineRate, Double avgResults) {
    }

    public record Day(LocalDate day, long partnerSearches, long doubleTeams, long failed) {
    }

    /** @param ageDays so ngay ke tu luc huan luyen/kich hoat; null neu model chua co moc nao */
    public record ModelQuality(UUID id, String type, String version, String status, Instant trainedAt,
                               Instant activatedAt, Long ageDays, Map<String, Object> metrics) {
    }

    /** @param severity info hoac warning */
    public record Alert(String code, String severity, String message) {
    }
}
