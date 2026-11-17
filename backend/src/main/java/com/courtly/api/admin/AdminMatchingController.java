package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminPartnerAccuracyResponse;
import com.courtly.api.admin.dto.AdminMatchingPerformanceResponse;
import com.courtly.api.admin.dto.AdminMatchingStatsResponse;
import com.courtly.api.admin.dto.AdminModelAnalysisResponse;
import com.courtly.api.admin.dto.AdminTeamAccuracyResponse;
import com.courtly.service.admin.AdminMatchingPerformanceService;
import com.courtly.service.admin.AdminMatchingStatsService;
import com.courtly.service.admin.AdminModelAnalysisService;
import com.courtly.service.admin.AdminPartnerAccuracyService;
import com.courtly.service.admin.AdminTeamAccuracyService;
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
    private final AdminTeamAccuracyService teamAccuracyService;
    private final AdminModelAnalysisService modelAnalysisService;
    private final AdminMatchingPerformanceService performanceService;
    private final AdminMatchingStatsService statsService;

    /** 2.3.70 - do chinh xac ghep cap nguoi choi. */
    @GetMapping("/partner-accuracy")
    public ResponseEntity<AdminPartnerAccuracyResponse> partnerAccuracy(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String source) {
        return ResponseEntity.ok(partnerAccuracyService.get(AdminJwt.adminId(jwt), from, to, source));
    }

    /** 2.3.71 - do chinh xac ghep doi. */
    @GetMapping("/team-accuracy")
    public ResponseEntity<AdminTeamAccuracyResponse> teamAccuracy(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String source) {
        return ResponseEntity.ok(teamAccuracyService.get(AdminJwt.adminId(jwt), from, to, source));
    }

    /** 2.3.72 - phan tich ket qua cham diem cua tung model. */
    @GetMapping("/model-analysis")
    public ResponseEntity<AdminModelAnalysisResponse> modelAnalysis(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String source) {
        return ResponseEntity.ok(modelAnalysisService.get(AdminJwt.adminId(jwt), from, to, source));
    }

    /** 2.3.73 - hieu suat xu ly va chat luong model. */
    @GetMapping("/performance")
    public ResponseEntity<AdminMatchingPerformanceResponse> performance(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(performanceService.get(AdminJwt.adminId(jwt), from, to));
    }

    /** 2.3.74 - thong ke hieu suat ghep cap theo ngay / tuan / thang. */
    @GetMapping("/stats")
    public ResponseEntity<AdminMatchingStatsResponse> stats(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "day") String granularity) {
        return ResponseEntity.ok(statsService.get(AdminJwt.adminId(jwt), from, to, granularity));
    }
}
