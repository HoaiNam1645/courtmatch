package com.courtly.api.auth;

import com.courtly.api.auth.dto.AuthResponse;
import com.courtly.api.auth.dto.LoginRequest;
import com.courtly.api.auth.dto.RegisterRequest;
import com.courtly.api.auth.dto.ResendVerificationRequest;
import com.courtly.api.auth.dto.UserResponse;
import com.courtly.api.auth.dto.VerificationSentResponse;
import com.courtly.api.auth.dto.VerifyEmailRequest;
import com.courtly.service.auth.AuthService;
import com.courtly.service.auth.EmailVerificationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.1.1 dang ky, 2.1.2 dang nhap, 2.1.4 dang xuat. */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final int USER_AGENT_MAX_LENGTH = 512;

    private final AuthService authService;
    private final EmailVerificationService emailVerificationService;

    /**
     * 2.1.1 - dang ky tai khoan nguoi choi.
     *
     * <p>Tra 201 nhung KHONG kem access token: tai khoan o trang thai cho xac minh email.
     * Frontend chuyen sang man hinh nhap ma.
     */
    @PostMapping("/register")
    public ResponseEntity<VerificationSentResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(
                request, clientIp(httpRequest), userAgent(httpRequest)));
    }

    /** 2.1.1 - nhap ma xac minh. Dung ma thi kich hoat tai khoan va cap token luon. */
    @PostMapping("/verify-email")
    public ResponseEntity<AuthResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        return ResponseEntity.ok(emailVerificationService.verify(request));
    }

    /** 2.1.1 - gui lai ma xac minh. */
    @PostMapping("/verify-email/resend")
    public ResponseEntity<VerificationSentResponse> resendVerification(
            @Valid @RequestBody ResendVerificationRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(emailVerificationService.resend(
                request.email(), clientIp(httpRequest), userAgent(httpRequest)));
    }

    /** 2.1.2 - dang nhap bang email hoac so dien thoai. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * 2.1.4 - dang xuat.
     *
     * <p>Phien dang dung access token khong luu o server (quyet dinh cua du an), nen viec
     * thu hoi thuc te la frontend xoa token. Endpoint nay ton tai de frontend co mot diem
     * goi thong nhat va de sau nay them thu hoi token ma khong phai doi hop dong API.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout() {
        return ResponseEntity.noContent().build();
    }

    /** Thong tin tai khoan dang dang nhap, dung de dung lai header sau khi tai lai trang. */
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(authService.currentUser(UUID.fromString(jwt.getSubject())));
    }
    /** Cot requested_ip kieu inet nen gia tri sai dinh dang phai thanh null. */
    private String clientIp(HttpServletRequest request) {
        String address = request.getRemoteAddr();
        return address == null || address.isBlank() ? null : address;
    }

    private String userAgent(HttpServletRequest request) {
        String value = request.getHeader("User-Agent");
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.length() > USER_AGENT_MAX_LENGTH ? value.substring(0, USER_AGENT_MAX_LENGTH) : value;
    }

}
