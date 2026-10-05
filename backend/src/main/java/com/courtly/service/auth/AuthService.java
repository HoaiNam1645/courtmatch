package com.courtly.service.auth;

import com.courtly.api.auth.dto.AuthResponse;
import com.courtly.api.auth.dto.LoginRequest;
import com.courtly.api.auth.dto.RegisterRequest;
import com.courtly.api.auth.dto.VerificationSentResponse;
import com.courtly.api.auth.dto.UserResponse;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Dang ky (2.1.1), dang nhap (2.1.2) va doc thong tin tai khoan hien tai. */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    /** So dien thoai di dong Viet Nam sau khi chuan hoa ve dang 0xxxxxxxxx. */
    private static final Pattern VN_MOBILE = Pattern.compile("^0[35789]\\d{8}$");

    private static final Pattern LOOKS_LIKE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailVerificationService emailVerificationService;

    /**
     * 2.1.1 - tao tai khoan o trang thai cho xac minh.
     *
     * <p>KHONG tra access token: tai khoan chi dang nhap duoc sau khi nhap dung ma gui ve
     * email. Viec tao tai khoan va tao ma xac minh nam chung mot transaction - khong the
     * co tai khoan cho xac minh ma khong co ma nao duoc phat.
     */
    @Transactional
    public VerificationSentResponse register(RegisterRequest request, String ip, String userAgent) {
        String phone = request.phone() == null ? null : normalizePhone(request.phone());
        if (phone != null && !VN_MOBILE.matcher(phone).matches()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "So dien thoai khong hop le.",
                    Map.of("phone", "So dien thoai di dong Viet Nam gom 10 so, bat dau bang 03, 05, 07, 08 hoac 09"));
        }

        // Kiem tra trung truoc de bao loi ro rang; unique index van la chot chan cuoi cung
        // cho truong hop hai request dang ky cung luc.
        if (request.email() != null && userRepository.existsByEmailIgnoreCase(request.email())) {
            throw ApiException.conflict(ErrorCode.EMAIL_ALREADY_EXISTS,
                    "Email nay da duoc dang ky.",
                    Map.of("email", "Email da ton tai"));
        }
        if (phone != null && userRepository.existsByPhone(phone)) {
            throw ApiException.conflict(ErrorCode.PHONE_ALREADY_EXISTS,
                    "So dien thoai nay da duoc dang ky.",
                    Map.of("phone", "So dien thoai da ton tai"));
        }

        Role playerRole = roleRepository.findByCode(Role.PLAYER)
                .orElseThrow(() -> new IllegalStateException(
                        "Thieu vai tro '" + Role.PLAYER + "'. Chay seeder hoac tao vai tro truoc."));

        User user = new User();
        // Gan id truoc vi UserRole va PlayerProfile dung id nay lam khoa chinh ghep.
        user.setId(UUID.randomUUID());
        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(phone);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        // Cho xac minh email; EmailVerificationService doi sang ACTIVE khi nhap dung ma.
        user.setStatus(UserStatus.PENDING);
        user.getUserRoles().add(new UserRole(user, playerRole));
        user.setPlayerProfile(new PlayerProfile(user));

        User saved = userRepository.save(user);
        log.info("Tai khoan moi duoc tao, dang cho xac minh email: {}", saved.getId());

        return emailVerificationService.issueFor(saved, ip, userAgent);
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = findByEmailOrPhone(request.emailOrPhone())
                // Khong phan biet "khong ton tai" voi "sai mat khau" de tranh do email/so dien thoai.
                .orElseThrow(AuthService::invalidCredentials);

        if (user.getPasswordHash() == null
                || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        // Tach rieng "chua xac minh" khoi "bi khoa": frontend can dua nguoi dung sang
        // man hinh nhap ma, chu khong phai bao ho lien he quan tri vien.
        if (user.getStatus() == UserStatus.PENDING) {
            throw ApiException.forbidden(ErrorCode.EMAIL_NOT_VERIFIED,
                    "Tai khoan chua xac minh email. Vui long nhap ma da gui toi email cua ban.");
        }
        if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden(ErrorCode.ACCOUNT_DISABLED,
                    "Tai khoan dang bi khoa.");
        }

        user.setLastLoginAt(Instant.now());
        return buildAuthResponse(user);
    }

    @Transactional(readOnly = true)
    public UserResponse currentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
        return UserResponse.from(user);
    }

    private AuthResponse buildAuthResponse(User user) {
        return AuthResponse.of(
                jwtService.generateAccessToken(user),
                jwtService.expiresInSeconds(),
                UserResponse.from(user));
    }

    private Optional<User> findByEmailOrPhone(String value) {
        if (LOOKS_LIKE_EMAIL.matcher(value).matches()) {
            return userRepository.findByEmailIgnoreCase(value);
        }
        return userRepository.findByPhone(normalizePhone(value));
    }

    /** Doi "+84 901 234 567", "84901234567", "090 123 4567" ve cung dang "0901234567". */
    private static String normalizePhone(String raw) {
        String digitsOnly = raw.replaceAll("[^0-9+]", "");
        if (digitsOnly.startsWith("+84")) {
            return "0" + digitsOnly.substring(3);
        }
        if (digitsOnly.startsWith("84") && digitsOnly.length() == 11) {
            return "0" + digitsOnly.substring(2);
        }
        return digitsOnly;
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized(ErrorCode.INVALID_CREDENTIALS,
                "Email, so dien thoai hoac mat khau khong dung.");
    }
}
