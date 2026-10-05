package com.courtly.api.auth;

import com.courtly.api.auth.dto.PasswordResetConfirmRequest;
import com.courtly.api.auth.dto.PasswordResetSendRequest;
import com.courtly.api.auth.dto.PasswordResetSendResponse;
import com.courtly.api.auth.dto.PasswordResetVerifyRequest;
import com.courtly.api.auth.dto.PasswordResetVerifyResponse;
import com.courtly.service.auth.PasswordResetService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.1.5 - quen va dat lai mat khau. Ca ba buoc deu khong can dang nhap. */
@RestController
@RequestMapping("/api/v1/auth/password-reset")
@RequiredArgsConstructor
public class PasswordResetController {

    private static final int USER_AGENT_MAX_LENGTH = 512;

    private final PasswordResetService passwordResetService;

    /** Buoc 1 - gui ma xac minh toi email. Luon tra 200 du email co ton tai hay khong. */
    @PostMapping
    public ResponseEntity<PasswordResetSendResponse> sendCode(
            @Valid @RequestBody PasswordResetSendRequest request,
            HttpServletRequest httpRequest) {
        return ResponseEntity.ok(passwordResetService.sendCode(
                request, clientIp(httpRequest), userAgent(httpRequest)));
    }

    /** Buoc 2 - doi ma 6 so lay token dung mot lan. */
    @PostMapping("/verify")
    public ResponseEntity<PasswordResetVerifyResponse> verify(
            @Valid @RequestBody PasswordResetVerifyRequest request) {
        return ResponseEntity.ok(passwordResetService.verify(request));
    }

    /** Buoc 3 - dat mat khau moi. Khong tra token dang nhap: nguoi dung dang nhap lai. */
    @PostMapping("/confirm")
    public ResponseEntity<Void> confirm(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirm(request);
        return ResponseEntity.noContent().build();
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
