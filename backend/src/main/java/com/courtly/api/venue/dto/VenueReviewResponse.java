package com.courtly.api.venue.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Mot danh gia san hien tren trang chi tiet (2.1.23, 2.3.80).
 *
 * @param reviewerName ten nguoi danh gia; khong tra email hay so dien thoai
 * @param edited       danh gia da duoc nguoi viet chinh sua sau khi dang
 * @param mine         danh gia cua chinh nguoi dang xem (de hien nut sua/xoa); luon false khi chua dang nhap
 */
public record VenueReviewResponse(UUID id,
                                  String reviewerName,
                                  String reviewerAvatarUrl,
                                  short rating,
                                  String comment,
                                  Instant createdAt,
                                  boolean edited,
                                  boolean mine) {
}
