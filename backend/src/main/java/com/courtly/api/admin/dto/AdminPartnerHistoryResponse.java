package com.courtly.api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 2.3.75 - lich su ghep cap: moi dong la mot goi y doi tac va ket qua cua yeu cau gan voi no.
 * {@code outcome}: none (chua co tuong tac), pending, accepted, rejected, expired hoac cancelled.
 */
public final class AdminPartnerHistoryResponse {

    private AdminPartnerHistoryResponse() {
    }

    public record PlayerRef(UUID id, String fullName, String email) {
    }

    public record Item(UUID id, Instant createdAt, PlayerRef viewer, PlayerRef suggested, Integer rankNo,
                       BigDecimal totalScore, String scoringSource, String modelVersion, String outcome,
                       UUID requestId, Instant requestedAt, Instant respondedAt) {
    }

    /** Chi tiet mot goi y: diem thanh phan, ly do va thong tin lan tim da sinh ra no. */
    public record Detail(Item item, BigDecimal skillScore, BigDecimal locationScore, BigDecimal timeScore,
                         BigDecimal styleScore, BigDecimal confidenceScore, Map<String, Object> reason,
                         Search search) {
    }

    /** @param durationMs thoi gian xu ly lan tim; null voi du lieu cu khong co nhat ky */
    public record Search(UUID id, Integer durationMs, Integer resultCount, Map<String, Object> params) {
    }
}
