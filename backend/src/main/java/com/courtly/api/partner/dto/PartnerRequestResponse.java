package com.courtly.api.partner.dto;

import com.courtly.common.enums.PartnerRequestStatus;
import java.time.Instant;
import java.util.UUID;

/** Yeu cau ghep cap kem thong tin hai nguoi choi. */
public record PartnerRequestResponse(
        UUID id,
        Player requester,
        Player receiver,
        String message,
        PartnerRequestStatus status,
        Instant respondedAt,
        Instant createdAt) {

    public record Player(UUID id, String fullName, String avatarUrl) {
    }
}
