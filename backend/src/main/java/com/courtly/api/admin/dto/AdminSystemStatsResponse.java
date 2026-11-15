package com.courtly.api.admin.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 2.3.38 - thong ke he thong cho quan tri vien. Moi nhom du lieu luon day du cac khoa
 * (0 neu chua co) de giao dien khong phai doan.
 */
public record AdminSystemStatsResponse(Overview overview, Trend trend) {

    /** @param byRole moi nguoi dung dem o moi vai tro minh co; byStatus: active, inactive, banned, pending */
    public record Users(long total, Map<String, Long> byRole, Map<String, Long> byStatus,
                        long newToday, long newLast7Days, long newLast30Days) {
    }

    /** @param byApproval pending, approved, rejected; byStatus: pending, active, inactive, rejected */
    public record Venues(long total, Map<String, Long> byApproval, Map<String, Long> byStatus) {
    }

    public record Counted(long total, Map<String, Long> byStatus) {
    }

    /** @param byType pending + reviewing theo loai doi tuong bi bao cao: user, venue, venue_review, partner_review */
    public record Reports(long pending, long reviewing, Map<String, Long> openByType) {
    }

    public record Overview(Users users, Venues venues, Counted bookings, Counted matches, Reports reports) {
    }

    /** @param period ngay (granularity day) hoac ngay dau thang (granularity month), gio Viet Nam */
    public record Point(LocalDate period, long newUsers, long newVenues, long newBookings, long newMatches) {
    }

    /** @param totals tong cac diem trong khoang duoc chon */
    public record Trend(LocalDate from, LocalDate to, String granularity, List<Point> points, Point totals) {
    }
}
