package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminOwnerResponses;
import com.courtly.api.admin.dto.ChangeUserStatusRequest;
import com.courtly.api.admin.dto.CreateOwnerAccountRequest;
import com.courtly.api.admin.dto.ReviewOwnerRequest;
import com.courtly.service.admin.AdminOwnerAccountService;
import com.courtly.service.admin.AdminOwnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.32 - tai khoan chu san duy nhat. */
@RestController
@RequestMapping("/api/v1/admin/owner")
@RequiredArgsConstructor
public class AdminOwnerController {

    private final AdminOwnerService ownerService;
    private final AdminOwnerAccountService accountService;

    @GetMapping
    public ResponseEntity<AdminOwnerResponses.Detail> get(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ownerService.get(AdminJwt.adminId(jwt)));
    }

    @PostMapping("/verification")
    public ResponseEntity<AdminOwnerResponses.Detail> review(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ReviewOwnerRequest request) {
        return ResponseEntity.ok(ownerService.review(AdminJwt.adminId(jwt), request.status(), request.reason()));
    }

    /** 2.3.33 - cap tai khoan chu san; mat khau tam chi tra ve mot lan nen khong duoc luu cache. */
    @PostMapping("/account")
    public ResponseEntity<AdminOwnerResponses.Credentials> create(@AuthenticationPrincipal Jwt jwt,
                                                                  @Valid @RequestBody CreateOwnerAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(accountService.create(AdminJwt.adminId(jwt), request));
    }

    /** 2.3.33 - khoa, ngung hoac kich hoat lai tai khoan chu san. */
    @PostMapping("/account/status")
    public ResponseEntity<AdminOwnerResponses.Detail> changeStatus(@AuthenticationPrincipal Jwt jwt,
                                                                   @Valid @RequestBody ChangeUserStatusRequest request) {
        return ResponseEntity.ok(accountService.changeStatus(AdminJwt.adminId(jwt), request.status(), request.reason()));
    }

    /** 2.3.33 - dat lai thong tin dang nhap bang mat khau tam moi. */
    @PostMapping("/account/reset-credentials")
    public ResponseEntity<AdminOwnerResponses.Credentials> resetCredentials(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accountService.resetCredentials(AdminJwt.adminId(jwt)));
    }
}
