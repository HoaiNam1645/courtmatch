package com.courtly.service.admin;

import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Khoang ngay (gio Viet Nam, tinh ca ngay cuoi) dung cho cac bao cao ghep cap quan tri. */
record MatchingRange(LocalDate from, LocalDate to) {

    static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    static final int DEFAULT_DAYS = 30;
    static final int MAX_DAYS = 366;

    static MatchingRange of(LocalDate from, LocalDate to) {
        LocalDate end = to == null ? LocalDate.now(ZONE) : to;
        LocalDate start = from == null ? end.minusDays(DEFAULT_DAYS - 1L) : from;
        if (end.isBefore(start)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Ngay ket thuc phai sau hoac bang ngay bat dau.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        if (ChronoUnit.DAYS.between(start, end) + 1 > MAX_DAYS) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Chi xem toi da " + MAX_DAYS + " ngay moi lan.",
                    Map.of("to", "Khoang thoi gian toi da " + MAX_DAYS + " ngay"));
        }
        return new MatchingRange(start, end);
    }

    Instant fromInstant() {
        return from.atStartOfDay(ZONE).toInstant();
    }

    /** Dau khoang mo: ngay sau ngay cuoi. */
    Instant toInstant() {
        return to.plusDays(1).atStartOfDay(ZONE).toInstant();
    }

    /** Nguon cham diem hop le: null (tat ca), ai hoac baseline. */
    static String source(String source) {
        if (source == null || source.isBlank()) return null;
        if (source.equals("ai") || source.equals("baseline")) return source;
        throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Nguon cham diem khong hop le.",
                Map.of("source", "Chi nhan ai hoac baseline"));
    }
}
