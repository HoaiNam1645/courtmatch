package com.courtly.service.dispute;

import com.courtly.api.dispute.dto.SupplementResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.payment.BookingDispute;
import com.courtly.domain.payment.BookingDisputeRepository;
import com.courtly.service.notification.DisputeNotifier;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nguoi bao cao bo sung thong tin cho bao cao dang xu ly (2.3.91).
 *
 * <p>Chi khi bao cao con xu ly (cho chu san giai trinh hoac dang duoc quan tri vien xem xet), toi da
 * {@value DisputeQueryService#MAX_SUPPLEMENTS} lan; moi lan la mot phan tu them vao {@code reporter_updates_json} va khong sua/xoa duoc de
 * chu san, quan tri vien xem lai dung lich su. Anh dinh kem tai len rieng (2.3.88) kem supplementId.
 */
@Service
@RequiredArgsConstructor
public class DisputeSupplementService {

    static final Set<String> OPEN_FOR_SUPPLEMENT = Set.of("awaiting_owner_response", "reviewing");
    static final int MIN_CONTENT = 5;
    static final int MAX_CONTENT = 2000;

    private final BookingDisputeRepository disputeRepository;
    private final DisputeNotifier disputeNotifier;

    @Transactional
    public SupplementResponse add(UUID userId, UUID disputeId, String content) {
        BookingDispute dispute = disputeRepository.findByIdForUpdate(disputeId)
                .filter(item -> item.getReporter().getId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay bao cao."));
        if (!OPEN_FOR_SUPPLEMENT.contains(dispute.getStatus())) {
            throw ApiException.conflict(ErrorCode.BOOKING_DISPUTE_INVALID,
                    "Bao cao da dong nen khong the bo sung thong tin.", Map.of());
        }
        String text = content == null ? "" : content.trim();
        if (text.length() < MIN_CONTENT) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Noi dung bo sung qua ngan.",
                    Map.of("content", "Noi dung it nhat " + MIN_CONTENT + " ky tu"));
        }
        if (text.length() > MAX_CONTENT) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Noi dung bo sung qua dai.",
                    Map.of("content", "Noi dung toi da " + MAX_CONTENT + " ky tu"));
        }
        List<Map<String, Object>> updates = dispute.getReporterUpdates() == null ? new ArrayList<>() : new ArrayList<>(dispute.getReporterUpdates());
        if (updates.size() >= DisputeQueryService.MAX_SUPPLEMENTS) {
            throw ApiException.conflict(ErrorCode.BOOKING_DISPUTE_INVALID,
                    "Moi bao cao chi bo sung toi da " + DisputeQueryService.MAX_SUPPLEMENTS + " lan.", Map.of());
        }

        Instant now = Instant.now();
        Map<String, Object> update = new LinkedHashMap<>();
        UUID id = UUID.randomUUID();
        update.put("id", id.toString());
        update.put("content", text);
        update.put("createdAt", now.toString());
        updates.add(update);
        dispute.setReporterUpdates(updates);
        disputeRepository.saveAndFlush(dispute);
        if ("awaiting_owner_response".equals(dispute.getStatus())) {
            disputeNotifier.updated(dispute);
        }
        int evidences = dispute.getReporterEvidences() == null ? 0 : dispute.getReporterEvidences().size();
        return new SupplementResponse(id, text, now, DisputeQueryService.MAX_SUPPLEMENTS - updates.size(),
                Math.max(0, DisputeEvidenceService.MAX_TOTAL - evidences));
    }
}
