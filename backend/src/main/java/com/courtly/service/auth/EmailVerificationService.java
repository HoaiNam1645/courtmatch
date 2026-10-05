package com.courtly.service.auth;

import com.courtly.api.auth.dto.AuthResponse;
import com.courtly.api.auth.dto.UserResponse;
import com.courtly.api.auth.dto.VerifyEmailRequest;
import com.courtly.api.auth.dto.VerificationSentResponse;
import com.courtly.common.config.EmailVerificationProperties;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.EmailVerificationRequest;
import com.courtly.domain.account.EmailVerificationRequestRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.service.mail.EmailVerificationCodeIssued;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Xac minh email khi dang ky (2.1.1).
 *
 * <p>Tai khoan moi tao o trang thai {@code pending} va KHONG dang nhap duoc. Nhap dung ma
 * gui ve email thi chuyen sang {@code active}, dat {@code email_verified_at} va cap token
 * luon - nguoi dung vua chung minh minh so huu email thi khong can bat dang nhap lai.
 *
 * <p>Database chi luu hash cua ma, khong bao gio luu ma goc.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailVerificationService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Duration RESEND_COUNT_WINDOW = Duration.ofHours(1);

    /** Han muc rieng cho luong nay, khong dung chung voi dat lai mat khau. */
    private static final String RATE_LIMIT_SCOPE = "verify";

    private final UserRepository userRepository;
    private final EmailVerificationRequestRepository verificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpRateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final EmailVerificationProperties properties;
    private final JwtService jwtService;

    /**
     * Phat ma moi cho tai khoan vua dang ky.
     *
     * <p>Goi ngay trong transaction cua {@code register()} nen khong tu mo transaction rieng:
     * tai khoan va ma xac minh phai cung thanh cong hoac cung khong co gi.
     */
    public VerificationSentResponse issueFor(User user, String ip, String userAgent) {
        String email = normalizeEmail(user.getEmail());
        Instant now = Instant.now();

        verificationRepository.invalidatePending(user.getId(), now);

        String code = randomSixDigits();
        EmailVerificationRequest verification = new EmailVerificationRequest();
        verification.setUser(user);
        verification.setEmail(email);
        verification.setVerificationHash(passwordEncoder.encode(code));
        verification.setExpiresAt(now.plus(properties.codeTtlMinutes(), ChronoUnit.MINUTES));
        verification.setRequestedIp(ip);
        verification.setUserAgent(userAgent);
        verification.setResendCount((short) Math.min(
                verificationRepository.countSentSince(email, now.minus(RESEND_COUNT_WINDOW)),
                Short.MAX_VALUE));
        verificationRepository.save(verification);

        // Gui mail chay sau khi commit va o luong khac - xem MailService.
        events.publishEvent(new EmailVerificationCodeIssued(
                email, user.getFullName(), code, properties.codeTtlMinutes()));

        return sentResponse(email);
    }

    /** Nguoi dung bam "Gui lai ma" o man hinh nhap ma. */
    @Transactional
    public VerificationSentResponse resend(String rawEmail, String ip, String userAgent) {
        String email = normalizeEmail(rawEmail);

        // Chan truoc khi tra cuu tai khoan de phan hoi giong nhau voi moi dia chi.
        rateLimiter.checkAndRecord(RATE_LIMIT_SCOPE, email,
                properties.resendCooldownSeconds(), properties.maxSendsPerHour());

        Optional<User> account = userRepository.findByEmailIgnoreCase(email)
                .filter(user -> user.getStatus() == UserStatus.PENDING);
        if (account.isEmpty()) {
            // Khong co tai khoan cho xac minh: van tra ve y het de khong lo email nao ton tai.
            log.info("Yeu cau gui lai ma xac minh cho dia chi khong o trang thai cho");
            return sentResponse(email);
        }

        return issueFor(account.get(), ip, userAgent);
    }

    /**
     * {@code noRollbackFor}: khi nguoi dung nhap sai ma, so lan thu vua tang len phai duoc
     * ghi lai. Neu de ApiException rollback transaction thi bo dem khong bao gio tang va
     * gioi han so lan thu tro thanh vo nghia.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public AuthResponse verify(VerifyEmailRequest request) {
        String email = normalizeEmail(request.email());
        EmailVerificationRequest verification = verificationRepository.findLatestPending(email)
                .orElseThrow(EmailVerificationService::invalidCode);

        Instant now = Instant.now();
        if (verification.getExpiresAt().isBefore(now)) {
            verification.setUsedAt(now);
            throw invalidCode();
        }
        if (verification.getAttemptCount() >= properties.maxAttempts()) {
            verification.setUsedAt(now);
            throw tooManyAttempts();
        }

        if (!passwordEncoder.matches(request.code(), verification.getVerificationHash())) {
            verification.setAttemptCount((short) (verification.getAttemptCount() + 1));
            if (verification.getAttemptCount() >= properties.maxAttempts()) {
                verification.setUsedAt(now);
                throw tooManyAttempts();
            }
            int remaining = properties.maxAttempts() - verification.getAttemptCount();
            throw ApiException.badRequest(ErrorCode.VERIFICATION_CODE_INVALID,
                    "Ma xac minh khong dung. Ban con " + remaining + " lan thu.",
                    Map.of("code", "Ma xac minh khong dung"));
        }

        verification.setVerifiedAt(now);
        verification.setUsedAt(now);

        User user = verification.getUser();
        user.setEmailVerifiedAt(now);
        // Chi kich hoat tai khoan dang cho; tai khoan bi khoa thi xac minh email khong mo lai.
        if (user.getStatus() == UserStatus.PENDING) {
            user.setStatus(UserStatus.ACTIVE);
        } else if (user.getStatus() != UserStatus.ACTIVE) {
            throw ApiException.forbidden(ErrorCode.ACCOUNT_DISABLED,
                    "Tai khoan dang bi khoa.");
        }

        log.info("Tai khoan {} da xac minh email va duoc kich hoat", user.getId());
        return AuthResponse.of(
                jwtService.generateAccessToken(user),
                jwtService.expiresInSeconds(),
                UserResponse.from(user));
    }

    private VerificationSentResponse sentResponse(String email) {
        return new VerificationSentResponse(
                email,
                Duration.ofMinutes(properties.codeTtlMinutes()).toSeconds(),
                properties.resendCooldownSeconds());
    }

    private static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String randomSixDigits() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    /** Gop moi ly do that bai vao mot ma loi de khong lo dia chi nao dang cho xac minh. */
    private static ApiException invalidCode() {
        return ApiException.badRequest(ErrorCode.VERIFICATION_CODE_INVALID,
                "Ma xac minh khong dung hoac da het han. Vui long yeu cau ma moi.",
                Map.of("code", "Ma xac minh khong dung hoac da het han"));
    }

    private static ApiException tooManyAttempts() {
        return ApiException.tooManyRequests(ErrorCode.VERIFICATION_TOO_MANY_ATTEMPTS,
                "Ban da nhap sai qua nhieu lan. Vui long yeu cau ma moi.", Map.of());
    }
}
