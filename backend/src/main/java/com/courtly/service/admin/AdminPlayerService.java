package com.courtly.service.admin;

import com.courtly.api.admin.dto.AdminPlayerResponses;
import com.courtly.common.enums.SkillLevel;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.AdminPlayerQueries;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.service.booking.AvailabilityService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 2.3.31 - quan tri vien quan ly tai khoan nguoi choi.
 *
 * <p>Khoa nguoi choi khong tu huy booking da xac nhan. Quan tri vien khong xem hay sua mat khau:
 * khong co truong nao tren man hinh nay tra ve mat khau.
 */
@Service
@RequiredArgsConstructor
public class AdminPlayerService {

    private static final Instant FAR_PAST = Instant.parse("2000-01-01T00:00:00Z");
    private static final Instant FAR_FUTURE = Instant.parse("2100-01-01T00:00:00Z");

    private final AdminAccessService accessService;
    private final UserRepository userRepository;
    private final AdminPlayerQueries playerQueries;
    private final AdminUserService userService;

    /**
     * @param status all hoac mot trang thai tai khoan
     * @param skill  all hoac mot trinh do
     * @param from   loc theo ngay dang ky (gio Viet Nam), tinh ca ngay nay
     */
    @Transactional(readOnly = true)
    public AdminPlayerResponses.ListResponse list(UUID adminId, String q, String status, String skill, LocalDate from,
                                                  LocalDate to, int page, int size) {
        accessService.requirePermission(adminId, AdminUserService.PERMISSION);
        if (from != null && to != null && to.isBefore(from)) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Khoang ngay khong hop le.",
                    Map.of("to", "Ngay ket thuc phai sau hoac bang ngay bat dau"));
        }
        List<UserStatus> statuses = parseStatuses(status);
        boolean anySkill = skill == null || skill.isBlank() || "all".equals(skill);
        List<SkillLevel> skills = anySkill ? List.of(SkillLevel.BEGINNER) : List.of(parseSkill(skill));
        String keyword = "%" + (q == null ? "" : q.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")) + "%";
        Instant start = from == null ? FAR_PAST : from.atStartOfDay(AvailabilityService.ZONE).toInstant();
        Instant end = to == null ? FAR_FUTURE : to.plusDays(1).atStartOfDay(AvailabilityService.ZONE).toInstant();

        Page<User> players = userRepository.searchPlayersForAdmin(statuses, anySkill, skills, start, end, keyword, PageRequest.of(page, size));
        Map<UUID, Long> bookings = new HashMap<>();
        if (!players.isEmpty()) {
            playerQueries.bookingCounts(players.getContent().stream().map(User::getId).toList())
                    .forEach(row -> bookings.put(row.getUserId(), row.getTotal()));
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        Arrays.stream(UserStatus.values()).forEach(value -> counts.put(value.getValue(), 0L));
        userRepository.countByStatus().forEach(row -> counts.put(row.getStatus().getValue(), row.getTotal()));

        return new AdminPlayerResponses.ListResponse(players.getContent().stream()
                .map(user -> new AdminPlayerResponses.Summary(user.getId(), user.getFullName(), user.getEmail(), user.getPhone(),
                        user.getStatus().getValue(), skillOf(user), bookings.getOrDefault(user.getId(), 0L),
                        user.getLastLoginAt(), user.getCreatedAt()))
                .toList(),
                players.getNumber(), players.getSize(), players.getTotalElements(), players.getTotalPages(), counts);
    }

    @Transactional(readOnly = true)
    public AdminPlayerResponses.Detail get(UUID adminId, UUID playerId) {
        accessService.requirePermission(adminId, AdminUserService.PERMISSION);
        return toDetail(requirePlayer(playerId));
    }

    /** Khoa nguoi choi: khong dang nhap hay dat san moi duoc; booking da xac nhan van giu nguyen. */
    @Transactional
    public AdminPlayerResponses.Detail lock(UUID adminId, UUID playerId, String reason) {
        User player = requirePlayer(playerId);
        userService.changeStatus(adminId, playerId, UserStatus.BANNED.getValue(), reason);
        return toDetail(player);
    }

    @Transactional
    public AdminPlayerResponses.Detail unlock(UUID adminId, UUID playerId) {
        User player = requirePlayer(playerId);
        userService.changeStatus(adminId, playerId, UserStatus.ACTIVE.getValue(), null);
        return toDetail(player);
    }

    private User requirePlayer(UUID playerId) {
        return userRepository.findById(playerId)
                .filter(user -> user.getUserRoles().stream().anyMatch(item -> Role.PLAYER.equals(item.getRole().getCode())))
                .orElseThrow(() -> ApiException.notFound("Khong tim thay nguoi choi."));
    }

    private AdminPlayerResponses.Detail toDetail(User user) {
        PlayerProfile profile = user.getPlayerProfile();
        Map<String, Long> byStatus = new LinkedHashMap<>();
        long total = 0;
        for (AdminPlayerQueries.StatusCount row : playerQueries.bookingCountsByStatus(user.getId())) {
            byStatus.put(row.getStatus(), row.getTotal());
            total += row.getTotal();
        }
        return new AdminPlayerResponses.Detail(userService.toDetail(user),
                profile == null || profile.getGender() == null ? null : profile.getGender().getValue(),
                profile == null ? null : profile.getDateOfBirth(), skillOf(user),
                profile == null ? null : profile.getSkillScore(),
                profile == null || profile.getDominantHand() == null ? null : profile.getDominantHand().getValue(),
                profile == null ? null : profile.getBio(), total, byStatus, playerQueries.matchCount(user.getId()),
                playerQueries.reportCount(user.getId()),
                playerQueries.recentBookings(user.getId()).stream()
                        .map(row -> new AdminPlayerResponses.RecentBooking(row.getId(), row.getBookingCode(), row.getVenueName(),
                                row.getStatus(), row.getTotalAmount(), row.getCreatedAt()))
                        .toList());
    }

    private static String skillOf(User user) {
        PlayerProfile profile = user.getPlayerProfile();
        return profile == null || profile.getSkillLevel() == null ? null : profile.getSkillLevel().getValue();
    }

    private static List<UserStatus> parseStatuses(String status) {
        if (status == null || status.isBlank() || "all".equals(status)) {
            return List.of(UserStatus.values());
        }
        try {
            return List.of(UserStatus.from(status));
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trang thai tai khoan khong hop le.",
                    Map.of("status", "Chi nhan all, active, inactive, banned hoac pending"));
        }
    }

    private static SkillLevel parseSkill(String skill) {
        try {
            return SkillLevel.from(skill);
        } catch (IllegalArgumentException exception) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED, "Trinh do khong hop le.",
                    Map.of("skill", "Chi nhan all, beginner, intermediate, advanced hoac professional"));
        }
    }
}
