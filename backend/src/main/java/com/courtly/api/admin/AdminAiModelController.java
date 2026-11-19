package com.courtly.api.admin;

import com.courtly.api.admin.dto.AdminAiModelResponses;
import com.courtly.service.admin.AdminAiModelService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 2.2.16, 2.2.19 - quan ly phien ban model AI (nhap, kich hoat, quay lai). */
@Validated
@RestController
@RequestMapping("/api/v1/admin/ai-models")
@RequiredArgsConstructor
public class AdminAiModelController {

    private final AdminAiModelService service;

    public record ImportRequest(@NotBlank(message = "Hay chon loai model") String modelType,
                                @NotBlank(message = "Hay chon phien ban") @Size(max = 50, message = "Phien ban toi da 50 ky tu") String modelVersion) {
    }

    @GetMapping
    public ResponseEntity<AdminAiModelResponses.ListResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(service.list(AdminJwt.adminId(jwt)));
    }

    @PostMapping("/import")
    public ResponseEntity<AdminAiModelResponses.Model> importModel(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ImportRequest request) {
        return ResponseEntity.status(201).body(service.importModel(AdminJwt.adminId(jwt), request.modelType(), request.modelVersion()));
    }

    @PostMapping("/{modelId}/activate")
    public ResponseEntity<AdminAiModelResponses.Model> activate(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID modelId) {
        return ResponseEntity.ok(service.activate(AdminJwt.adminId(jwt), modelId));
    }

    @PostMapping("/{modelId}/rollback")
    public ResponseEntity<AdminAiModelResponses.Model> rollback(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID modelId) {
        return ResponseEntity.ok(service.rollback(AdminJwt.adminId(jwt), modelId));
    }
}
