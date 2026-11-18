package com.courtly.service.dispute;

import com.courtly.api.dispute.dto.DisputeRefundResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.service.admin.AdminAccessService;
import com.courtly.service.admin.AdminRefundService;
import com.courtly.service.audit.AuditService;
import com.courtly.service.notification.DisputeNotifier;
import com.courtly.service.storage.DisputeEvidenceStorage;
import com.courtly.service.storage.VenueImageStorage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 2.3.108 - theo doi va xac nhan chu san da hoan tien truc tiep cho nguoi choi; 2.3.109 - dieu chinh hoa hong khi khoan hoan duoc xac nhan.
 *
 * <p>Courtly khong giu tien nen khong chuyen tien. Vong doi khoan hoan tao o 2.3.100/101/107:
 * {@code awaiting_owner_refund} -> (chu san bao da hoan kem chung tu) {@code owner_reported_refunded} -> (nguoi choi xac nhan,
 * quan tri vien xac nhan thay, hoac tu xac nhan sau {@code autoConfirmDays} ngay) {@code confirmed}. Nguoi choi khieu nai thi quay lai
 * {@code awaiting_owner_refund}. Chi khi {@code confirmed} moi cap nhat trang thai thanh toan va dieu chinh hoa hong, dung mot lan nho
 * khoa dong khoan hoan.
 */
@Service
public class DisputeRefundService {

    static final int MIN_TEXT = 5;
    static final int MAX_TEXT = 1000;
    static final long MAX_BYTES = 5L * 1024 * 1024;

    private final JdbcTemplate jdbc;
    private final DisputeEvidenceStorage storage;
    private final AdminAccessService accessService;
    private final AdminRefundService adminRefundService;
    private final AuditService auditService;
    private final DisputeNotifier notifier;
    private final UserRepository userRepository;
    private final int autoConfirmDays;

    public DisputeRefundService(JdbcTemplate jdbc, DisputeEvidenceStorage storage, AdminAccessService accessService,
                                AdminRefundService adminRefundService, AuditService auditService, DisputeNotifier notifier,
                                UserRepository userRepository,
                                @Value("${courtly.disputes.refund-auto-confirm-days:7}") int autoConfirmDays) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.accessService = accessService;
        this.adminRefundService = adminRefundService;
        this.auditService = auditService;
        this.notifier = notifier;
        this.userRepository = userRepository;
        this.autoConfirmDays = autoConfirmDays;
    }

    private record Row(UUID id, UUID disputeId, UUID bookingId, UUID paymentId, BigDecimal amount, String status, UUID reporterId,
                       UUID ownerId, String bookingCode, Instant createdAt, Instant ownerReportedAt, String ownerNote, String proof,
                       String complaint, Instant complainedAt, Instant confirmedAt, boolean auto) {
    }

    private static final String SELECT = """
            SELECT r.id, r.dispute_id, r.booking_id, r.payment_id, r.amount, r.status, d.reporter_id, v.owner_id, b.booking_code,
                   r.created_at, r.owner_reported_at, r.owner_note, r.proof_url, r.player_complaint, r.complained_at, r.confirmed_at,
                   r.auto_confirmed
            FROM refunds r JOIN booking_disputes d ON d.id = r.dispute_id JOIN bookings b ON b.id = r.booking_id
            JOIN venues v ON v.id = b.venue_id
            """;

    private Row load(String where, boolean lock, Object... args) {
        List<Row> rows = jdbc.query(SELECT + " WHERE " + where + (lock ? " FOR UPDATE OF r" : ""), (rs, n) -> new Row(
                rs.getObject("id", UUID.class), rs.getObject("dispute_id", UUID.class), rs.getObject("booking_id", UUID.class),
                rs.getObject("payment_id", UUID.class), rs.getBigDecimal("amount"), rs.getString("status"),
                rs.getObject("reporter_id", UUID.class), rs.getObject("owner_id", UUID.class), rs.getString("booking_code"),
                instant(rs.getTimestamp("created_at")), instant(rs.getTimestamp("owner_reported_at")), rs.getString("owner_note"),
                rs.getString("proof_url"), rs.getString("player_complaint"), instant(rs.getTimestamp("complained_at")),
                instant(rs.getTimestamp("confirmed_at")), rs.getBoolean("auto_confirmed")), args);
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private static Instant instant(Timestamp value) {
        return value == null ? null : value.toInstant();
    }

    /** Khoan hoan cua bao cao, nhin tu nguoi bao cao, chu san hoac quan tri vien; nguoi khac 404. Chua co khoan hoan thi tra null. */
    @Transactional(readOnly = true)
    public DisputeRefundResponse view(UUID userId, UUID disputeId) {
        Row row = load("r.dispute_id = ? ORDER BY r.created_at DESC LIMIT 1", false, disputeId);
        if (row == null) {
            Integer exists = jdbc.queryForObject("SELECT count(*) FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id JOIN venues v ON v.id = b.venue_id WHERE d.id = ? AND (d.reporter_id = ? OR v.owner_id = ?)",
                    Integer.class, disputeId, userId, userId);
            if (exists == 0 && !isAdmin(userId)) {
                throw ApiException.notFound("Khong tim thay bao cao.");
            }
            return null;
        }
        boolean admin = isAdmin(userId);
        if (!admin && !userId.equals(row.reporterId()) && !userId.equals(row.ownerId())) {
            throw ApiException.notFound("Khong tim thay bao cao.");
        }
        return toResponse(row, userId, admin);
    }

    private DisputeRefundResponse toResponse(Row row, UUID userId, boolean admin) {
        boolean open = !"confirmed".equals(row.status());
        return new DisputeRefundResponse(row.id(), row.amount(), row.status(), row.createdAt(), row.ownerReportedAt(), row.ownerNote(),
                row.proof() == null ? null : "/api/v1/refunds/" + row.id() + "/proof", row.complaint(), row.complainedAt(),
                row.confirmedAt(), row.auto(),
                "owner_reported_refunded".equals(row.status()) && row.ownerReportedAt() != null
                        ? row.ownerReportedAt().plus(Duration.ofDays(autoConfirmDays)) : null,
                "awaiting_owner_refund".equals(row.status()) && userId.equals(row.ownerId()),
                "owner_reported_refunded".equals(row.status()) && userId.equals(row.reporterId()),
                open && admin);
    }

    private boolean isAdmin(UUID userId) {
        return accessService.isActiveAdmin(userId);
    }

    /** Chu san bao da hoan: bat buoc 1 anh chung tu, ghi chu tuy chon. Khoan hoan sau khieu nai co the bao lai. */
    @Transactional
    public DisputeRefundResponse ownerReport(UUID ownerId, UUID refundId, String note, MultipartFile file) {
        Row row = load("r.id = ?", true, refundId);
        if (row == null || !row.ownerId().equals(ownerId)) {
            throw ApiException.notFound("Khong tim thay khoan hoan tien.");
        }
        if (!"awaiting_owner_refund".equals(row.status())) {
            throw invalid("Khoan hoan tien nay khong con cho ban bao da hoan.");
        }
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        if (cleanNote != null && cleanNote.length() > MAX_TEXT) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ghi chu qua dai.", Map.of("note", "Ghi chu toi da " + MAX_TEXT + " ky tu"));
        }
        if (file == null || file.isEmpty()) {
            throw badImage("Hay dinh kem anh chung tu da hoan tien.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw badImage("Anh vuot qua dung luong toi da 5MB.");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
        String extension = VenueImageStorage.detectExtension(bytes).orElseThrow(() -> badImage("Chi nhan anh JPEG, PNG hoac WebP."));
        String fileName = storage.store(row.disputeId(), bytes, extension);
        if (row.proof() != null) {
            storage.delete(row.disputeId(), row.proof());
        }
        jdbc.update("UPDATE refunds SET status = 'owner_reported_refunded', owner_reported_at = now(), owner_note = ?, proof_url = ?, player_complaint = NULL WHERE id = ?",
                cleanNote, fileName, refundId);
        User owner = userRepository.findById(ownerId).orElse(null);
        auditService.record(owner, "refund.owner_report", "refunds", refundId, Map.of("status", row.status()),
                Map.of("status", "owner_reported_refunded", "disputeId", row.disputeId().toString(), "amount", row.amount()));
        notifier.refundReported(row.disputeId(), row.bookingId(), row.bookingCode(), row.reporterId(), row.amount(), autoConfirmDays);
        return view(ownerId, row.disputeId());
    }

    @Transactional
    public DisputeRefundResponse playerConfirm(UUID userId, UUID refundId) {
        Row row = load("r.id = ?", true, refundId);
        if (row == null || !row.reporterId().equals(userId)) {
            throw ApiException.notFound("Khong tim thay khoan hoan tien.");
        }
        if (!"owner_reported_refunded".equals(row.status())) {
            throw invalid("Chi xac nhan duoc khi chu san da bao hoan tien.");
        }
        confirmCore(row, userRepository.findById(userId).orElse(null), false, null);
        return view(userId, row.disputeId());
    }

    @Transactional
    public DisputeRefundResponse playerComplain(UUID userId, UUID refundId, String reason) {
        Row row = load("r.id = ?", true, refundId);
        if (row == null || !row.reporterId().equals(userId)) {
            throw ApiException.notFound("Khong tim thay khoan hoan tien.");
        }
        if (!"owner_reported_refunded".equals(row.status())) {
            throw invalid("Chi khieu nai duoc khi chu san da bao hoan tien.");
        }
        String clean = requireText(reason, "reason", "Ly do");
        jdbc.update("UPDATE refunds SET status = 'awaiting_owner_refund', player_complaint = ?, complained_at = now(), owner_reported_at = NULL WHERE id = ?",
                clean, refundId);
        auditService.record(userRepository.findById(userId).orElse(null), "refund.player_complaint", "refunds", refundId,
                Map.of("status", "owner_reported_refunded"), Map.of("status", "awaiting_owner_refund", "reason", clean));
        notifier.refundComplained(row.disputeId(), row.bookingId(), row.bookingCode(), row.ownerId(), row.amount(), clean);
        return view(userId, row.disputeId());
    }

    @Transactional
    public DisputeRefundResponse adminConfirm(UUID adminId, UUID refundId, String note) {
        User admin = accessService.requirePermission(adminId, "refund.process");
        Row row = load("r.id = ?", true, refundId);
        if (row == null) {
            throw ApiException.notFound("Khong tim thay khoan hoan tien.");
        }
        if ("confirmed".equals(row.status())) {
            throw invalid("Khoan hoan tien nay da duoc xac nhan.");
        }
        confirmCore(row, admin, false, requireText(note, "note", "Ghi chu"));
        return view(adminId, row.disputeId());
    }

    /** Tu xac nhan cac khoan hoan chu san da bao tu {@code autoConfirmDays} ngay ma nguoi choi khong phan hoi (job nen). */
    @Transactional
    public int autoConfirmDue(Instant now) {
        List<UUID> due = jdbc.queryForList("SELECT id FROM refunds WHERE status = 'owner_reported_refunded' AND dispute_id IS NOT NULL AND owner_reported_at <= ? ORDER BY owner_reported_at",
                UUID.class, Timestamp.from(now.minus(Duration.ofDays(autoConfirmDays))));
        int done = 0;
        for (UUID id : due) {
            Row row = load("r.id = ?", true, id);
            if (row != null && "owner_reported_refunded".equals(row.status())) {
                confirmCore(row, null, true, null);
                done++;
            }
        }
        return done;
    }

    /** Xac nhan khoan hoan: thanh toan, hoa hong (2.3.109), audit va thong bao; chay dung mot lan nho khoa dong. */
    private void confirmCore(Row row, User actor, boolean auto, String note) {
        BigDecimal paid = jdbc.queryForObject("SELECT amount FROM payments WHERE id = ? FOR UPDATE", BigDecimal.class, row.paymentId());
        BigDecimal before = jdbc.queryForObject("SELECT coalesce(sum(amount), 0) FROM refunds WHERE payment_id = ? AND status = 'confirmed'",
                BigDecimal.class, row.paymentId());
        BigDecimal after = before.add(row.amount());
        String suffix = note == null ? "" : " | Xac nhan: " + note;
        jdbc.update("UPDATE refunds SET status = 'confirmed', confirmed_by = ?, confirmed_at = now(), auto_confirmed = ?, reason = coalesce(reason, '') || ? WHERE id = ?",
                actor == null ? null : actor.getId(), auto, suffix, row.id());
        jdbc.update("UPDATE payments SET status = ? WHERE id = ?", after.compareTo(paid) >= 0 ? "refunded" : "partially_refunded", row.paymentId());
        boolean adjusted = adminRefundService.applyDisputeRefund(actor, row.bookingId(), row.id(), row.amount(), paid, before,
                "Hoan tien theo bao cao " + row.disputeId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "confirmed");
        data.put("auto", auto);
        data.put("amount", row.amount());
        data.put("commissionAdjusted", adjusted);
        if (note != null) {
            data.put("note", note);
        }
        auditService.record(actor, auto ? "refund.auto_confirm" : "refund.confirm", "refunds", row.id(), Map.of("status", row.status()), data);
        notifier.refundConfirmed(row.disputeId(), row.bookingId(), row.bookingCode(), row.ownerId(), row.reporterId(), row.amount(), auto, adjusted);
    }

    /** Anh chung tu: nguoi bao cao, chu san hoac quan tri vien. */
    @Transactional(readOnly = true)
    public EvidenceFile proof(UUID userId, UUID refundId) {
        Row row = load("r.id = ?", false, refundId);
        if (row == null || row.proof() == null || (!userId.equals(row.reporterId()) && !userId.equals(row.ownerId()) && !isAdmin(userId))) {
            throw ApiException.notFound("Khong tim thay chung tu.");
        }
        return new EvidenceFile(storage.resolveExisting(row.disputeId(), row.proof()), VenueImageStorage.contentTypeOf(row.proof()));
    }

    public record EvidenceFile(Path path, String contentType) {
    }

    private static String requireText(String value, String field, String label) {
        String clean = value == null ? "" : value.trim();
        if (clean.length() < MIN_TEXT || clean.length() > MAX_TEXT) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, label + " khong hop le.",
                    Map.of(field, label + " tu " + MIN_TEXT + " den " + MAX_TEXT + " ky tu"));
        }
        return clean;
    }

    private static ApiException invalid(String message) {
        return ApiException.conflict(ErrorCode.BOOKING_DISPUTE_INVALID, message, Map.of());
    }

    private static ApiException badImage(String message) {
        return ApiException.badRequest(ErrorCode.IMAGE_INVALID, message, Map.of("file", message));
    }
}
