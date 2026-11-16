package com.courtly.service.voucher;

import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.voucher.Voucher;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dieu kien nhan voucher theo hoat dong hop le cua nguoi choi (B4, Van de.docx muc 6): chi tinh tran gan voi
 * booking tren Courtly va da duoc xac nhan, khong tinh tran nhap thu cong hoac tran bi loai khoi Elo.
 */
@Service
@RequiredArgsConstructor
public class VoucherEligibilityService {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final int MAX_WEEKS = 104;

    private final PlayerStatisticsRepository statisticsRepository;
    private final JdbcTemplate jdbc;

    /**
     * @param elo           diem Elo hien tai
     * @param bookingMatches so tran hop le gan voi booking
     * @param activeWeeks   so tuan lien tiep (tinh lui tu tuan nay, hoac tuan truoc neu tuan nay chua co) co it nhat mot tran hop le
     */
    public record Stats(BigDecimal elo, long bookingMatches, int activeWeeks) {
    }

    /** @param code elo, booking_matches hoac active_weeks */
    public record Unmet(String code, BigDecimal required, BigDecimal actual) {
    }

    @Transactional(readOnly = true)
    public Stats statsOf(UUID userId) {
        BigDecimal elo = statisticsRepository.findById(userId).map(PlayerStatistics::getRatingScore).orElse(PlayerStatistics.DEFAULT_RATING);
        List<Timestamp> played = jdbc.query("""
                SELECT m.scheduled_at FROM matches m
                JOIN match_players mp ON mp.match_id = m.id
                JOIN match_results mr ON mr.match_id = m.id AND mr.confirmed_at IS NOT NULL
                WHERE mp.user_id = ? AND m.data_source = 'booking' AND m.status = 'completed'
                  AND m.excluded_from_rating = false AND m.scheduled_at IS NOT NULL
                """, (rs, row) -> rs.getTimestamp(1), userId);
        Set<LocalDate> weeks = new HashSet<>();
        for (Timestamp timestamp : played) {
            weeks.add(weekStart(timestamp.toInstant().atZone(ZONE).toLocalDate()));
        }
        return new Stats(elo, played.size(), streak(weeks, LocalDate.now(ZONE)));
    }

    static LocalDate weekStart(LocalDate day) {
        return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    /** Dem tuan lien tiep co tran, tinh lui tu tuan nay (hoac tuan truoc neu tuan nay chua co tran). */
    static int streak(Set<LocalDate> weeks, LocalDate today) {
        LocalDate cursor = weekStart(today);
        if (!weeks.contains(cursor)) {
            cursor = cursor.minusWeeks(1);
        }
        int count = 0;
        while (weeks.contains(cursor) && count < MAX_WEEKS) {
            count++;
            cursor = cursor.minusWeeks(1);
        }
        return count;
    }

    /** Cac dieu kien cua voucher ma nguoi choi chua dat; rong nghia la du dieu kien. */
    public List<Unmet> unmet(Voucher voucher, Stats stats) {
        List<Unmet> unmet = new ArrayList<>();
        if (voucher.getMinElo() != null && stats.elo().compareTo(voucher.getMinElo()) < 0) {
            unmet.add(new Unmet("elo", voucher.getMinElo(), stats.elo()));
        }
        if (voucher.getMinBookingMatches() != null && stats.bookingMatches() < voucher.getMinBookingMatches()) {
            unmet.add(new Unmet("booking_matches", BigDecimal.valueOf(voucher.getMinBookingMatches()), BigDecimal.valueOf(stats.bookingMatches())));
        }
        if (voucher.getMinActiveWeeks() != null && stats.activeWeeks() < voucher.getMinActiveWeeks()) {
            unmet.add(new Unmet("active_weeks", BigDecimal.valueOf(voucher.getMinActiveWeeks()), BigDecimal.valueOf(stats.activeWeeks())));
        }
        return unmet;
    }
}
