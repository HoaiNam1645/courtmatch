package com.courtly.api.admin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 2.3.74 - thong ke hieu suat ghep cap theo ngay / tuan / thang. Yeu cau duoc tinh theo ngay gui va trang thai
 * hien tai; ket noi tinh mot lan cho moi cap (khong tinh hai chieu).
 */
public record AdminMatchingStatsResponse(LocalDate from, LocalDate to, String granularity, Point totals,
                                         Double avgResponseMinutes, long responseSamples, List<Point> points) {

    /**
     * @param period ngay (day), thu Hai dau tuan (week) hoac ngay dau thang (month), gio Viet Nam; null o tong
     */
    public record Point(LocalDate period, long searches, long suggestions, long requests, long accepted,
                        long rejected, long expired, long cancelled, long connections, long teamRuns,
                        long teamOptions) {
    }
}
