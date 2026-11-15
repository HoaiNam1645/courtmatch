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

    /** Gia tri phi co dinh toi da moi booking (VND), chan nhap nham nhieu so 0. */
    static final BigDecimal MAX_FIXED_FEE = new BigDecimal("10000000");

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
