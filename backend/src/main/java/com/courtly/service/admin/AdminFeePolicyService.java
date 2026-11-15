package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminFeePolicyResponses;
import com.courtly.common.enums.ActiveStatus;
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

    /** 2.3.41 - chinh sach dang ap dung, cac chinh sach cu va nguong han che cong no. */
    @Transactional(readOnly = true)
    public AdminFeePolicyResponses.ListResponse list(UUID adminId) {
        accessService.requirePermission(adminId, PERMISSION);
        return view();
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
