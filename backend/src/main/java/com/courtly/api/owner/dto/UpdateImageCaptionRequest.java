package com.courtly.api.owner.dto;

import jakarta.validation.constraints.Size;

/** 2.3.6 - sua chu thich anh; de trong de xoa chu thich. */
public record UpdateImageCaptionRequest(
        @Size(max = 255, message = "Chu thich toi da 255 ky tu")
        String caption) {
}
