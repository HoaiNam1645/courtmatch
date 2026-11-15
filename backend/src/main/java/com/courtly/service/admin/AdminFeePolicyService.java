package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminFeePolicyResponses;
import com.courtly.common.enums.ActiveStatus;
import com.courtly.common.enums.FeeType;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.User;
import com.courtly.service.audit.AuditService;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.springframework.dao.DataIntegrityViolationException;
import com.courtly.api.admin.dto.UpdateDebtRestrictionsRequest;
import com.courtly.domain.finance.DebtRestrictionLevel;
import com.courtly.domain.finance.DebtRestrictionLevelRepository;
import com.courtly.domain.finance.PlatformFeeConfig;
import com.courtly.domain.finance.PlatformFeeConfigRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.39 - 2.3.41 - quan tri vien quan ly chinh sach phi nen tang.
 *
 * <p>Tai mot thoi diem chi co mot chinh sach active. Hoa hong da ghi nhan giu nguyen loai phi va
 * gia tri da chot (snapshot trong booking_commissions) nen thay doi chinh sach chi anh huong cac hoa
 * hong ghi nhan sau do.
 */
@Service
@RequiredArgsConstructor
public class AdminFeePolicyService {

    static final String PERMISSION = "finance.fee.config";

    private final AdminAccessService accessService;
    private final PlatformFeeConfigRepository configRepository;
    private final DebtRestrictionLevelRepository restrictionRepository;
    private final AuditService auditService;
    private final com.courtly.service.owner.OwnerRestrictionService restrictionService;

    /** Gia tri phi co dinh toi da moi booking (VND), chan nhap nham nhieu so 0. */
    static final BigDecimal MAX_FIXED_FEE = new BigDecimal("10000000");
    static final BigDecimal MAX_OVERDUE_AMOUNT = new BigDecimal("100000000000");
    static final int MAX_OVERDUE_DAYS = 3650;

    /** 2.3.41 - chinh sach dang ap dung, cac chinh sach cu va nguong han che cong no. */
    @Transactional(readOnly = true)
    public AdminFeePolicyResponses.ListResponse list(UUID adminId) {
        accessService.requirePermission(adminId, PERMISSION);
        return view();
    }

    /**
     * 2.3.39 - tao chinh sach moi va ap dung ngay: chinh sach dang active (neu co) chuyen inactive va
     * duoc dong hieu luc. Hoa hong da ghi nhan giu nguyen loai phi va gia tri da chot.
     */
    @Transactional
    public AdminFeePolicyResponses.ListResponse create(UUID adminId, String name, String feeTypeValue, BigDecimal feeValue) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        String cleanName = name == null ? "" : name.trim();
        FeeType feeType = validate(cleanName, feeTypeValue, feeValue);
        BigDecimal value = feeValue.setScale(2, java.math.RoundingMode.HALF_UP);

        Instant now = Instant.now();
        Map<String, Object> before = new LinkedHashMap<>();
        configRepository.findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus.ACTIVE).ifPresent(current -> {
            before.put("id", current.getId().toString());
            before.put("name", current.getName());
            before.put("feeType", current.getFeeType().getValue());
            before.put("feeValue", current.getFeeValue());
            current.setStatus(ActiveStatus.INACTIVE);
            // effective_to phai sau effective_from (rang buoc pfc_effective_check).
            current.setEffectiveTo(now.isAfter(current.getEffectiveFrom()) ? now : current.getEffectiveFrom().plusMillis(1));
            configRepository.saveAndFlush(current);
        });

        PlatformFeeConfig created = new PlatformFeeConfig();
        created.setId(UUID.randomUUID());
        created.setName(cleanName);
        created.setFeeType(feeType);
        created.setFeeValue(value);
        created.setStatus(ActiveStatus.ACTIVE);
        created.setEffectiveFrom(now);
        created.setCreatedBy(admin);
        created.setCreatedAt(now);
        try {
            configRepository.saveAndFlush(created);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict(ErrorCode.FEE_POLICY_INVALID, "Chinh sach phi vua duoc quan tri vien khac thay doi, vui long tai lai.",
                    Map.of("status", "Chinh sach phi vua duoc thay doi"));
        }
        Map<String, Object> after = new LinkedHashMap<>();
        after.put("id", created.getId().toString());
        after.put("name", created.getName());
        after.put("feeType", feeType.getValue());
        after.put("feeValue", value);
        auditService.record(admin, "fee.create", "platform_fee_configs", created.getId(), before, after);
        return view();
    }

    /** 2.3.40 - sua ten, loai phi, gia tri; khong dong den hoa hong da ghi nhan (da chot gia tri rieng). */
    @Transactional
    public AdminFeePolicyResponses.ListResponse update(UUID adminId, UUID policyId, String name, String feeTypeValue, BigDecimal feeValue) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        PlatformFeeConfig config = requirePolicy(policyId);
        String cleanName = name == null ? "" : name.trim();
        FeeType feeType = validate(cleanName, feeTypeValue, feeValue);
        BigDecimal value = feeValue.setScale(2, java.math.RoundingMode.HALF_UP);
        if (cleanName.equals(config.getName()) && feeType == config.getFeeType() && value.compareTo(config.getFeeValue()) == 0) {
            throw ApiException.conflict(ErrorCode.FEE_POLICY_INVALID, "Khong co thay doi nao de luu.",
                    Map.of("name", "Chua thay doi thong tin nao"));
        }
        Map<String, Object> before = snapshot(config);
        config.setName(cleanName);
        config.setFeeType(feeType);
        config.setFeeValue(value);
        configRepository.saveAndFlush(config);
        auditService.record(admin, "fee.update", "platform_fee_configs", config.getId(), before, snapshot(config));
        return view();
    }

    /**
     * 2.3.40 - kich hoat lai mot chinh sach cu: chinh sach dang active chuyen inactive va duoc dong
     * hieu luc, chinh sach nay co hieu luc tu bay gio. Muon dung mot chinh sach phai kich hoat chinh
     * sach khac, vi khong duoc de he thong khong con chinh sach active (khong ghi nhan duoc hoa hong).
     */
    @Transactional
    public AdminFeePolicyResponses.ListResponse activate(UUID adminId, UUID policyId) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        PlatformFeeConfig target = requirePolicy(policyId);
        if (target.getStatus() == ActiveStatus.ACTIVE) {
            throw ApiException.conflict(ErrorCode.FEE_POLICY_INVALID, "Chinh sach nay da dang ap dung.",
                    Map.of("status", "Chinh sach da active"));
        }
        Instant now = Instant.now();
        Map<String, Object> before = new LinkedHashMap<>();
        configRepository.findFirstByStatusOrderByEffectiveFromDesc(ActiveStatus.ACTIVE).ifPresent(current -> {
            before.putAll(snapshot(current));
            current.setStatus(ActiveStatus.INACTIVE);
            current.setEffectiveTo(now.isAfter(current.getEffectiveFrom()) ? now : current.getEffectiveFrom().plusMillis(1));
            configRepository.saveAndFlush(current);
        });
        target.setStatus(ActiveStatus.ACTIVE);
        target.setEffectiveFrom(now);
        target.setEffectiveTo(null);
        try {
            configRepository.saveAndFlush(target);
        } catch (DataIntegrityViolationException exception) {
            throw ApiException.conflict(ErrorCode.FEE_POLICY_INVALID, "Chinh sach phi vua duoc quan tri vien khac thay doi, vui long tai lai.",
                    Map.of("status", "Chinh sach phi vua duoc thay doi"));
        }
        auditService.record(admin, "fee.activate", "platform_fee_configs", target.getId(), before, snapshot(target));
        return view();
    }

    /**
     * 2.3.40 - cap nhat nguong han che cong no qua han. Muc 2 phai nghiem hon hoac bang muc 1 o moi
     * nguong ca hai muc cung dung.
     */
    @Transactional
    public AdminFeePolicyResponses.ListResponse updateRestrictions(UUID adminId, List<UpdateDebtRestrictionsRequest.Level> levels) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        Map<Integer, UpdateDebtRestrictionsRequest.Level> byLevel = new LinkedHashMap<>();
        Map<String, String> errors = new LinkedHashMap<>();
        for (UpdateDebtRestrictionsRequest.Level level : levels) {
            if (level.level() == null || (level.level() != 1 && level.level() != 2)) {
                errors.put("levels", "Chi nhan muc 1 va muc 2");
            } else if (byLevel.put(level.level(), level) != null) {
                errors.put("levels", "Muc " + level.level() + " bi gui nhieu lan");
            }
        }
        if (errors.isEmpty() && byLevel.size() != 2) {
            errors.put("levels", "Phai gui du muc 1 va muc 2");
        }
        byLevel.forEach((number, level) -> validateLevel(number, level, errors));
        UpdateDebtRestrictionsRequest.Level first = byLevel.get(1);
        UpdateDebtRestrictionsRequest.Level second = byLevel.get(2);
        if (errors.isEmpty() && first.enabled() && second.enabled()) {
            if (first.minOverdueAmount() != null && second.minOverdueAmount() != null
                    && second.minOverdueAmount().compareTo(first.minOverdueAmount()) < 0) {
                errors.put("level2.minOverdueAmount", "Nguong so tien cua muc 2 phai tu nguong muc 1 tro len");
            }
            if (first.minOverdueDays() != null && second.minOverdueDays() != null && second.minOverdueDays() < first.minOverdueDays()) {
                errors.put("level2.minOverdueDays", "Nguong so ngay cua muc 2 phai tu nguong muc 1 tro len");
            }
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Nguong han che khong hop le.", errors);
        }

        Map<String, Object> before = new LinkedHashMap<>();
        Map<String, Object> after = new LinkedHashMap<>();
        boolean changed = false;
        Instant now = Instant.now();
        for (DebtRestrictionLevel stored : restrictionRepository.findAllByOrderByLevelAsc()) {
            UpdateDebtRestrictionsRequest.Level wanted = byLevel.get((int) stored.getLevel());
            BigDecimal amount = wanted.minOverdueAmount() == null ? null : wanted.minOverdueAmount().setScale(2, java.math.RoundingMode.HALF_UP);
            boolean same = stored.isEnabled() == wanted.enabled()
                    && java.util.Objects.equals(stored.getMinOverdueDays(), wanted.minOverdueDays())
                    && (stored.getMinOverdueAmount() == null ? amount == null
                            : amount != null && stored.getMinOverdueAmount().compareTo(amount) == 0);
            if (same) {
                continue;
            }
            changed = true;
            before.put("level" + stored.getLevel(), restrictionSnapshot(stored));
            stored.setEnabled(wanted.enabled());
            stored.setMinOverdueAmount(amount);
            stored.setMinOverdueDays(wanted.minOverdueDays());
            stored.setUpdatedBy(admin);
            stored.setUpdatedAt(now);
            restrictionRepository.saveAndFlush(stored);
            after.put("level" + stored.getLevel(), restrictionSnapshot(stored));
        }
        if (!changed) {
            throw ApiException.conflict(ErrorCode.FEE_POLICY_INVALID, "Khong co thay doi nao de luu.",
                    Map.of("levels", "Chua thay doi nguong nao"));
        }
        auditService.record(admin, "fee.restriction.update", "debt_restriction_levels", null, before, after);
        // Nguong moi ap dung ngay cho moi chu san, khong cho toi lan chay dinh ky ke tiep.
        restrictionService.recomputeAll();
        return view();
    }

    private static void validateLevel(int number, UpdateDebtRestrictionsRequest.Level level, Map<String, String> errors) {
        String prefix = "level" + number + ".";
        if (level.minOverdueAmount() != null) {
            if (level.minOverdueAmount().signum() <= 0) {
                errors.put(prefix + "minOverdueAmount", "Nguong so tien phai lon hon 0");
            } else if (level.minOverdueAmount().compareTo(MAX_OVERDUE_AMOUNT) > 0) {
                errors.put(prefix + "minOverdueAmount", "Nguong so tien toi da 100.000.000.000 dong");
            } else if (level.minOverdueAmount().stripTrailingZeros().scale() > 2) {
                errors.put(prefix + "minOverdueAmount", "Nguong so tien toi da 2 chu so thap phan");
            }
        }
        if (level.minOverdueDays() != null && (level.minOverdueDays() < 1 || level.minOverdueDays() > MAX_OVERDUE_DAYS)) {
            errors.put(prefix + "minOverdueDays", "Nguong so ngay tu 1 den " + MAX_OVERDUE_DAYS);
        }
        if (level.enabled() && level.minOverdueAmount() == null && level.minOverdueDays() == null) {
            errors.put(prefix + "enabled", "Muc dang bat phai co it nhat mot nguong (so tien hoac so ngay)");
        }
    }

    private PlatformFeeConfig requirePolicy(UUID policyId) {
        return configRepository.findById(policyId).orElseThrow(() -> ApiException.notFound("Khong tim thay chinh sach phi."));
    }

    private static Map<String, Object> snapshot(PlatformFeeConfig config) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", config.getId().toString());
        data.put("name", config.getName());
        data.put("feeType", config.getFeeType().getValue());
        data.put("feeValue", config.getFeeValue());
        data.put("status", config.getStatus().getValue());
        return data;
    }

    private static Map<String, Object> restrictionSnapshot(DebtRestrictionLevel level) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("enabled", level.isEnabled());
        data.put("minOverdueAmount", level.getMinOverdueAmount());
        data.put("minOverdueDays", level.getMinOverdueDays());
        return data;
    }

    static FeeType validate(String name, String feeTypeValue, BigDecimal feeValue) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (name.length() < 2 || name.length() > 150) {
            errors.put("name", "Ten chinh sach tu 2 den 150 ky tu");
        }
        FeeType feeType = null;
        try {
            feeType = FeeType.from(feeTypeValue);
        } catch (IllegalArgumentException exception) {
            errors.put("feeType", "Chi nhan percentage hoac fixed");
        }
        if (feeValue == null) {
            errors.put("feeValue", "Vui long nhap gia tri phi");
        } else if (feeValue.signum() < 0) {
            errors.put("feeValue", "Gia tri phi khong duoc am");
        } else if (feeValue.scale() > 2 && feeValue.stripTrailingZeros().scale() > 2) {
            errors.put("feeValue", "Gia tri phi toi da 2 chu so thap phan");
        } else if (feeType == FeeType.PERCENTAGE && feeValue.compareTo(new BigDecimal("100")) > 0) {
            errors.put("feeValue", "Phi phan tram khong duoc vuot qua 100");
        } else if (feeType == FeeType.FIXED && feeValue.compareTo(MAX_FIXED_FEE) > 0) {
            errors.put("feeValue", "Phi co dinh toi da 10.000.000 dong moi booking");
        }
        if (!errors.isEmpty()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Thong tin chinh sach phi khong hop le.", errors);
        }
        return feeType;
    }

    AdminFeePolicyResponses.ListResponse view() {
        Map<UUID, PlatformFeeConfigRepository.UsageRow> usage = new HashMap<>();
        configRepository.usageByConfig().forEach(row -> usage.put(row.getConfigId(), row));
        List<AdminFeePolicyResponses.Policy> policies = configRepository.findAllWithCreator().stream()
                .map(config -> toPolicy(config, usage.get(config.getId()))).toList();
        return new AdminFeePolicyResponses.ListResponse(
                policies.stream().filter(policy -> ActiveStatus.ACTIVE.getValue().equals(policy.status())).findFirst().orElse(null),
                policies,
                restrictionRepository.findAllByOrderByLevelAsc().stream().map(AdminFeePolicyService::toRestriction).toList());
    }

    static AdminFeePolicyResponses.Policy toPolicy(PlatformFeeConfig config, PlatformFeeConfigRepository.UsageRow usage) {
        return new AdminFeePolicyResponses.Policy(config.getId(), config.getName(), config.getFeeType().getValue(),
                config.getFeeValue(), config.getStatus().getValue(), config.getEffectiveFrom(), config.getEffectiveTo(),
                config.getCreatedBy() == null ? null : config.getCreatedBy().getFullName(), config.getCreatedAt(),
                usage == null ? 0 : usage.getTotal(), usage == null ? BigDecimal.ZERO : usage.getAmount());
    }

    static AdminFeePolicyResponses.Restriction toRestriction(DebtRestrictionLevel level) {
        return new AdminFeePolicyResponses.Restriction(level.getLevel(), level.isEnabled(), level.getMinOverdueAmount(),
                level.getMinOverdueDays(), level.getUpdatedBy() == null ? null : level.getUpdatedBy().getFullName(),
                level.getUpdatedAt());
    }
}
