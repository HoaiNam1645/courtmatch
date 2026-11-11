package com.courtly.api.partner;

import com.courtly.api.partner.dto.PartnerConnectionResponse;
import com.courtly.common.PageResponse;
import com.courtly.service.matching.PartnerConnectionService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API danh sach doi tac da ket noi cua nguoi choi. */
@Validated
@RestController
@RequestMapping("/api/v1/partners/connections")
@RequiredArgsConstructor
public class PartnerConnectionController {

    private final PartnerConnectionService connectionService;

    /** 2.2.36 - chi tra cac ket noi dang hoat dong cua nguoi dang nhap. */
    @GetMapping
    public ResponseEntity<PageResponse<PartnerConnectionResponse>> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(connectionService.listActive(
                UUID.fromString(jwt.getSubject()), page, size));
    }
}
