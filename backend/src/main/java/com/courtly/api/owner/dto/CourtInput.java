package com.courtly.api.owner.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Mot san con khi them san moi (2.3.3) hoac them san con (2.3.4).
 *
 * @param courtType   standard hoac vip (xem VenueCatalog)
 * @param surfaceType vinyl, wood, concrete hoac de trong
 */
public record CourtInput(

        @NotBlank(message = "Vui long nhap ten san con")
        @Size(max = 100, message = "Ten san con toi da 100 ky tu")
        String name,

        @NotBlank(message = "Vui long nhap ma san con")
        @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$",
                message = "Ma san con gom 1-20 ky tu chu, so, gach ngang hoac gach duoi")
        String courtCode,

        @NotBlank(message = "Vui long chon loai san")
        String courtType,

        String surfaceType,

        boolean indoor) {

    public CourtInput {
        name = name == null ? null : name.trim();
        courtCode = courtCode == null ? null : courtCode.trim().toUpperCase(java.util.Locale.ROOT);
        surfaceType = surfaceType == null || surfaceType.isBlank() ? null : surfaceType.trim();
    }
}
