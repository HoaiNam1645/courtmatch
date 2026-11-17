package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminPartnerAccuracyResponse;
import com.courtly.service.admin.AdminPartnerAccuracyService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Phan tich va danh gia he thong ghep cap (2.3.70 - 2.3.76), chi doc. */
@RestController
@RequestMapping("/api/v1/admin/matching")
@RequiredArgsConstructor
public class AdminMatchingController {

    private final AdminPartnerAccuracyService partnerAccuracyService;

    /** 2.3.70 - do chinh xac ghep cap nguoi choi. */
    @GetMapping("/partner-accuracy")
    public ResponseEntity<AdminPartnerAccuracyResponse> partnerAccuracy(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String source) {
        return ResponseEntity.ok(partnerAccuracyService.get(AdminJwt.adminId(jwt), from, to, source));
    }
}
