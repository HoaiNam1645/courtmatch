package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminVoucherResponses;
import com.courtly.api.admin.dto.VoucherRequest;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.domain.voucher.Voucher;
import com.courtly.domain.voucher.VoucherRedemption;
import com.courtly.domain.voucher.VoucherRedemptionRepository;
import com.courtly.domain.voucher.VoucherRepository;
import com.courtly.service.audit.AuditService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * B4 - quan tri vien tao, sua, khoa voucher va xem chi phi Courtly da chiu.
 *
 * <p>Sau khi co luot dung (ke ca da tra lai), phan tram giam va tran giam khong doi duoc de lich su so sanh duoc;
 * khoa voucher khong anh huong booking da ap dung.
 */
@Service
@RequiredArgsConstructor
public class AdminVoucherService {

    static final String PERMISSION = "voucher.manage";
    private static final Pattern CODE = Pattern.compile("[A-Z0-9_-]{3,40}");
    private static final Set<String> STATES = Set.of("all", "active", "locked", "scheduled", "expired", "exhausted");
    static final BigDecimal MAX_CAP = new BigDecimal("100000000");
    static final BigDecimal MAX_ELO = new BigDecimal("5000");
    static final int MAX_COUNT = 100000;
    static final int MAX_WEEKS = 520;

    private final AdminAccessService accessService;
    private final VoucherRepository voucherRepository;
    private final VoucherRedemptionRepository redemptionRepository;
    private final AuditService auditService;

    /** @param state all hoac active, locked, scheduled, expired, exhausted; q tim theo ma hoac ten */
    @Transactional(readOnly = true)
    public AdminVoucherResponses.ListResponse list(UUID adminId, String state, String q) {
        accessService.requirePermission(adminId, PERMISSION);
        String wanted = state == null || state.isBlank() ? "all" : state;
        if (!STATES.contains(wanted)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai voucher khong hop le.",
                    Map.of("state", "Chi nhan all, active, locked, scheduled, expired hoac exhausted"));
        }
        String keyword = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        List<Voucher> vouchers = voucherRepository.findAllNewestFirst();
        Map<UUID, VoucherRedemptionRepository.UsageRow> usage = usage(vouchers);
        Instant now = Instant.now();
        List<AdminVoucherResponses.Summary> content = vouchers.stream()
                .filter(voucher -> keyword.isEmpty() || voucher.getCode().toLowerCase(Locale.ROOT).contains(keyword)
                        || voucher.getName().toLowerCase(Locale.ROOT).contains(keyword))
                .map(voucher -> summary(voucher, usage.get(voucher.getId()), now))
                .filter(item -> "all".equals(wanted) || wanted.equals(item.state()))
                .toList();
        long redemptions = content.stream().mapToLong(AdminVoucherResponses.Summary::usedCount).sum();
        BigDecimal cost = content.stream().map(AdminVoucherResponses.Summary::totalDiscount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return new AdminVoucherResponses.ListResponse(content, new AdminVoucherResponses.Totals(content.size(), redemptions, cost));
    }

    @Transactional(readOnly = true)
    public AdminVoucherResponses.Detail get(UUID adminId, UUID voucherId) {
        accessService.requirePermission(adminId, PERMISSION);
        return detail(require(voucherId));
    }

    @Transactional
    public AdminVoucherResponses.Detail create(UUID adminId, VoucherRequest request) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        String code = request.code() == null ? "" : request.code().trim().toUpperCase(Locale.ROOT);
        Map<String, String> errors = validate(request, code);
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Thong tin voucher khong hop le.", errors);
        }
        if (voucherRepository.findByCode(code).isPresent()) {
            throw ApiException.conflict(ErrorCode.VOUCHER_INVALID, "Ma voucher da ton tai.", Map.of("code", "Ma voucher da duoc dung"));
        }
        Voucher voucher = new Voucher();
        voucher.setId(UUID.randomUUID());
        voucher.setCode(code);
        voucher.setCreatedBy(admin);
        voucher.setCreatedAt(Instant.now());
        apply(voucher, request);
        try {
            voucherRepository.saveAndFlush(voucher);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict(ErrorCode.VOUCHER_INVALID, "Ma voucher da ton tai.", Map.of("code", "Ma voucher da duoc dung"));
        }
        auditService.record(admin, "voucher.create", "vouchers", voucher.getId(), Map.of(), snapshot(voucher));
        return detail(voucher);
    }

    @Transactional
    public AdminVoucherResponses.Detail update(UUID adminId, UUID voucherId, VoucherRequest request) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        Voucher voucher = require(voucherId);
        String code = voucher.getCode();
        Map<String, String> errors = validate(request, code);
        if (request.code() != null && !request.code().trim().equalsIgnoreCase(code)) {
            errors.put("code", "Khong doi duoc ma voucher sau khi tao");
        }
        long ever = redemptionRepository.findByVoucher(voucherId).size();
        if (ever > 0 && (request.discountPercent() != null && request.discountPercent().compareTo(voucher.getDiscountPercent()) != 0
                || !sameCap(request.maxDiscountAmount(), voucher.getMaxDiscountAmount()))) {
            errors.put("discountPercent", "Voucher da co luot dung nen khong doi duoc muc giam; hay tao voucher moi");
        }
        long used = redemptionRepository.countByVoucherIdAndStatus(voucherId, VoucherRedemption.APPLIED);
        if (request.totalLimit() != null && request.totalLimit() < used) {
            errors.put("totalLimit", "Tong so luot khong duoc nho hon so luot da dung (" + used + ")");
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Thong tin voucher khong hop le.", errors);
        }
        Map<String, Object> before = snapshot(voucher);
        apply(voucher, request);
        voucher.setUpdatedAt(Instant.now());
        voucherRepository.saveAndFlush(voucher);
        auditService.record(admin, "voucher.update", "vouchers", voucher.getId(), before, snapshot(voucher));
        return detail(voucher);
    }

    @Transactional
    public AdminVoucherResponses.Detail lock(UUID adminId, UUID voucherId) {
        return changeStatus(adminId, voucherId, Voucher.LOCKED);
    }

    @Transactional
    public AdminVoucherResponses.Detail unlock(UUID adminId, UUID voucherId) {
        return changeStatus(adminId, voucherId, Voucher.ACTIVE);
    }

    private AdminVoucherResponses.Detail changeStatus(UUID adminId, UUID voucherId, String target) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        Voucher voucher = require(voucherId);
        if (target.equals(voucher.getStatus())) {
            throw ApiException.conflict(ErrorCode.VOUCHER_INVALID, Voucher.LOCKED.equals(target)
                    ? "Voucher da bi khoa." : "Voucher dang hoat dong.", Map.of("status", "Trang thai khong doi"));
        }
        String before = voucher.getStatus();
        voucher.setStatus(target);
        voucher.setUpdatedAt(Instant.now());
        voucherRepository.saveAndFlush(voucher);
        auditService.record(admin, Voucher.LOCKED.equals(target) ? "voucher.lock" : "voucher.unlock", "vouchers", voucher.getId(),
                Map.of("status", before), Map.of("status", target, "code", voucher.getCode()));
        return detail(voucher);
    }

    private Voucher require(UUID voucherId) {
        return voucherRepository.findById(voucherId).orElseThrow(() -> ApiException.notFound("Khong tim thay voucher."));
    }

    private static boolean sameCap(BigDecimal first, BigDecimal second) {
        return first == null ? second == null : second != null && first.compareTo(second) == 0;
    }

    private static Map<String, String> validate(VoucherRequest request, String code) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (!CODE.matcher(code).matches()) {
            errors.put("code", "Ma voucher tu 3 den 40 ky tu chu hoa, so, gach noi hoac gach duoi");
        }
        if (request.name() == null || request.name().trim().length() < 2 || request.name().trim().length() > 150) {
            errors.put("name", "Ten voucher tu 2 den 150 ky tu");
        }
        BigDecimal percent = request.discountPercent();
        if (percent == null || percent.signum() <= 0 || percent.compareTo(BigDecimal.valueOf(100)) > 0) {
            errors.put("discountPercent", "Phan tram giam lon hon 0 va toi da 100");
        } else if (percent.stripTrailingZeros().scale() > 2) {
            errors.put("discountPercent", "Phan tram giam toi da 2 chu so thap phan");
        }
        BigDecimal cap = request.maxDiscountAmount();
        if (cap != null && (cap.signum() <= 0 || cap.compareTo(MAX_CAP) > 0 || cap.stripTrailingZeros().scale() > 2)) {
            errors.put("maxDiscountAmount", "Tran giam lon hon 0 va toi da 100.000.000 dong");
        }
        if (request.validFrom() != null && request.validUntil() != null && !request.validUntil().isAfter(request.validFrom())) {
            errors.put("validUntil", "Ngay het han phai sau ngay bat dau");
        }
        if (request.totalLimit() != null && (request.totalLimit() < 1 || request.totalLimit() > MAX_COUNT)) {
            errors.put("totalLimit", "Tong so luot tu 1 den " + MAX_COUNT);
        }
        BigDecimal elo = request.minElo();
        if (elo != null && (elo.signum() < 0 || elo.compareTo(MAX_ELO) > 0)) {
            errors.put("minElo", "Elo toi thieu tu 0 den " + MAX_ELO.toPlainString());
        }
        if (request.minBookingMatches() != null && (request.minBookingMatches() < 1 || request.minBookingMatches() > MAX_COUNT)) {
            errors.put("minBookingMatches", "So tran toi thieu tu 1 den " + MAX_COUNT);
        }
        if (request.minActiveWeeks() != null && (request.minActiveWeeks() < 1 || request.minActiveWeeks() > MAX_WEEKS)) {
            errors.put("minActiveWeeks", "So tuan hoat dong toi thieu tu 1 den " + MAX_WEEKS);
        }
        return errors;
    }

    private static void apply(Voucher voucher, VoucherRequest request) {
        voucher.setName(request.name().trim());
        voucher.setDescription(request.description() == null || request.description().isBlank() ? null : request.description().trim());
        voucher.setDiscountPercent(request.discountPercent().setScale(2, RoundingMode.HALF_UP));
        voucher.setMaxDiscountAmount(request.maxDiscountAmount() == null ? null : request.maxDiscountAmount().setScale(2, RoundingMode.HALF_UP));
        voucher.setValidFrom(request.validFrom());
        voucher.setValidUntil(request.validUntil());
        voucher.setTotalLimit(request.totalLimit());
        voucher.setMinElo(request.minElo() == null ? null : request.minElo().setScale(2, RoundingMode.HALF_UP));
        voucher.setMinBookingMatches(request.minBookingMatches());
        voucher.setMinActiveWeeks(request.minActiveWeeks());
    }

    private static Map<String, Object> snapshot(Voucher voucher) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("code", voucher.getCode());
        data.put("name", voucher.getName());
        data.put("discountPercent", voucher.getDiscountPercent());
        data.put("maxDiscountAmount", voucher.getMaxDiscountAmount());
        data.put("validFrom", String.valueOf(voucher.getValidFrom()));
        data.put("validUntil", String.valueOf(voucher.getValidUntil()));
        data.put("totalLimit", voucher.getTotalLimit());
        data.put("minElo", voucher.getMinElo());
        data.put("minBookingMatches", voucher.getMinBookingMatches());
        data.put("minActiveWeeks", voucher.getMinActiveWeeks());
        data.put("status", voucher.getStatus());
        return data;
    }

    private Map<UUID, VoucherRedemptionRepository.UsageRow> usage(List<Voucher> vouchers) {
        Map<UUID, VoucherRedemptionRepository.UsageRow> result = new HashMap<>();
        if (!vouchers.isEmpty()) {
            redemptionRepository.usage(vouchers.stream().map(Voucher::getId).toList()).forEach(row -> result.put(row.getVoucherId(), row));
        }
        return result;
    }

    private AdminVoucherResponses.Summary summary(Voucher voucher, VoucherRedemptionRepository.UsageRow usage, Instant now) {
        long used = usage == null ? 0 : usage.getTotal();
        BigDecimal cost = usage == null ? BigDecimal.ZERO : usage.getDiscount();
        Integer remaining = voucher.getTotalLimit() == null ? null : (int) Math.max(0, voucher.getTotalLimit() - used);
        String state;
        if (Voucher.LOCKED.equals(voucher.getStatus())) {
            state = "locked";
        } else if (voucher.getValidFrom().isAfter(now)) {
            state = "scheduled";
        } else if (!voucher.getValidUntil().isAfter(now)) {
            state = "expired";
        } else if (remaining != null && remaining == 0) {
            state = "exhausted";
        } else {
            state = "active";
        }
        return new AdminVoucherResponses.Summary(voucher.getId(), voucher.getCode(), voucher.getName(), voucher.getDiscountPercent(),
                voucher.getMaxDiscountAmount(), voucher.getValidFrom(), voucher.getValidUntil(), voucher.getTotalLimit(), used,
                remaining, cost, voucher.getMinElo(), voucher.getMinBookingMatches(), voucher.getMinActiveWeeks(),
                voucher.getStatus(), state, voucher.getCreatedAt());
    }

    private AdminVoucherResponses.Detail detail(Voucher voucher) {
        Map<UUID, VoucherRedemptionRepository.UsageRow> usage = usage(List.of(voucher));
        List<AdminVoucherResponses.Redemption> redemptions = redemptionRepository.findByVoucher(voucher.getId()).stream()
                .limit(100)
                .map(item -> new AdminVoucherResponses.Redemption(item.getId(), item.getUser().getFullName(),
                        item.getBooking().getBookingCode(), item.getOriginalAmount(), item.getDiscountAmount(), item.getStatus(),
                        item.getCreatedAt(), item.getReleasedAt()))
                .toList();
        return new AdminVoucherResponses.Detail(summary(voucher, usage.get(voucher.getId()), Instant.now()), voucher.getDescription(),
                voucher.getCreatedBy() == null ? null : voucher.getCreatedBy().getFullName(), redemptions);
    }
}
