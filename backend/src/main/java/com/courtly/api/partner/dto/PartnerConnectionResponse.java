package com.courtly.api.partner.dto;

import java.time.Instant;
import java.util.UUID;

/** Ket noi doi tac dang hoat dong cua nguoi choi (2.2.36). */
public record PartnerConnectionResponse(
        UUID connectionId,
        UUID partnerId,
        String fullName,
        String avatarUrl,
        String playingStyle,
        String preferredPlayType,
        Instant connectedAt) {
}
