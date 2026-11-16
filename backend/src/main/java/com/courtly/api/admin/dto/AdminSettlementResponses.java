package com.courtly.api.admin.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** 2.3.45 - 2.3.46: ky cong no hoa hong cua chu san, goc nhin quan tri vien. */
public final class AdminSettlementResponses {

    private AdminSettlementResponses() {
    }

    /**
     * @param status       open, pending_review, partial, paid, overdue, disputed
     * @param remaining    amountDue - amountPaid, khong am
     * @param overdue      con no va co hoa hong thuoc ky da qua han thanh toan
     * @param bookingCount so booking thuoc ky
     * @param pendingReports so bao cao thanh toan cua chu san dang cho xac nhan
     */
    public record Row(UUID id, UUID ownerId, String ownerName, String ownerEmail, LocalDate periodFrom, LocalDate periodTo,
                      BigDecimal commissionTotal, BigDecimal adjustmentTotal, BigDecimal amountDue, BigDecimal amountPaid,
                      BigDecimal remaining, String status, boolean overdue, long bookingCount, long pendingReports,
                      Instant createdAt, Instant paidAt) {
    }

    public record Totals(long count, BigDecimal amountDue, BigDecimal amountPaid, BigDecimal remaining) {
    }

    public record OwnerOption(UUID id, String fullName, String email) {
    }

    /** Tong so ky theo tung trang thai, ap dung moi bo loc tru bo loc trang thai. */
    public record ListResponse(List<Row> content, int page, int size, long totalElements, int totalPages, Totals totals,
                               Map<String, Long> statusCounts, List<OwnerOption> owners) {
    }

    /** Bao cao thanh toan cua chu san thuoc ky. */
    public record Report(UUID id, BigDecimal amount, LocalDate paidOn, String referenceCode, String proofUrl, String note,
                         String status, String reviewNote, String reviewedByName, Instant reviewedAt, Instant createdAt) {
    }

    /** Booking cau thanh ky: lay truc tiep tu hoa hong co settlement_id = ky. */
    public record Booking(UUID commissionId, UUID bookingId, String bookingCode, String venueName, BigDecimal grossAmount,
                          BigDecimal commissionAmount, String status, Instant dueAt, boolean overdue,
                          boolean unconfirmedPayment) {
    }

    public record BookingPage(List<Booking> content, int page, int size, long totalElements, int totalPages) {
    }

    public record HistoryEntry(UUID id, String action, String actorName, Object oldData, Object newData, Instant createdAt) {
    }

    /**
     * @param restrictionLevel muc han che hien tai cua chu san (B2): 0, 1 hoac 2
     * @param owedByCourtly    tong khoan Courtly con no chu san chua bu tru
     */
    public record Detail(Row settlement, String ownerPhone, int restrictionLevel, BigDecimal owedByCourtly,
                         List<Report> reports, BookingPage bookings, List<HistoryEntry> history) {
    }
}
