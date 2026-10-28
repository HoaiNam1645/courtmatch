package com.courtly.api.match.dto;

import java.util.UUID;

public record MatchPlayerOptionResponse(UUID id, String fullName, String avatarUrl) {
}
