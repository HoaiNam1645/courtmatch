package com.courtly.service.owner;

import com.courtly.api.owner.dto.OwnerProfileResponse;
import com.courtly.api.owner.dto.UpdateOwnerProfileRequest;
import com.courtly.common.PhoneNumbers;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.service.audit.AuditService;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.2 - xem va cap nhat ho so chu san duy nhat.
 *
 * <p>Thong tin ngan hang chi dung cho booking duoc chap nhan SAU khi luu: PaymentService
 * chup lai (snapshot) vao tung payment, nen booking cu khong doi khi chu san sua tai khoan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OwnerProfileService {

    private final UserRepository userRepository;
    private final CourtOwnerProfileRepository ownerProfileRepository;
    private final AuditService auditService;

    @Transactional(readOnly = true)
    public OwnerProfileResponse get(UUID ownerId) {
        User owner = requireOwner(ownerId);
        return toResponse(owner, ownerProfileRepository.findById(ownerId).orElse(null));
    }

    @Transactional
    public OwnerProfileResponse update(UUID ownerId, UpdateOwnerProfileRequest request) {
        User owner = requireOwner(ownerId);

        String phone = PhoneNumbers.normalize(request.phone());
        if (!PhoneNumbers.isMobile(phone)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "So dien thoai khong hop le.",
                    Map.of("phone", "So dien thoai di dong Viet Nam gom 10 so, bat dau bang 03, 05, 07, 08 hoac 09"));
        }
        userRepository.findByPhone(phone)
                .filter(other -> !other.getId().equals(ownerId))
                .ifPresent(other -> {
                    throw ApiException.conflict(ErrorCode.PHONE_ALREADY_EXISTS,
                            "So dien thoai nay da duoc tai khoan khac su dung.",
                            Map.of("phone", "So dien thoai da ton tai"));
                });

        CourtOwnerProfile profile = ownerProfileRepository.findById(ownerId)
                .orElseGet(() -> new CourtOwnerProfile(owner));
        Map<String, Object> before = snapshot(owner, profile);

        owner.setFullName(request.fullName());
        owner.setPhone(phone);
        profile.setBusinessName(request.businessName());
        profile.setTaxCode(request.taxCode() == null || request.taxCode().isEmpty() ? null : request.taxCode());
        profile.setBankName(request.bankName());
        profile.setBankAccountNo(request.bankAccountNo());
        // Ten chu tai khoan ngan hang luon in hoa, giong cach ngan hang hien thi khi chuyen khoan.
        profile.setBankAccountName(request.bankAccountName().toUpperCase(Locale.ROOT));

        userRepository.saveAndFlush(owner);
        ownerProfileRepository.saveAndFlush(profile);

        auditService.record(owner, "owner.profile.update", "court_owner_profiles", ownerId,
                before, snapshot(owner, profile));
        log.info("Chu san {} cap nhat ho so", ownerId);
        return toResponse(owner, profile);
    }

    private User requireOwner(UUID ownerId) {
        // OwnerAccessInterceptor da kiem tra vai tro va trang thai truoc khi vao day.
        return userRepository.findById(ownerId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan chu san."));
    }

    private static Map<String, Object> snapshot(User owner, CourtOwnerProfile profile) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("fullName", owner.getFullName());
        data.put("phone", owner.getPhone());
        data.put("businessName", profile.getBusinessName());
        data.put("taxCode", profile.getTaxCode());
        data.put("bankName", profile.getBankName());
        data.put("bankAccountNo", profile.getBankAccountNo());
        data.put("bankAccountName", profile.getBankAccountName());
        return data;
    }

    private static OwnerProfileResponse toResponse(User owner, CourtOwnerProfile profile) {
        return new OwnerProfileResponse(
                owner.getFullName(),
                owner.getEmail(),
                owner.getPhone(),
                profile == null ? null : profile.getBusinessName(),
                profile == null ? null : profile.getTaxCode(),
                profile == null ? null : profile.getBankName(),
                profile == null ? null : profile.getBankAccountNo(),
                profile == null ? null : profile.getBankAccountName(),
                profile == null ? "pending" : profile.getVerificationStatus().getValue(),
                profile == null ? null : profile.getVerifiedAt(),
                profile == null ? owner.getUpdatedAt() : profile.getUpdatedAt());
    }
}
