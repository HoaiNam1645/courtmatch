package com.courtly.api.profile.dto;

import com.courtly.api.auth.dto.UserResponse;
import java.util.List;

/**
 * Toan bo du lieu ho so nguoi choi (2.1.6).
 *
 * <p>Mot lan goi phuc vu ca man hinh /profile lan /profile/preferences, tranh de
 * frontend phai ghep nhieu lan goi.
 */
public record ProfileResponse(UserResponse user,
                              PlayerProfileDto profile,
                              PlayerStatisticsDto statistics,
                              List<AvailabilitySlotDto> availability,
                              List<PreferredLocationDto> preferredLocations) {
}
