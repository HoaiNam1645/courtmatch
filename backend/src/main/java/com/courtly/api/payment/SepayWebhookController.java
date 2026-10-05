package com.courtly.api.payment;

import com.courtly.api.payment.dto.SepayWebhookAck;
import com.courtly.api.payment.dto.SepayWebhookRequest;
import com.courtly.service.payment.SepayWebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diem nhan webhook tu SePay (2.1.37).
 *
 * <p>Endpoint nay cong khai voi Internet nen khong dung JWT ma kiem tra khoa rieng do SePay gui
 * trong header {@code Authorization: Apikey <key>}.
 *
 * <p>Khong dung {@code @Valid}: payload sai dinh dang van phai duoc ghi nhat ky de doi soat,
 * tu choi tu vong ngoai se lam mat dau vet.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/payments/sepay")
@RequiredArgsConstructor
public class SepayWebhookController {

    private final SepayWebhookService sepayWebhookService;

    @PostMapping("/webhook")
    public ResponseEntity<SepayWebhookAck> receive(
            @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorization,
            @RequestBody SepayWebhookRequest request) {

        if (!sepayWebhookService.isAuthorized(authorization)) {
            log.warn("Tu choi webhook SePay: khoa khong hop le");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new SepayWebhookAck(false, "Khoa khong hop le"));
        }

        return ResponseEntity.ok(sepayWebhookService.handle(request));
    }
}
