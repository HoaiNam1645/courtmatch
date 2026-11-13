package com.courtly.api.owner;

import com.courtly.api.owner.dto.PriceRuleInput;
import com.courtly.api.owner.dto.VenuePriceRulesResponse;
import com.courtly.service.owner.OwnerPriceRuleService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.5 - bang gia cua cac san con. */
@RestController
@RequestMapping("/api/v1/owner/venues/{venueId}")
@RequiredArgsConstructor
public class OwnerPriceRuleController {

    private final OwnerPriceRuleService priceRuleService;

    @GetMapping("/price-rules")
    public ResponseEntity<VenuePriceRulesResponse> list(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID venueId) {
        return ResponseEntity.ok(priceRuleService.list(OwnerVenueController.ownerId(jwt), venueId));
    }

    @PostMapping("/courts/{courtId}/price-rules")
    public ResponseEntity<VenuePriceRulesResponse.PriceRuleItem> create(@AuthenticationPrincipal Jwt jwt,
                                                                        @PathVariable UUID venueId,
                                                                        @PathVariable UUID courtId,
                                                                        @Valid @RequestBody PriceRuleInput request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(priceRuleService.create(OwnerVenueController.ownerId(jwt), venueId, courtId, request));
    }

    @PutMapping("/courts/{courtId}/price-rules/{ruleId}")
    public ResponseEntity<VenuePriceRulesResponse.PriceRuleItem> update(@AuthenticationPrincipal Jwt jwt,
                                                                        @PathVariable UUID venueId,
                                                                        @PathVariable UUID courtId,
                                                                        @PathVariable UUID ruleId,
                                                                        @Valid @RequestBody PriceRuleInput request) {
        return ResponseEntity.ok(priceRuleService.update(OwnerVenueController.ownerId(jwt), venueId, courtId, ruleId, request));
    }

    /** Ngung ap dung quy tac gia; ban ghi duoc giu lai lam lich su. */
    @DeleteMapping("/courts/{courtId}/price-rules/{ruleId}")
    public ResponseEntity<Void> deactivate(@AuthenticationPrincipal Jwt jwt,
                                           @PathVariable UUID venueId,
                                           @PathVariable UUID courtId,
                                           @PathVariable UUID ruleId) {
        priceRuleService.deactivate(OwnerVenueController.ownerId(jwt), venueId, courtId, ruleId);
        return ResponseEntity.noContent().build();
    }
}
