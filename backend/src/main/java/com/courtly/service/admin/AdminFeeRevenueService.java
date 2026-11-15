package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminFeeRevenueResponse;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.42 - quan tri vien xem tong doanh thu phi nen tang.
 *
 * <p>Tinh truc tiep tu hoa hong tung booking (booking_commissions), khong duy tri so cai rieng. Ngay tinh
 * theo gio Viet Nam theo thoi diem ghi nhan hoa hong (calculated_at). Courtly khong giu tien san cua
 * nguoi choi nen day chi la phi nen tang thu tu chu san, khong phai tong doanh thu dat san.
 */
@Service
@RequiredArgsConstructor
public class AdminFeeRevenueService {

    static final String PERMISSION = "finance.commission.view";
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private static final String RECORDED = "bc.status IN ('pending', 'due', 'adjusted', 'settled')";
    private static final String OUTSTANDING = "bc.status IN ('pending', 'due', 'adjusted')";
    private static final String FROM_CLAUSE = """
             FROM booking_commissions bc
             JOIN bookings b ON b.id = bc.booking_id
             JOIN venues v ON v.id = b.venue_id
            """;
    private static final String SUMS = """
            count(*) FILTER (WHERE %1$s) AS booking_count,
            coalesce(sum(bc.gross_amount) FILTER (WHERE %1$s), 0) AS gross_amount,
            coalesce(sum(bc.commission_amount) FILTER (WHERE %1$s), 0) AS recorded,
            coalesce(sum(bc.commission_amount) FILTER (WHERE bc.status = 'settled'), 0) AS collected,
            coalesce(sum(bc.commission_amount) FILTER (WHERE %2$s), 0) AS outstanding
            """.formatted(RECORDED, OUTSTANDING);

    private final AdminAccessService accessService;
    private final JdbcTemplate jdbc;

    /**
     * @param from        ngay bat dau (tinh ca ngay nay); null = 29 ngay truoc ngay ket thuc
     * @param to          ngay ket thuc (tinh ca ngay nay); null = hom nay
     * @param granularity day hoac month
     * @param venueId     null = moi san
     */
    @Transactional(readOnly = true)
    public AdminFeeRevenueResponse get(UUID adminId, LocalDate from, LocalDate to, String granularity, UUID venueId) {
        accessService.requirePermission(adminId, PERMISSION);
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        boolean monthly = parseGranularity(granularity);
        validate(start, end);
        if (venueId != null && Boolean.FALSE.equals(jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM venues WHERE id = ?)", Boolean.class, venueId))) {
            throw ApiException.notFound("Khong tim thay san.");
        }

        Timestamp since = Timestamp.from(start.atStartOfDay(ZONE).toInstant());
        Timestamp until = Timestamp.from(end.plusDays(1).atStartOfDay(ZONE).toInstant());
        String where = " WHERE bc.calculated_at >= ? AND bc.calculated_at < ?" + (venueId == null ? "" : " AND v.id = ?");
        Object[] args = venueId == null ? new Object[] {since, until} : new Object[] {since, until, venueId};

        return new AdminFeeRevenueResponse(summary(where, args), trend(start, end, monthly, where, args),
                venues(where, args), venueOptions());
    }

    private AdminFeeRevenueResponse.Summary summary(String where, Object[] args) {
        return jdbc.queryForObject("SELECT " + SUMS + """
                , count(*) FILTER (WHERE bc.status = 'suspended') AS suspended_count,
                  coalesce(sum(bc.commission_amount) FILTER (WHERE bc.status = 'suspended'), 0) AS suspended,
                  count(*) FILTER (WHERE bc.status = 'waived') AS waived_count,
                  coalesce(sum(bc.commission_amount) FILTER (WHERE bc.status = 'waived'), 0) AS waived
                """ + FROM_CLAUSE + where, (rs, row) -> {
            BigDecimal gross = rs.getBigDecimal("gross_amount");
            BigDecimal recorded = rs.getBigDecimal("recorded");
            BigDecimal rate = gross.signum() == 0 ? null : recorded.multiply(BigDecimal.valueOf(100)).divide(gross, 2, RoundingMode.HALF_UP);
            return new AdminFeeRevenueResponse.Summary(rs.getLong("booking_count"), gross, recorded, rs.getBigDecimal("collected"),
                    rs.getBigDecimal("outstanding"), rate, rs.getLong("suspended_count"), rs.getBigDecimal("suspended"),
                    rs.getLong("waived_count"), rs.getBigDecimal("waived"));
        }, args);
    }

    private AdminFeeRevenueResponse.Trend trend(LocalDate start, LocalDate end, boolean monthly, String where, Object[] args) {
        String bucket = monthly
                ? "date_trunc('month', bc.calculated_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date"
                : "(bc.calculated_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date";
        Map<LocalDate, AdminFeeRevenueResponse.Point> found = new LinkedHashMap<>();
        jdbc.query("SELECT " + bucket + " AS period, " + SUMS + FROM_CLAUSE + where + " GROUP BY period", rs -> {
            LocalDate period = rs.getDate("period").toLocalDate();
            found.put(period, new AdminFeeRevenueResponse.Point(period, rs.getLong("booking_count"), rs.getBigDecimal("gross_amount"),
                    rs.getBigDecimal("recorded"), rs.getBigDecimal("collected"), rs.getBigDecimal("outstanding")));
        }, args);

        List<AdminFeeRevenueResponse.Point> points = new ArrayList<>();
        LocalDate period = monthly ? start.withDayOfMonth(1) : start;
        while (!period.isAfter(end)) {
            points.add(found.getOrDefault(period, new AdminFeeRevenueResponse.Point(period, 0, BigDecimal.ZERO, BigDecimal.ZERO,
                    BigDecimal.ZERO, BigDecimal.ZERO)));
            period = monthly ? period.plusMonths(1) : period.plusDays(1);
        }
        return new AdminFeeRevenueResponse.Trend(start, end, monthly ? "month" : "day", points);
    }

    private List<AdminFeeRevenueResponse.VenueRow> venues(String where, Object[] args) {
        return jdbc.query("SELECT v.id, v.name, u.full_name AS owner_name, " + SUMS + FROM_CLAUSE
                        + " JOIN users u ON u.id = v.owner_id" + where
                        + " GROUP BY v.id, v.name, u.full_name HAVING count(*) FILTER (WHERE " + RECORDED + ") > 0"
                        + " ORDER BY recorded DESC, v.name ASC",
                (rs, row) -> new AdminFeeRevenueResponse.VenueRow(rs.getObject("id", UUID.class), rs.getString("name"),
                        rs.getString("owner_name"), rs.getLong("booking_count"), rs.getBigDecimal("gross_amount"),
                        rs.getBigDecimal("recorded"), rs.getBigDecimal("collected"), rs.getBigDecimal("outstanding")), args);
    }

    private List<AdminFeeRevenueResponse.VenueOption> venueOptions() {
        return jdbc.query("SELECT id, name FROM venues ORDER BY name",
                (rs, row) -> new AdminFeeRevenueResponse.VenueOption(rs.getObject("id", UUID.class), rs.getString("name")));
    }

    private static boolean parseGranularity(String granularity) {
        if (granularity == null || granularity.isBlank() || "day".equals(granularity)) {
            return false;
        }
        if ("month".equals(granularity)) {
            return true;
        }
        throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Don vi thoi gian khong hop le.",
                Map.of("granularity", "Chi nhan day hoac month"));
    }

    private static void validate(LocalDate start, LocalDate end) {
        if (end.isBefore(start)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay ket thuc phai sau hoac bang ngay bat dau.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Chi xem toi da " + MAX_DAYS + " ngay moi lan.",
                    Map.of("to", "Khoang thoi gian toi da " + MAX_DAYS + " ngay"));
        }
    }
}
