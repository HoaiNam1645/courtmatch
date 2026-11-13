package com.courtly.api.owner;

import com.courtly.api.auth.dto.UserResponse;
import com.courtly.api.owner.dto.ChangePasswordRequest;
import com.courtly.service.owner.OwnerAccountService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.1 - tai khoan chu san do quan tri vien cap. */
@RestController
@RequestMapping("/api/v1/owner/account")
@RequiredArgsConstructor
public class OwnerAccountController {

    private final OwnerAccountService ownerAccountService;

    /** Doi mat khau tam; thanh cong thi chu san duoc vao trang quan ly. */
    @PostMapping("/password")
    public ResponseEntity<UserResponse> changePassword(@AuthenticationPrincipal Jwt jwt,
                                                       @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(ownerAccountService.changePassword(
                UUID.fromString(jwt.getSubject()), request));
    }
}
