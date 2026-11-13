package com.courtly.api.owner.dto;

import java.util.UUID;

/** Mot anh cua san (2.3.6), sap theo displayOrder; cover = anh dai dien. */
public record VenueImageItem(UUID id, String imageUrl, String caption, int displayOrder, boolean cover) {
}
