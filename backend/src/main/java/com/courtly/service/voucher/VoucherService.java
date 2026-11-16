package com.courtly.service.voucher;

import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.booking.Booking;
import com.courtly.domain.voucher.Voucher;
import com.courtly.domain.voucher.VoucherRedemption;
import com.courtly.domain.voucher.VoucherRedemptionRepository;
import com.courtly.domain.voucher.VoucherRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B4 - ap dung voucher cho nguoi choi. Courtly chiu phan giam: booking giu gia goc (totalAmount), nguoi choi tra
 * phan sau giam, hoa hong tinh tren gia goc (xem CommissionService).
 *
 * <p>Mot nguoi dung mot voucher mot lan, mot booking chi mot voucher (khong cong don). Booking bi huy, tu choi
 * hoac het han thi luot dung duoc tra lai de nguoi choi dung lai.
 */
@Service
@RequiredArgsConstructor
public class VoucherService {

    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository redemptionRepository;
    private final VoucherEligibilityService eligibilityService;
    private final UserRepository userRepository;

    /**
     * @param status active (dung duoc), used (da dung), exhausted (het luot), locked / expired khong hien
     * @param unmet  dieu kien chua dat; rong nghia la du dieu kien
     * @param remaining so luot con lai; null neu khong gioi han
     */
    public record View(UUID id, String code, String name, String description, BigDecimal discountPercent,
                       BigDecimal maxDiscountAmount, Instant validUntil, Integer remaining, String status, boolean eligible,
                       List<VoucherEligibilityService.Unmet> unmet) {
    }

    /** @param voucher voucher da khoa dong, san sang ghi luot dung */
    public record Application(Voucher voucher, BigDecimal originalAmount, BigDecimal discountAmount) {
        public BigDecimal payableAmount() {
            return originalAmount.subtract(discountAmount);
        }
    }

    /** Cac voucher dang phat hanh cho nguoi choi, kem trang thai dung va dieu kien chua dat. */
    @Transactional(readOnly = true)
    public List<View> listForPlayer(UUID userId) {
        Instant now = Instant.now();
        VoucherEligibilityService.Stats stats = eligibilityService.statsOf(userId);
        Set<UUID> used = new HashSet<>(redemptionRepository.appliedVoucherIds(userId));
        List<Voucher> vouchers = voucherRepository.findAllNewestFirst().stream()
                .filter(voucher -> Voucher.ACTIVE.equals(voucher.getStatus()) && !voucher.getValidFrom().isAfter(now)
                        && voucher.getValidUntil().isAfter(now))
                .toList();
        Map<UUID, Long> usage = usageCounts(vouchers);
        List<View> views = new ArrayList<>();
        for (Voucher voucher : vouchers) {
            Integer remaining = voucher.getTotalLimit() == null ? null
                    : (int) Math.max(0, voucher.getTotalLimit() - usage.getOrDefault(voucher.getId(), 0L));
            String status = used.contains(voucher.getId()) ? "used" : remaining != null && remaining == 0 ? "exhausted" : "active";
            List<VoucherEligibilityService.Unmet> unmet = eligibilityService.unmet(voucher, stats);
            views.add(new View(voucher.getId(), voucher.getCode(), voucher.getName(), voucher.getDescription(),
                    voucher.getDiscountPercent(), voucher.getMaxDiscountAmount(), voucher.getValidUntil(), remaining, status,
                    unmet.isEmpty(), unmet));
        }
        return views;
    }

    private Map<UUID, Long> usageCounts(List<Voucher> vouchers) {
        if (vouchers.isEmpty()) {
            return Map.of();
        }
        return redemptionRepository.usage(vouchers.stream().map(Voucher::getId).toList()).stream()
                .collect(Collectors.toMap(VoucherRedemptionRepository.UsageRow::getVoucherId, VoucherRedemptionRepository.UsageRow::getTotal));
    }

    /** Xem truoc muc giam cho mot tong tien, khong ghi gi. */
    @Transactional(readOnly = true)
    public Application preview(UUID userId, String code, BigDecimal total) {
        Voucher voucher = findByCode(code);
        return validate(voucher, userId, total);
    }

    /** Kiem tra va khoa voucher khi tao booking; tra ve muc giam. Ghi luot dung bang {@link #record}. */
    @Transactional
    public Application prepare(UUID userId, String code, BigDecimal total) {
        Voucher found = findByCode(code);
        Voucher voucher = voucherRepository.findByIdForUpdate(found.getId()).orElseThrow();
        return validate(voucher, userId, total);
    }

    @Transactional
    public void record(Application application, User user, Booking booking) {
        VoucherRedemption redemption = new VoucherRedemption();
        redemption.setId(UUID.randomUUID());
        redemption.setVoucher(application.voucher());
        redemption.setUser(user);
        redemption.setBooking(booking);
        redemption.setOriginalAmount(application.originalAmount());
        redemption.setDiscountAmount(application.discountAmount());
        redemption.setCreatedAt(Instant.now());
        redemptionRepository.save(redemption);
    }

    /** Tra lai voucher khi booking khong con hieu luc (huy, tu choi, het han); goi nhieu lan khong sao. */
    @Transactional
    public void release(Booking booking) {
        redemptionRepository.findByBookingId(booking.getId()).ifPresent(redemption -> {
            if (VoucherRedemption.APPLIED.equals(redemption.getStatus())) {
                redemption.setStatus(VoucherRedemption.RELEASED);
                redemption.setReleasedAt(Instant.now());
                redemptionRepository.save(redemption);
            }
        });
    }

    private Voucher findByCode(String code) {
        String clean = code == null ? "" : code.trim();
        if (clean.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Vui long nhap ma voucher.", Map.of("voucherCode", "Chua nhap ma voucher"));
        }
        return voucherRepository.findByCode(clean).orElseThrow(() -> invalid("Ma voucher khong ton tai.", "Khong tim thay voucher"));
    }

    private Application validate(Voucher voucher, UUID userId, BigDecimal total) {
        Instant now = Instant.now();
        if (!Voucher.ACTIVE.equals(voucher.getStatus()) || voucher.getValidFrom().isAfter(now) || !voucher.getValidUntil().isAfter(now)) {
            throw invalid("Voucher khong con hieu luc.", "Voucher da het han hoac bi khoa");
        }
        if (redemptionRepository.existsByVoucherIdAndUserIdAndStatus(voucher.getId(), userId, VoucherRedemption.APPLIED)) {
            throw invalid("Ban da dung voucher nay roi.", "Moi nguoi chi dung mot lan");
        }
        if (voucher.getTotalLimit() != null
                && redemptionRepository.countByVoucherIdAndStatus(voucher.getId(), VoucherRedemption.APPLIED) >= voucher.getTotalLimit()) {
            throw invalid("Voucher da het luot su dung.", "Voucher het luot");
        }
        List<VoucherEligibilityService.Unmet> unmet = eligibilityService.unmet(voucher, eligibilityService.statsOf(userId));
        if (!unmet.isEmpty()) {
            Map<String, String> fields = new LinkedHashMap<>();
            unmet.forEach(item -> fields.put(item.code(), "Can " + item.required().stripTrailingZeros().toPlainString()
                    + ", hien co " + item.actual().stripTrailingZeros().toPlainString()));
            throw ApiException.conflict(ErrorCode.VOUCHER_INVALID, "Ban chua du dieu kien dung voucher nay.", fields);
        }
        BigDecimal discount = discountFor(voucher, total);
        return new Application(voucher, total, discount);
    }

    /** Giam theo phan tram, toi da bang tran giam va khong vuot tong tien. */
    static BigDecimal discountFor(Voucher voucher, BigDecimal total) {
        BigDecimal discount = total.multiply(voucher.getDiscountPercent()).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        if (voucher.getMaxDiscountAmount() != null) {
            discount = discount.min(voucher.getMaxDiscountAmount());
        }
        return discount.min(total).max(BigDecimal.ZERO);
    }

    private static ApiException invalid(String message, String field) {
        return ApiException.conflict(ErrorCode.VOUCHER_INVALID, message, Map.of("voucherCode", field));
    }
}
