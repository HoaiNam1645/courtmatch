package com.courtly.api.owner;

import com.courtly.api.owner.dto.NetIncomeResponse;
import com.courtly.service.owner.OwnerNetIncomeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 2.3.19 - so tien thuc nhan cua tung booking. */
@Validated
@RestController
@RequestMapping("/api/v1/owner/net-income")
@RequiredArgsConstructor
public class OwnerNetIncomeController {

    private final OwnerNetIncomeService netIncomeService;

    @GetMapping
    public ResponseEntity<NetIncomeResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) UUID venueId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") @Min(value = 0, message = "So trang bat dau tu 0") int page,
            @RequestParam(defaultValue = "20")
            @Min(value = 1, message = "Kich thuoc trang tu 1 den 100")
            @Max(value = 100, message = "Kich thuoc trang tu 1 den 100") int size) {
        return ResponseEntity.ok(netIncomeService.list(OwnerVenueController.ownerId(jwt), venueId, from, to, page, size));
    }
}
