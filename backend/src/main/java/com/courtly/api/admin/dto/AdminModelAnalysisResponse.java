package com.courtly.api.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.72 - ket qua cham diem cua tung model/phien ban trong khoang chon, de so sanh canh nhau.
 * Chi so khong tinh duoc (mau so 0) la null.
 */
public record AdminModelAnalysisResponse(LocalDate from, LocalDate to, String source, int minSample,
                                         List<Model> models) {

    public record Model(UUID id, String type, String version, String status, Instant trainedAt, Instant activatedAt,
                        Partner partner, Team team) {
    }

    /**
     * Gợi ý đối tác do model chấm; null khi model không chấm gợi ý trong khoảng.
     *
     * @param missingTimeRate     ty le goi y thieu diem thoi gian (nguoi choi chua khai bao lich)
     * @param missingLocationRate ty le goi y thieu diem vi tri
     */
    public record Partner(long scored, Double avgTotal, Double avgSkill, Double avgLocation, Double avgTime,
                          Double avgStyle, Double missingTimeRate, Double missingLocationRate,
                          List<Bucket> distribution, long requested, long answered, long accepted,
                          Double acceptanceRate, boolean sufficient) {
    }

    /** Chia doi do model chay; null khi khong co lan chia doi nao. */
    public record Team(long batches, Double avgBestGap, long pairs, Double missingTimeRate,
                       Double missingLocationRate, boolean sufficient) {
    }

    public record Bucket(String label, long count) {
    }
}
