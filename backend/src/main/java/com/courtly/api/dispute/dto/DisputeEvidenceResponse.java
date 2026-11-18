package com.courtly.api.dispute.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Mot anh bang chung (2.3.88). {@code url} la duong dan API can dang nhap de lay anh, khong phai URL cong khai.
 *
 * @param supplementId lan bo sung (2.3.91) ma anh thuoc ve; null la anh cua bao cao ban dau
 */
public record DisputeEvidenceResponse(UUID id, String originalName, long size, String contentType, Instant uploadedAt,
                                      UUID supplementId, String url) {
}
