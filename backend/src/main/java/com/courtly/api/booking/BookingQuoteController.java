package com.courtly.api.booking;

import com.courtly.api.booking.dto.QuoteRequest;
import com.courtly.api.booking.dto.QuoteResponse;
import com.courtly.service.booking.QuoteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tam tinh tien truoc khi tao don (2.1.27).
 *
 * <p>Cong khai vi nguoi dung xem gia duoc truoc khi dang nhap; chi den buoc tao don
 * that moi bat buoc dang nhap.
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
public class BookingQuoteController {

    private final QuoteService quoteService;

    @PostMapping("/quote")
    public ResponseEntity<QuoteResponse> quote(@Valid @RequestBody QuoteRequest request) {
        return ResponseEntity.ok(quoteService.quote(request));
    }
}
