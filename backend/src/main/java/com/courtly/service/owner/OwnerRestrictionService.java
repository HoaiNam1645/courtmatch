package com.courtly.service.owner;

import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.finance.BookingCommissionRepository;
import com.courtly.domain.finance.DebtRestrictionLevel;
import com.courtly.domain.finance.DebtRestrictionLevelRepository;
import com.courtly.domain.venue.Venue;
import com.courtly.service.audit.AuditService;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B2 - han che chu san co cong no hoa hong qua han (Van de.docx muc 10.1).
 *
 * <p>Muc 1 chan nhan booking moi, muc 2 tam an san khoi nguoi choi. Moi muc co nguong so tien va nguong
 * so ngay qua han (dat mot trong hai la ap dung); muc cao nhat dat nguong duoc ap dung. Muc hien tai luu o
 * {@code court_owner_profiles.restriction_level} de cac truy van cong khai chi doc mot cot; he thong tinh
 * lai dinh ky, khi quan tri vien doi nguong va khi cong no thay doi. Booking da nhan truoc do van xu ly binh thuong.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerRestrictionService {

    private final CourtOwnerProfileRepository profileRepository;
    private final DebtRestrictionLevelRepository levelRepository;
    private final BookingCommissionRepository commissionRepository;
    private final AuditService auditService;

    /**
     * Ket qua danh gia cong no qua han cua mot chu san.
     *
     * @param level          muc han che ap dung theo nguong hien tai (0, 1, 2)
     * @param overdueAmount  tong hoa hong chua thu da qua han
     * @param overdueDays    so ngay tu han thanh toan cu nhat dang qua han (0 neu khong co)
     * @param byAmount       muc ap dung vi dat nguong so tien
     * @param byDays         muc ap dung vi dat nguong so ngay
     */
    public record Evaluation(int level, BigDecimal overdueAmount, long overdueDays, boolean byAmount, boolean byDays) {
    }

    @Transactional(readOnly = true)
    public Evaluation evaluate(UUID ownerId, Instant now) {
        BookingCommissionRepository.OverdueSummary summary = commissionRepository.overdueSummary(ownerId, now);
        BigDecimal amount = summary == null || summary.getAmount() == null ? BigDecimal.ZERO : summary.getAmount();
        long days = summary == null || summary.getOldest() == null ? 0 : Math.max(0, ChronoUnit.DAYS.between(summary.getOldest(), now));
        List<DebtRestrictionLevel> levels = levelRepository.findAllByOrderByLevelAsc();
        for (int index = levels.size() - 1; index >= 0; index--) {
            DebtRestrictionLevel config = levels.get(index);
            if (!config.isEnabled()) {
                continue;
            }
            boolean byAmount = config.getMinOverdueAmount() != null && amount.signum() > 0 && amount.compareTo(config.getMinOverdueAmount()) >= 0;
            boolean byDays = config.getMinOverdueDays() != null && days >= config.getMinOverdueDays() && amount.signum() > 0;
            if (byAmount || byDays) {
                return new Evaluation(config.getLevel(), amount, days, byAmount, byDays);
            }
        }
        return new Evaluation(0, amount, days, false, false);
    }

    /** Muc han che dang ap dung (da luu); 0 neu chu san chua co ho so. */
    @Transactional(readOnly = true)
    public int levelOf(UUID ownerId) {
        return profileRepository.findById(ownerId).map(CourtOwnerProfile::getRestrictionLevel).map(Short::intValue).orElse(0);
    }

    /** San bi tam an khoi nguoi choi khi chu san o muc 2. */
    @Transactional(readOnly = true)
    public boolean isHidden(Venue venue) {
        return levelOf(venue.getOwner().getId()) >= DebtRestrictionLevel.LEVEL_HIDE_VENUES;
    }

    /** Chan tao booking moi khi chu san o muc 1 tro len. */
    @Transactional(readOnly = true)
    public void requireAcceptingBookings(UUID ownerId) {
        if (levelOf(ownerId) >= DebtRestrictionLevel.LEVEL_BLOCK_BOOKINGS) {
            throw ApiException.conflict(ErrorCode.OWNER_RESTRICTED, "San tam thoi khong nhan dat san moi.",
                    Map.of("venue", "Chu san dang bi han che do cong no qua han"));
        }
    }

    /** Tinh lai muc han che cua mot chu san; ghi nhat ky khi doi muc. Tra ve true neu muc thay doi. */
    @Transactional
    public boolean recompute(UUID ownerId) {
        CourtOwnerProfile profile = profileRepository.findById(ownerId).orElse(null);
        if (profile == null) {
            return false;
        }
        Instant now = Instant.now();
        Evaluation evaluation = evaluate(ownerId, now);
        int before = profile.getRestrictionLevel();
        if (before == evaluation.level()) {
            return false;
        }
        profile.setRestrictionLevel((short) evaluation.level());
        profile.setRestrictionChangedAt(now);
        profile.setUpdatedAt(now);
        profileRepository.saveAndFlush(profile);
        Map<String, Object> oldData = new LinkedHashMap<>();
        oldData.put("level", before);
        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("level", evaluation.level());
        newData.put("overdueAmount", evaluation.overdueAmount());
        newData.put("overdueDays", evaluation.overdueDays());
        auditService.record(null, "owner.restriction.change", "court_owner_profiles", ownerId, oldData, newData);
        log.info("Chu san {} doi muc han che cong no {} -> {}", ownerId, before, evaluation.level());
        return true;
    }

    /** Tinh lai cho moi chu san; goi dinh ky va khi quan tri vien doi nguong. Tra ve so chu san doi muc. */
    @Transactional
    public int recomputeAll() {
        int changed = 0;
        for (CourtOwnerProfile profile : profileRepository.findAll()) {
            if (recompute(profile.getUserId())) {
                changed++;
            }
        }
        return changed;
    }
}
