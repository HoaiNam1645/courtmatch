package com.courtly.api.dispute.dto;

/** Mot loai bao cao trong danh muc (2.3.86). {@code minDescriptionLength}: so ky tu mo ta toi thieu cho loai nay. */
public record DisputeCategoryResponse(String code, String label, String hint, int minDescriptionLength) {
}
