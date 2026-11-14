package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminOwnerResponses;
import com.courtly.common.enums.VerificationStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.AdminOwnerQueries;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.finance.BookingCommissionRepository;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.32 - quan tri vien xem tai khoan chu san duy nhat va quyet dinh xac minh ho so.
 *
 * <p>Chu san khong tu sua trang thai xac minh; viec cap, khoa va dat lai tai khoan thuoc 2.3.33.
 */
@Service
@RequiredArgsConstructor
public class AdminOwnerService {

    static final String PERMISSION = "owner.verify";

    private final AdminAccessService accessService;
    private final AdminOwnerQueries ownerQueries;
    private final CourtOwnerProfileRepository profileRepository;
    private final BookingCommissionRepository commissionRepository;
    private final AdminUserService userService;
    private final com.courtly.service.audit.AuditService auditService;

    @Transactional(readOnly = true)
    public AdminOwnerResponses.Detail get(java.util.UUID adminId) {
        accessService.requirePermission(adminId, PERMISSION);
        return build(currentOwner());
    }

    /** Dat trang thai xac minh; tu choi hoac dat lai phai co ly do. */
    @Transactional
    public AdminOwnerResponses.Detail review(java.util.UUID adminId, String statusValue, String reason) {
        User admin = accessService.requirePermission(adminId, PERMISSION);
        VerificationStatus target;
        try {
            target = VerificationStatus.from(statusValue);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai xac minh khong hop le.",
                    Map.of("status", "Chi nhan pending, verified hoac rejected"));
        }
        User owner = currentOwner();
        if (owner == null) {
            throw ApiException.notFound("He thong chua co tai khoan chu san.");
        }
        CourtOwnerProfile profile = profileRepository.findById(owner.getId())
                .orElseThrow(() -> ApiException.notFound("Chu san chua co ho so."));
        VerificationStatus current = profile.getVerificationStatus();
        if (current == target) {
            throw ApiException.conflict(ErrorCode.OWNER_ACCOUNT_INVALID, "Ho so da o trang thai nay.",
                    Map.of("status", "Ho so da o trang thai nay"));
        }
        if (target != VerificationStatus.VERIFIED && (reason == null || reason.length() < 3)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Vui long nhap ly do (it nhat 3 ky tu).",
                    Map.of("reason", "Bat buoc nhap ly do khi tu choi hoac dat lai xac minh"));
        }

        Instant now = Instant.now();
        profile.setVerificationStatus(target);
        profile.setVerifiedAt(target == VerificationStatus.VERIFIED ? now : null);
        profile.setVerifiedBy(admin);
        profile.setVerificationNote(target == VerificationStatus.VERIFIED ? null : reason);
        profileRepository.saveAndFlush(profile);

        Map<String, Object> newData = new LinkedHashMap<>();
        newData.put("verificationStatus", target.getValue());
        if (reason != null) {
            newData.put("reason", reason);
        }
        auditService.record(admin, "owner.verification.review", "users", owner.getId(),
                Map.of("verificationStatus", current.getValue()), newData);
        return build(owner);
    }

    private User currentOwner() {
        List<User> owners = ownerQueries.findOwners();
        return owners.isEmpty() ? null : owners.getFirst();
    }

    AdminOwnerResponses.Detail build(User owner) {
        if (owner == null) {
            return new AdminOwnerResponses.Detail(null, null, null);
        }
        CourtOwnerProfile profile = profileRepository.findById(owner.getId()).orElse(null);
        Map<String, Long> venues = new LinkedHashMap<>();
        List.of("pending", "approved", "rejected").forEach(value -> venues.put(value, 0L));
        ownerQueries.venuesByApproval(owner.getId()).forEach(row -> venues.put(row.getStatus(), row.getTotal()));
        return new AdminOwnerResponses.Detail(userService.toDetail(owner),
                profile == null ? null : new AdminOwnerResponses.Profile(profile.getBusinessName(), profile.getTaxCode(),
                        profile.getIdentityNumber(), profile.getBankName(), profile.getBankAccountNo(),
                        profile.getBankAccountName(), profile.getVerificationStatus().getValue(), profile.getVerifiedAt(),
                        profile.getVerificationNote(), profile.getVerifiedBy() == null ? null : profile.getVerifiedBy().getFullName()),
                new AdminOwnerResponses.Overview(venues, commissionRepository.sumOutstandingByOwnerId(owner.getId()),
                        ownerQueries.activeBookings(owner.getId())));
    }
}
