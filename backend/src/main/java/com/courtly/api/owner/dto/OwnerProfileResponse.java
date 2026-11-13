package com.courtly.api.owner.dto;

import java.time.Instant;

/**
 * 2.3.2 - ho so chu san.
 *
 * <p>Khong tra so CCCD (identity_number): man hinh khong can va day la du lieu nhay cam.
 *
 * @param verificationStatus pending, verified hoac rejected - do quan tri vien quyet dinh
 */
public record OwnerProfileResponse(String fullName,
                                   String email,
                                   String phone,
                                   String businessName,
                                   String taxCode,
                                   String bankName,
                                   String bankAccountNo,
                                   String bankAccountName,
                                   String verificationStatus,
                                   Instant verifiedAt,
                                   Instant updatedAt) {
}
