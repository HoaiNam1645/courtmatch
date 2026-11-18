package com.courtly.api.admin.dto;

import com.courtly.api.dispute.dto.DisputeDetailResponse.TimelineEntry;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 2.3.98 - 2.3.99: quan tri vien xem bao cao ve don dat san. */
public final class AdminDisputeResponses {

    private AdminDisputeResponses() {
    }

    /**
     * @param counts   so bao cao theo nhom: pending (chu san da giai trinh), awaiting, resolved, rejected, cancelled, all
     * @param waitingSince moc bat dau cho quan tri vien: luc chu san giai trinh (pending) hoac luc gui bao cao
     * @param priorVenueDisputes so bao cao truoc do cua cung san
     */
    public record Item(UUID id, UUID bookingId, String bookingCode, String venueName, String ownerName, String playerName,
                       String category, String categoryLabel, String status, boolean refundRequested, BigDecimal refundAmount,
                       int playerImages, int ownerImages, Instant createdAt, Instant waitingSince, String resolution,
                       long priorVenueDisputes, String refundStatus) {
    }

    public record ListResponse(List<Item> items, int page, int size, long totalElements, int totalPages, Map<String, Long> counts) {
    }

    public record Party(UUID id, String fullName, String phone, String email) {
    }

    public record PaymentInfo(String method, BigDecimal amount, String status, Instant createdAt) {
    }

    /** @param refundedBefore tong so da hoan truoc do (khong tinh yeu cau bi tu choi/that bai) */
    public record Money(BigDecimal payable, BigDecimal confirmedPaid, BigDecimal refundedBefore, BigDecimal refundable,
                        List<PaymentInfo> payments) {
    }

    public record CommissionInfo(UUID id, String status, BigDecimal amount, BigDecimal voucherDiscount) {
    }

    /**
     * @param venueDisputes  tong so bao cao ve san (ke ca bao cao nay)
     * @param venueUpheld    so bao cao san bi xu thua (hoan tien toan phan hoac mot nua)
     * @param venueRejected  so bao cao bi bac bo
     * @param reporterDisputes so bao cao cua nguoi choi nay (ke ca bao cao nay)
     */
    public record History(long venueDisputes, long venueUpheld, long venueRejected, long reporterDisputes) {
    }

    /**
     * @param fullAmount so tien se hoan neu hoan toan phan (null khi khong co yeu cau hoan tien)
     * @param halfAmount so tien se hoan neu hoan mot nua
     * @param canDecide  dang cho quan tri vien quyet dinh (reviewing)
     * @param canRefund  duoc quyet dinh hoan tien: co yeu cau hoan tien va don co khoan thanh toan da xac nhan con co the hoan
     */
    public record Detail(UUID id, String category, String categoryLabel, String status, boolean closed, String resolution,
                         Instant createdAt, Instant ownerResponseDeadline, boolean refundRequested, BigDecimal refundAmount,
                         UUID bookingId, String bookingCode, String venueName, String courtName, Instant startTime, Instant endTime,
                         Party player, Party owner, Money money, CommissionInfo commission, History history,
                         List<TimelineEntry> timeline, boolean canDecide, boolean canRefund,
                         BigDecimal fullAmount, BigDecimal halfAmount) {
    }
}
