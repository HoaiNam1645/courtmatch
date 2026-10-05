package com.courtly.api.venue.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Mot danh gia san hien tren trang chi tiet (2.1.23).
 *
 * @param reviewerName ten nguoi danh gia; khong tra email hay so dien thoai
 */
public record VenueReviewResponse(UUID id,
                                  String reviewerName,
                                  String reviewerAvatarUrl,
                                  short rating,
                                  String comment,
                                  Instant createdAt) {
}
