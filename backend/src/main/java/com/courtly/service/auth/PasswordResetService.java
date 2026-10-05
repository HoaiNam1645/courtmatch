package com.courtly.service.auth;

import com.courtly.api.auth.dto.PasswordResetConfirmRequest;
import com.courtly.api.auth.dto.PasswordResetSendRequest;
import com.courtly.api.auth.dto.PasswordResetSendResponse;
import com.courtly.api.auth.dto.PasswordResetVerifyRequest;
import com.courtly.api.auth.dto.PasswordResetVerifyResponse;
import com.courtly.common.config.PasswordResetProperties;
import com.courtly.common.enums.ResetChannel;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.PasswordResetRequest;
import com.courtly.domain.account.PasswordResetRequestRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.service.mail.PasswordResetCodeIssued;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Quen va dat lai mat khau (2.1.5) - ba buoc: gui ma, xac minh ma, dat mat khau moi.
 *
 * <p>Nguyen tac xuyen suot: khong buoc nao duoc tiet lo email co ton tai trong he thong
 * hay khong. Buoc gui ma luon tra ve cung mot ket qua; buoc xac minh gop moi truong hop
 * that bai (khong co yeu cau nao, het han, sai ma) vao chung mot ma loi.
 *
 * <p>Database chi luu hash, khong bao gio luu ma goc. Sau khi xac minh dung, hash cua ma
 * 6 so bi ghi de bang hash cua token dat mat khau - ma cu het tac dung ngay lap tuc va
 * mot yeu cau chi doi duoc mot token.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Pattern LOOKS_LIKE_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Duration RESEND_COUNT_WINDOW = Duration.ofHours(1);
    private static final int TOKEN_BYTES = 32;

    /** Han muc rieng cho luong nay, khong dung chung voi xac minh email. */
    private static final String RATE_LIMIT_SCOPE = "reset";

    private final UserRepository userRepository;
    private final PasswordResetRequestRepository resetRepository;
    private final PasswordEncoder passwordEncoder;
    private final OtpRateLimiter rateLimiter;
    private final ApplicationEventPublisher events;
    private final PasswordResetProperties properties;

    // --- Buoc 1: gui ma ----------------------------------------------------

    @Transactional
    public PasswordResetSendResponse sendCode(PasswordResetSendRequest request, String ip, String userAgent) {
        if (request.channel() != ResetChannel.EMAIL) {
            throw ApiException.badRequest(ErrorCode.RESET_CHANNEL_UNSUPPORTED,
                    "Hien tai chi ho tro gui ma xac minh qua email.",
                    Map.of("channel", "Chua ho tro kenh nay"));
        }

        String destination = normalizeEmail(request.destination());
        if (!LOOKS_LIKE_EMAIL.matcher(destination).matches()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "Email khong dung dinh dang.",
                    Map.of("destination", "Email khong dung dinh dang"));
        }

        // Chan truoc khi tra cuu tai khoan de phan hoi giong nhau voi moi dia chi.
        rateLimiter.checkAndRecord(RATE_LIMIT_SCOPE, destination,
                properties.resendCooldownSeconds(), properties.maxSendsPerHour());

        Optional<User> account = userRepository.findByEmailIgnoreCase(destination);
        if (account.isEmpty()) {
            log.info("Yeu cau dat lai mat khau cho email chua dang ky");
        } else if (account.get().getStatus() != UserStatus.ACTIVE) {
            log.info("Yeu cau dat lai mat khau cho tai khoan khong hoat dong: {}", account.get().getId());
        } else {
            issueCode(account.get(), destination, ip, userAgent);
        }

        return new PasswordResetSendResponse(
                Duration.ofMinutes(properties.codeTtlMinutes()).toSeconds(),
                properties.resendCooldownSeconds());
    }

    private void issueCode(User user, String destination, String ip, String userAgent) {
        Instant now = Instant.now();

        // Gui ma moi thi ma cu het hieu luc, tranh viec nhieu ma cung song song.
        resetRepository.invalidatePending(user.getId(), now);

        String code = randomSixDigits();
        PasswordResetRequest reset = new PasswordResetRequest();
        reset.setUser(user);
        reset.setChannel(ResetChannel.EMAIL);
        reset.setDestination(destination);
        reset.setVerificationHash(passwordEncoder.encode(code));
        reset.setExpiresAt(now.plus(properties.codeTtlMinutes(), ChronoUnit.MINUTES));
        reset.setRequestedIp(ip);
        reset.setUserAgent(userAgent);
        reset.setResendCount(countSentInWindow(destination, now));
        resetRepository.save(reset);

        // Gui mail chay sau khi commit va o luong khac - xem MailService.
        events.publishEvent(new PasswordResetCodeIssued(
                destination, user.getFullName(), code, properties.codeTtlMinutes()));
    }

    // --- Buoc 2: xac minh ma -----------------------------------------------

    /**
     * {@code noRollbackFor}: khi nguoi dung nhap sai ma, so lan thu vua tang len phai duoc
     * ghi lai. Neu de ApiException rollback transaction thi bo dem khong bao gio tang va
     * gioi han so lan thu tro thanh vo nghia.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public PasswordResetVerifyResponse verify(PasswordResetVerifyRequest request) {
        String destination = normalizeEmail(request.destination());
        PasswordResetRequest reset = resetRepository.findLatestPending(destination)
                .orElseThrow(PasswordResetService::invalidCode);

        Instant now = Instant.now();
        if (reset.getExpiresAt().isBefore(now)) {
            reset.setUsedAt(now);
            throw invalidCode();
        }
        if (reset.getVerifiedAt() != null) {
            // Da doi lay token roi; ma 6 so khong dung lai duoc nua.
            throw invalidCode();
        }
        if (reset.getAttemptCount() >= properties.maxAttempts()) {
            reset.setUsedAt(now);
            throw tooManyAttempts();
        }

        if (!passwordEncoder.matches(request.code(), reset.getVerificationHash())) {
            reset.setAttemptCount((short) (reset.getAttemptCount() + 1));
            if (reset.getAttemptCount() >= properties.maxAttempts()) {
                reset.setUsedAt(now);
                throw tooManyAttempts();
            }
            int remaining = properties.maxAttempts() - reset.getAttemptCount();
            throw ApiException.badRequest(ErrorCode.RESET_CODE_INVALID,
                    "Ma xac minh khong dung. Ban con " + remaining + " lan thu.",
                    Map.of("code", "Ma xac minh khong dung"));
        }

        String secret = randomToken();
        reset.setVerificationHash(passwordEncoder.encode(secret));
        reset.setVerifiedAt(now);
        // Tu luc nay dong ho dem cho buoc dat mat khau moi.
        reset.setExpiresAt(now.plus(properties.tokenTtlMinutes(), ChronoUnit.MINUTES));

        return new PasswordResetVerifyResponse(reset.getId() + "." + secret,
                Duration.ofMinutes(properties.tokenTtlMinutes()).toSeconds());
    }

    // --- Buoc 3: dat mat khau moi ------------------------------------------

    @Transactional
    public void confirm(PasswordResetConfirmRequest request) {
        String[] parts = request.resetToken().split("\\.", 2);
        if (parts.length != 2) {
            throw invalidToken();
        }
        UUID requestId;
        try {
            requestId = UUID.fromString(parts[0]);
        } catch (IllegalArgumentException e) {
            throw invalidToken();
        }

        PasswordResetRequest reset = resetRepository.findWithUserById(requestId)
                .orElseThrow(PasswordResetService::invalidToken);

        Instant now = Instant.now();
        boolean usable = reset.getVerifiedAt() != null
                && reset.getUsedAt() == null
                && reset.getExpiresAt().isAfter(now)
                && passwordEncoder.matches(parts[1], reset.getVerificationHash());
        if (!usable) {
            throw invalidToken();
        }

        User user = reset.getUser();
        if (user.getPasswordHash() != null
                && passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw ApiException.badRequest(ErrorCode.PASSWORD_SAME_AS_OLD,
                    "Mat khau moi phai khac mat khau hien tai.",
                    Map.of("password", "Mat khau nay trung voi mat khau hien tai"));
        }

        user.setPasswordHash(passwordEncoder.encode(request.password()));
        reset.setUsedAt(now);
        // Cac yeu cau khac cua cung tai khoan (neu co) cung phai het hieu luc.
        resetRepository.invalidatePending(user.getId(), now);

        // Access token da phat truoc do khong luu o server nen khong thu hoi duoc;
        // chung se tu het han theo courtly.security.jwt.expiration-minutes.
        log.info("Tai khoan {} da dat lai mat khau qua email", user.getId());
    }

    // --- Ho tro ------------------------------------------------------------

    private short countSentInWindow(String destination, Instant now) {
        long sent = resetRepository.countSentSince(destination, now.minus(RESEND_COUNT_WINDOW));
        return (short) Math.min(sent, Short.MAX_VALUE);
    }

    private static String normalizeEmail(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
    }

    private static String randomSixDigits() {
        return String.format("%06d", RANDOM.nextInt(1_000_000));
    }

    private static String randomToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Gop moi ly do that bai vao mot ma loi de khong lo dia chi nao dang co yeu cau. */
    private static ApiException invalidCode() {
        return ApiException.badRequest(ErrorCode.RESET_CODE_INVALID,
                "Ma xac minh khong dung hoac da het han. Vui long yeu cau ma moi.",
                Map.of("code", "Ma xac minh khong dung hoac da het han"));
    }

    private static ApiException tooManyAttempts() {
        return ApiException.tooManyRequests(ErrorCode.RESET_TOO_MANY_ATTEMPTS,
                "Ban da nhap sai qua nhieu lan. Vui long yeu cau ma moi.", Map.of());
    }

    private static ApiException invalidToken() {
        return ApiException.badRequest(ErrorCode.RESET_TOKEN_INVALID,
                "Phien dat lai mat khau khong hop le hoac da het han. Vui long lam lai tu dau.",
                Map.of());
    }
}
