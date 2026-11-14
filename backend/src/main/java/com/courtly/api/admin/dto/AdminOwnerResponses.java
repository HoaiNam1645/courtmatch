package com.courtly.api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/** 2.3.32 - quan ly tai khoan chu san duy nhat. */
public final class AdminOwnerResponses {

    private AdminOwnerResponses() {
    }

    /**
     * @param verificationStatus pending, verified hoac rejected
     * @param verificationNote   ly do tu choi / dat lai; null neu chua co
     * @param verifiedBy         ten quan tri vien quyet dinh; null neu chua co
     */
    public record Profile(String businessName, String taxCode, String identityNumber, String bankName,
                          String bankAccountNo, String bankAccountName, String verificationStatus,
                          Instant verifiedAt, String verificationNote, String verifiedBy) {
    }

    /**
     * @param venuesByApproval so san theo pending, approved, rejected (du moi khoa)
     * @param outstandingCommission cong no hoa hong hien tai (2.3.20)
     * @param activeBookings booking dang xu ly
     */
    public record Overview(Map<String, Long> venuesByApproval, BigDecimal outstandingCommission, long activeBookings) {
    }

    /** @param owner null khi he thong chua cap tai khoan chu san */
    public record Detail(AdminUserResponses.Detail owner, Profile profile, Overview overview) {
    }

    /**
     * Ket qua cap hoac dat lai thong tin dang nhap.
     *
     * @param temporaryPassword mat khau tam, chi hien mot lan; he thong khong luu va khong ghi log ban ro
     */
    public record Credentials(Detail detail, String temporaryPassword) {
    }
}
