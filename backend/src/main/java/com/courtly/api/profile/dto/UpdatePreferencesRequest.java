package com.courtly.api.profile.dto;

import com.courtly.common.enums.DominantHand;
import com.courtly.common.enums.PlayType;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.common.enums.SkillLevel;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Size;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Thiet lap choi (2.1.8, 2.1.9, 2.1.10, 2.1.11, 2.1.12) - man hinh /profile/preferences.
 *
 * <p>Man hinh nay chi co mot nut "Luu thiet lap" nen API nhan tron mot lan.
 * {@code availability} va {@code preferredLocations} duoc ghi de toan bo danh sach,
 * khong phai cap nhat tung dong.
 */
public record UpdatePreferencesRequest(

        SkillLevel skillLevel,

        DominantHand dominantHand,

        PlayingStyle playingStyle,

        PlayType preferredPlayType,

        @Valid
        @Size(max = 7, message = "Toi da 7 khung gio, moi thu mot khung")
        List<AvailabilitySlotDto> availability,

        @Valid
        @Size(max = 10, message = "Toi da 10 dia diem uu tien")
        List<PreferredLocationDto> preferredLocations) {

    public UpdatePreferencesRequest {
        availability = availability == null ? List.of() : List.copyOf(availability);
        preferredLocations = preferredLocations == null ? List.of() : List.copyOf(preferredLocations);
    }

    @JsonIgnore
    @AssertTrue(message = "Moi thu trong tuan chi duoc khai bao mot khung gio")
    public boolean isDayOfWeekUnique() {
        Set<Short> seen = new HashSet<>();
        return availability.stream()
                .map(AvailabilitySlotDto::dayOfWeek)
                .filter(day -> day != null)
                .allMatch(seen::add);
    }

    @JsonIgnore
    @AssertTrue(message = "Chi duoc chon mot dia diem mac dinh")
    public boolean isDefaultLocationUnique() {
        return preferredLocations.stream().filter(PreferredLocationDto::isDefault).count() <= 1;
    }
}
