package com.courtly.api.partner;

import com.courtly.api.partner.dto.PartnerSearchQuery;
import com.courtly.api.partner.dto.PartnerSearchResponse;
import com.courtly.common.PageResponse;
import com.courtly.service.matching.PartnerSearchService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** API tim doi tac cua nguoi choi dang dang nhap. */
@Validated
@RestController
@RequestMapping("/api/v1/partners")
@RequiredArgsConstructor
public class PartnerSearchController {

    private final PartnerSearchService partnerSearchService;

    @GetMapping("/search")
    public ResponseEntity<PageResponse<PartnerSearchResponse>> search(
            @AuthenticationPrincipal Jwt jwt,
            @Valid PartnerSearchQuery query) {
        return ResponseEntity.ok(partnerSearchService.search(
                UUID.fromString(jwt.getSubject()), query));
    }
}
