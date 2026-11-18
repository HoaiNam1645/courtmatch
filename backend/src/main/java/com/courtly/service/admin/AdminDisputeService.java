package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminDisputeResponses.CommissionInfo;
import com.courtly.api.admin.dto.AdminDisputeResponses.Detail;
import com.courtly.api.admin.dto.AdminDisputeResponses.History;
import com.courtly.api.admin.dto.AdminDisputeResponses.Item;
import com.courtly.api.admin.dto.AdminDisputeResponses.ListResponse;
import com.courtly.api.admin.dto.AdminDisputeResponses.Money;
import com.courtly.api.admin.dto.AdminDisputeResponses.Party;
import com.courtly.api.admin.dto.AdminDisputeResponses.PaymentInfo;
import com.courtly.api.dispute.dto.DisputeDetailResponse;
import com.courtly.api.dispute.dto.DisputeDetailResponse.TimelineEntry;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.booking.BookingItem;
import com.courtly.domain.payment.BookingDispute;
import com.courtly.domain.payment.BookingDisputeRepository;
import com.courtly.service.booking.AvailabilityService;
import com.courtly.service.dispute.DisputeCategories;
import com.courtly.service.dispute.DisputeQueryService;
import com.courtly.service.dispute.DisputeRefundAmounts;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.98 - 2.3.99: quan tri vien xem bao cao ve don dat san (danh sach va chi tiet kem bang chung hai phia). Chi doc;
 * quyet dinh hoan tien la 2.3.100-102. Dung quyen {@code refund.process} co san.
 */
@Service
@RequiredArgsConstructor
public class AdminDisputeService {

    static final String PERMISSION = "refund.process";

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;
    private final BookingDisputeRepository disputeRepository;

    @Transactional(readOnly = true)
    public ListResponse list(UUID adminId, String status, String q, String category, String refund, UUID venueId, LocalDate from, LocalDate to,
                             int page, int size) {
        accessService.requirePermission(adminId, PERMISSION);
        String group = status == null || status.isBlank() ? "pending" : status;
        String statusSql = switch (group) {
            case "pending" -> "d.status = 'reviewing'";
            case "awaiting" -> "d.status IN ('open', 'awaiting_owner_response')";
            case "resolved", "rejected", "cancelled" -> "d.status = '" + group + "'";
            case "refunding" -> "d.status = 'resolved' AND EXISTS (SELECT 1 FROM refunds rf WHERE rf.dispute_id = d.id AND rf.status IN ('awaiting_owner_refund', 'owner_reported_refunded'))";
            case "all" -> "TRUE";
            default -> throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai khong hop le.",
                    Map.of("status", "Chi nhan pending, awaiting, refunding, resolved, rejected, cancelled hoac all"));
        };
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khoang ngay khong hop le.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        List<Object> args = new ArrayList<>();
        StringBuilder where = new StringBuilder(statusSql);
        if (q != null && !q.isBlank()) {
            String like = "%" + q.trim().toLowerCase().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
            where.append(" AND (lower(b.booking_code) LIKE ? OR lower(r.full_name) LIKE ? OR lower(v.name) LIKE ?)");
            args.add(like);
            args.add(like);
            args.add(like);
        }
        if (category != null && !category.isBlank() && !"all".equals(category)) {
            where.append(" AND d.category = ?");
            args.add(category);
        }
        if (venueId != null) {
            where.append(" AND v.id = ?");
            args.add(venueId);
        }
        if ("yes".equals(refund)) {
            where.append(" AND d.refund_requested");
        } else if ("no".equals(refund)) {
            where.append(" AND NOT d.refund_requested");
        }
        if (from != null) {
            where.append(" AND d.created_at >= ?");
            args.add(java.sql.Timestamp.from(from.atStartOfDay(AvailabilityService.ZONE).toInstant()));
        }
        if (to != null) {
            where.append(" AND d.created_at < ?");
            args.add(java.sql.Timestamp.from(to.plusDays(1).atStartOfDay(AvailabilityService.ZONE).toInstant()));
        }
        String from_ = """
                FROM booking_disputes d
                JOIN bookings b ON b.id = d.booking_id
                JOIN venues v ON v.id = b.venue_id
                JOIN users o ON o.id = v.owner_id
                JOIN users r ON r.id = d.reporter_id
                """;
        String order = switch (group) {
            case "pending" -> "d.owner_responded_at ASC NULLS LAST, d.created_at ASC";
            case "awaiting" -> "d.owner_response_deadline ASC NULLS LAST, d.created_at ASC";
            case "refunding" -> "d.handled_at ASC NULLS LAST, d.created_at ASC";
            default -> "d.created_at DESC";
        };
        Long total = jdbc.queryForObject("SELECT count(*) " + from_ + " WHERE " + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((long) page * size);
        List<Item> items = jdbc.query("""
                SELECT d.id, b.id AS booking_id, b.booking_code, v.name AS venue_name, o.full_name AS owner_name, r.full_name AS player_name,
                       d.category, d.status, d.refund_requested, d.requested_refund_amount,
                       COALESCE(jsonb_array_length(d.reporter_evidences_json), 0) AS player_images,
                       COALESCE(jsonb_array_length(d.owner_evidences_json), 0) AS owner_images,
                       d.created_at, COALESCE(d.owner_responded_at, d.created_at) AS waiting_since, d.resolution,
                       (SELECT rf.status FROM refunds rf WHERE rf.dispute_id = d.id ORDER BY rf.created_at DESC LIMIT 1) AS refund_status,
                       (SELECT count(*) FROM booking_disputes p JOIN bookings pb ON pb.id = p.booking_id
                        WHERE pb.venue_id = v.id AND p.id <> d.id AND p.created_at < d.created_at) AS prior
                """ + from_ + " WHERE " + where + " ORDER BY " + order + ", d.id LIMIT ? OFFSET ?",
                (rs, row) -> new Item(rs.getObject("id", UUID.class), rs.getObject("booking_id", UUID.class), rs.getString("booking_code"),
                        rs.getString("venue_name"), rs.getString("owner_name"), rs.getString("player_name"), rs.getString("category"),
                        categoryLabel(rs.getString("category")), rs.getString("status"), rs.getBoolean("refund_requested"),
                        rs.getBigDecimal("requested_refund_amount"), rs.getInt("player_images"), rs.getInt("owner_images"),
                        instant(rs.getObject("created_at", OffsetDateTime.class)), instant(rs.getObject("waiting_since", OffsetDateTime.class)),
                        rs.getString("resolution"), rs.getLong("prior"), rs.getString("refund_status")), pageArgs.toArray());
        int totalPages = (int) Math.ceil(total / (double) size);
        return new ListResponse(items, page, size, total, totalPages, counts());
    }

    @Transactional(readOnly = true)
    public Detail get(UUID adminId, UUID disputeId) {
        accessService.requirePermission(adminId, PERMISSION);
        BookingDispute dispute = disputeRepository.findWithParties(disputeId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay bao cao."));
        return toDetail(dispute);
    }

    /** Dung chung cho 2.3.100-102 de tra ve chi tiet moi sau khi quyet dinh. */
    @Transactional(readOnly = true)
    public Detail toDetail(BookingDispute dispute) {
        DisputeDetailResponse view = DisputeQueryService.toDetail(dispute, true);
        var booking = dispute.getBooking();
        var venue = booking.getVenue();
        User owner = venue.getOwner();
        User player = dispute.getReporter();
        BookingItem first = booking.getItems().stream().min(Comparator.comparing(BookingItem::getStartTime)).orElse(null);
        BookingItem last = booking.getItems().stream().max(Comparator.comparing(BookingItem::getEndTime)).orElse(null);

        List<PaymentInfo> payments = jdbc.query("SELECT method, amount, status, created_at FROM payments WHERE booking_id = ? ORDER BY created_at",
                (rs, row) -> new PaymentInfo(rs.getString("method"), rs.getBigDecimal("amount"), rs.getString("status"),
                        instant(rs.getObject("created_at", OffsetDateTime.class))), booking.getId());
        BigDecimal paid = jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM payments WHERE booking_id = ? AND status IN ('confirmed', 'partially_refunded', 'refunded')",
                BigDecimal.class, booking.getId());
        BigDecimal refunded = jdbc.queryForObject("SELECT COALESCE(sum(amount), 0) FROM refunds WHERE booking_id = ? AND status NOT IN ('rejected', 'failed')",
                BigDecimal.class, booking.getId());
        BigDecimal refundable = paid.subtract(refunded).max(BigDecimal.ZERO);
        Money money = new Money(booking.getPayableAmount(), paid, refunded, refundable, payments);

        CommissionInfo commission = jdbc.query("SELECT id, status, commission_amount, voucher_discount FROM booking_commissions WHERE booking_id = ?",
                rs -> rs.next() ? new CommissionInfo(rs.getObject("id", UUID.class), rs.getString("status"),
                        rs.getBigDecimal("commission_amount"), rs.getBigDecimal("voucher_discount")) : null, booking.getId());

        History history = jdbc.queryForObject("""
                SELECT count(*) AS total,
                       count(*) FILTER (WHERE d.status = 'resolved' AND d.resolution IN ('refund_full', 'refund_half')) AS upheld,
                       count(*) FILTER (WHERE d.status = 'rejected') AS rejected
                FROM booking_disputes d JOIN bookings b ON b.id = d.booking_id WHERE b.venue_id = ?
                """, (rs, row) -> new History(rs.getLong("total"), rs.getLong("upheld"), rs.getLong("rejected"),
                jdbc.queryForObject("SELECT count(*) FROM booking_disputes WHERE reporter_id = ?", Long.class, player.getId())), venue.getId());

        boolean canDecide = "reviewing".equals(dispute.getStatus());
        BigDecimal requested = dispute.isRefundRequested() ? dispute.getRequestedRefundAmount() : null;
        boolean canRefund = canDecide && requested != null && refundable.signum() > 0 && paid.signum() > 0;
        BigDecimal full = requested == null ? null : DisputeRefundAmounts.full(requested, refundable);
        BigDecimal half = requested == null ? null : DisputeRefundAmounts.half(requested, refundable);

        List<TimelineEntry> timeline = view.timeline().stream()
                .map(entry -> "you".equals(entry.actor())
                        ? new TimelineEntry(entry.type(), "owner", entry.at(), entry.text(), entry.label(), entry.evidences()) : entry)
                .toList();
        return new Detail(dispute.getId(), dispute.getCategory(), view.categoryLabel(), dispute.getStatus(), view.closed(),
                dispute.getResolution(), dispute.getCreatedAt(), dispute.getOwnerResponseDeadline(), dispute.isRefundRequested(),
                dispute.getRequestedRefundAmount(), booking.getId(), booking.getBookingCode(), venue.getName(),
                first == null ? null : first.getCourt().getName(), first == null ? null : first.getStartTime(),
                last == null ? null : last.getEndTime(),
                new Party(player.getId(), player.getFullName(), player.getPhone(), player.getEmail()),
                new Party(owner.getId(), owner.getFullName(), owner.getPhone(), owner.getEmail()),
                money, commission, history, timeline, canDecide, canRefund, canRefund ? full : null, canRefund ? half : null);
    }

    private Map<String, Long> counts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("pending", 0L);
        counts.put("awaiting", 0L);
        counts.put("resolved", 0L);
        counts.put("rejected", 0L);
        counts.put("cancelled", 0L);
        jdbc.query("SELECT status, count(*) AS total FROM booking_disputes GROUP BY status", rs -> {
            String key = switch (rs.getString("status")) {
                case "reviewing" -> "pending";
                case "open", "awaiting_owner_response" -> "awaiting";
                default -> rs.getString("status");
            };
            counts.merge(key, rs.getLong("total"), Long::sum);
        });
        counts.put("all", counts.values().stream().mapToLong(Long::longValue).sum());
        counts.put("refunding", jdbc.queryForObject("SELECT count(DISTINCT d.id) FROM booking_disputes d JOIN refunds rf ON rf.dispute_id = d.id WHERE d.status = 'resolved' AND rf.status IN ('awaiting_owner_refund', 'owner_reported_refunded')", Long.class));
        return counts;
    }

    private static String categoryLabel(String code) {
        var category = DisputeCategories.find(code);
        return category == null ? code : category.label();
    }

    private static Instant instant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }
}
