package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminOwnerResponses;
import com.courtly.api.admin.dto.CreateOwnerAccountRequest;
import com.courtly.common.PhoneNumbers;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.AdminOwnerQueries;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.CourtOwnerProfileRepository;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.service.audit.AuditService;
import java.security.SecureRandom;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.33 - quan tri vien cap, khoa / kich hoat va dat lai thong tin dang nhap chu san.
 *
 * <p>Mat khau tam chi tra ve mot lan trong response; he thong chi luu ban bam, khong ghi log hay
 * nhat ky ban ro. Chu san phai doi mat khau tam truoc khi dung khu quan ly (2.3.1), nen token cu
 * khong dung duoc sau khi dat lai.
 */
@Service
@RequiredArgsConstructor
public class AdminOwnerAccountService {

    private static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    private static final String LOWER = "abcdefghijkmnpqrstuvwxyz";
    private static final String DIGITS = "23456789";
    private static final String SYMBOLS = "@#$%&*";
    private static final int PASSWORD_LENGTH = 12;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final AdminAccessService accessService;
    private final AdminOwnerQueries ownerQueries;
    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final CourtOwnerProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
    private final AdminOwnerService ownerService;
    private final AdminUserService userService;
    private final AuditService auditService;

    /** Cap tai khoan chu san khi he thong chua co chu san. */
    @Transactional
    public AdminOwnerResponses.Credentials create(UUID adminId, CreateOwnerAccountRequest request) {
        User admin = accessService.requirePermission(adminId, AdminUserService.PERMISSION);
        if (!ownerQueries.findOwners().isEmpty()) {
            throw ApiException.conflict(ErrorCode.OWNER_ACCOUNT_INVALID,
                    "He thong chi co mot chu san va da co tai khoan chu san.", Map.of("email", "Da co tai khoan chu san"));
        }
        String phone = PhoneNumbers.normalize(request.phone());
        if (!PhoneNumbers.isMobile(phone)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "So dien thoai khong hop le.",
                    Map.of("phone", "So dien thoai di dong Viet Nam gom 10 so, bat dau bang 03, 05, 07, 08 hoac 09"));
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict(ErrorCode.EMAIL_ALREADY_EXISTS, "Email nay da duoc tai khoan khac su dung.",
                    Map.of("email", "Email da ton tai"));
        }
        if (userRepository.existsByPhone(phone)) {
            throw ApiException.conflict(ErrorCode.PHONE_ALREADY_EXISTS, "So dien thoai nay da duoc tai khoan khac su dung.",
                    Map.of("phone", "So dien thoai da ton tai"));
        }
        Role ownerRole = roleRepository.findByCode(Role.OWNER)
                .orElseThrow(() -> new IllegalStateException("Chua co vai tro owner."));

        String temporaryPassword = generatePassword();
        User owner = new User();
        owner.setId(UUID.randomUUID());
        owner.setFullName(request.fullName());
        owner.setEmail(request.email());
        owner.setPhone(phone);
        owner.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        owner.setPasswordChangeRequired(true);
        owner.setStatus(UserStatus.ACTIVE);
        owner.getUserRoles().add(new UserRole(owner, ownerRole));
        userRepository.saveAndFlush(owner);
        profileRepository.saveAndFlush(new CourtOwnerProfile(owner));

        auditService.record(admin, "owner.account.create", "users", owner.getId(), null,
                Map.of("email", owner.getEmail(), "phone", phone, "fullName", owner.getFullName()));
        return new AdminOwnerResponses.Credentials(ownerService.build(owner), temporaryPassword);
    }

    /** Khoa, ngung hoac kich hoat lai tai khoan chu san kem ly do; booking da xac nhan khong bi huy. */
    @Transactional
    public AdminOwnerResponses.Detail changeStatus(UUID adminId, String status, String reason) {
        User owner = requireOwner();
        userService.changeStatus(adminId, owner.getId(), status, reason);
        return ownerService.build(owner);
    }

    /** Sinh mat khau tam moi, bat buoc doi o lan dang nhap ke tiep va vo hieu hoa token cu. */
    @Transactional
    public AdminOwnerResponses.Credentials resetCredentials(UUID adminId) {
        User admin = accessService.requirePermission(adminId, AdminUserService.PERMISSION);
        User owner = requireOwner();
        String temporaryPassword = generatePassword();
        owner.setPasswordHash(passwordEncoder.encode(temporaryPassword));
        owner.setPasswordChangeRequired(true);
        userRepository.saveAndFlush(owner);
        auditService.record(admin, "owner.credentials.reset", "users", owner.getId(), null,
                Map.of("passwordChangeRequired", true));
        return new AdminOwnerResponses.Credentials(ownerService.build(owner), temporaryPassword);
    }

    private User requireOwner() {
        List<User> owners = ownerQueries.findOwners();
        if (owners.isEmpty()) {
            throw ApiException.notFound("He thong chua co tai khoan chu san.");
        }
        return owners.getFirst();
    }

    /** Mat khau tam 12 ky tu co chu hoa, chu thuong, so va ky hieu; bo cac ky tu de nham (0 O 1 l I). */
    static String generatePassword() {
        String all = UPPER + LOWER + DIGITS + SYMBOLS;
        char[] chars = new char[PASSWORD_LENGTH];
        chars[0] = pick(UPPER);
        chars[1] = pick(LOWER);
        chars[2] = pick(DIGITS);
        chars[3] = pick(SYMBOLS);
        for (int i = 4; i < PASSWORD_LENGTH; i++) {
            chars[i] = pick(all);
        }
        for (int i = chars.length - 1; i > 0; i--) {
            int j = RANDOM.nextInt(i + 1);
            char swap = chars[i];
            chars[i] = chars[j];
            chars[j] = swap;
        }
        return new String(chars);
    }

    private static char pick(String source) {
        return source.charAt(RANDOM.nextInt(source.length()));
    }
}
