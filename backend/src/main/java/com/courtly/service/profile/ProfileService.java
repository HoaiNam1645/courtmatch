package com.courtly.service.profile;

import com.courtly.api.auth.dto.UserResponse;
import com.courtly.api.profile.dto.AvailabilitySlotDto;
import com.courtly.api.profile.dto.PlayerProfileDto;
import com.courtly.api.profile.dto.PlayerStatisticsDto;
import com.courtly.api.profile.dto.PreferredLocationDto;
import com.courtly.api.profile.dto.ProfileResponse;
import com.courtly.api.profile.dto.UpdatePreferencesRequest;
import com.courtly.api.profile.dto.UpdateProfileRequest;
import com.courtly.common.exception.ApiException;
import com.courtly.common.exception.ErrorCode;
import com.courtly.domain.account.PlayerAvailabilitySlot;
import com.courtly.domain.account.PlayerAvailabilitySlotRepository;
import com.courtly.domain.account.PlayerPreferredLocation;
import com.courtly.domain.account.PlayerPreferredLocationRepository;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ho so nguoi choi: xem (2.1.6), chinh sua (2.1.7) va thiet lap choi
 * (2.1.8, 2.1.9, 2.1.10, 2.1.11, 2.1.12).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProfileService {

    private static final Pattern VN_MOBILE = Pattern.compile("^0[35789]\\d{8}$");

    private final UserRepository userRepository;
    private final PlayerStatisticsRepository statisticsRepository;
    private final PlayerAvailabilitySlotRepository availabilityRepository;
    private final PlayerPreferredLocationRepository preferredLocationRepository;

    /** 2.1.6 - toan bo du lieu ho so cho ca /profile va /profile/preferences. */
    @Transactional(readOnly = true)
    public ProfileResponse getProfile(UUID userId) {
        User user = requireUser(userId);
        return buildResponse(user);
    }

    /** 2.1.7 - cap nhat thong tin lien he va thong tin ca nhan. */
    @Transactional
    public ProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = requireUser(userId);
        String phone = request.phone() == null ? null : normalizePhone(request.phone());

        if (phone != null && !VN_MOBILE.matcher(phone).matches()) {
            throw ApiException.badRequest(ErrorCode.VALIDATION_FAILED,
                    "So dien thoai khong hop le.",
                    Map.of("phone", "So dien thoai di dong Viet Nam gom 10 so, bat dau bang 03, 05, 07, 08 hoac 09"));
        }
        // Chi bao trung khi email/so dien thoai thuoc ve tai khoan khac.
        if (request.email() != null) {
            userRepository.findByEmailIgnoreCase(request.email())
                    .filter(other -> !other.getId().equals(userId))
                    .ifPresent(other -> {
                        throw ApiException.conflict(ErrorCode.EMAIL_ALREADY_EXISTS,
                                "Email nay da duoc tai khoan khac su dung.",
                                Map.of("email", "Email da ton tai"));
                    });
        }
        if (phone != null) {
            userRepository.findByPhone(phone)
                    .filter(other -> !other.getId().equals(userId))
                    .ifPresent(other -> {
                        throw ApiException.conflict(ErrorCode.PHONE_ALREADY_EXISTS,
                                "So dien thoai nay da duoc tai khoan khac su dung.",
                                Map.of("phone", "So dien thoai da ton tai"));
                    });
        }

        user.setFullName(request.fullName());
        user.setEmail(request.email());
        user.setPhone(phone);

        PlayerProfile profile = requireProfile(user);
        profile.setGender(request.gender());
        profile.setDateOfBirth(request.dateOfBirth());
        profile.setBio(request.bio());

        log.info("Cap nhat ho so cua {}", userId);
        return buildResponse(user);
    }

    /** 2.1.8, 2.1.9, 2.1.12 cung voi 2.1.10 va 2.1.11 - man hinh chi co mot nut luu. */
    @Transactional
    public ProfileResponse updatePreferences(UUID userId, UpdatePreferencesRequest request) {
        User user = requireUser(userId);

        PlayerProfile profile = requireProfile(user);
        profile.setSkillLevel(request.skillLevel());
        profile.setDominantHand(request.dominantHand());
        profile.setPlayingStyle(request.playingStyle());
        profile.setPreferredPlayType(request.preferredPlayType());

        replaceAvailability(user, request.availability());
        replacePreferredLocations(user, request.preferredLocations());

        log.info("Cap nhat thiet lap choi cua {}", userId);
        return buildResponse(user);
    }

    /**
     * Ghi de toan bo danh sach thay vi sua tung dong.
     *
     * <p>Giao dien gui len tron ven trang thai moi sau khi nguoi dung bam luu, va
     * cac ban ghi nay khong duoc bang nao khac tham chieu toi.
     */
    private void replaceAvailability(User user, List<AvailabilitySlotDto> slots) {
        availabilityRepository.deleteAllByUserId(user.getId());
        // Xoa truoc khi chen de khong dung unique/check con lai cua ban ghi cu.
        availabilityRepository.flush();

        for (AvailabilitySlotDto dto : slots) {
            PlayerAvailabilitySlot slot = new PlayerAvailabilitySlot();
            slot.setUser(user);
            slot.setDayOfWeek(dto.dayOfWeek());
            slot.setStartTime(dto.startTime());
            slot.setEndTime(dto.endTime());
            availabilityRepository.save(slot);
        }
    }

    private void replacePreferredLocations(User user, List<PreferredLocationDto> locations) {
        preferredLocationRepository.deleteAllByUserId(user.getId());
        preferredLocationRepository.flush();

        for (PreferredLocationDto dto : locations) {
            PlayerPreferredLocation location = new PlayerPreferredLocation();
            location.setUser(user);
            location.setLabel(dto.label());
            location.setAddress(dto.address());
            location.setLatitude(dto.latitude());
            location.setLongitude(dto.longitude());
            location.setRadiusKm(dto.radiusKm());
            location.setDefault(dto.isDefault());
            preferredLocationRepository.save(location);
        }
    }

    private ProfileResponse buildResponse(User user) {
        PlayerProfile profile = requireProfile(user);

        PlayerStatisticsDto statistics = statisticsRepository.findById(user.getId())
                .map(ProfileService::toStatisticsDto)
                .orElseGet(PlayerStatisticsDto::empty);

        List<AvailabilitySlotDto> availability =
                availabilityRepository.findAllByUserIdOrderByDayOfWeekAscStartTimeAsc(user.getId()).stream()
                        .map(slot -> new AvailabilitySlotDto(
                                slot.getDayOfWeek(), slot.getStartTime(), slot.getEndTime()))
                        .toList();

        List<PreferredLocationDto> locations =
                preferredLocationRepository.findAllByUserIdOrderByCreatedAtAsc(user.getId()).stream()
                        .map(location -> new PreferredLocationDto(
                                location.getId(),
                                location.getLabel(),
                                location.getAddress(),
                                location.getLatitude(),
                                location.getLongitude(),
                                location.getRadiusKm(),
                                location.isDefault()))
                        .toList();

        return new ProfileResponse(
                UserResponse.from(user),
                new PlayerProfileDto(
                        profile.getGender(),
                        profile.getDateOfBirth(),
                        profile.getBio(),
                        profile.getSkillLevel(),
                        profile.getSkillScore(),
                        profile.getDominantHand(),
                        profile.getPlayingStyle(),
                        profile.getPreferredPlayType()),
                statistics,
                availability,
                locations);
    }

    private static PlayerStatisticsDto toStatisticsDto(PlayerStatistics statistics) {
        return new PlayerStatisticsDto(
                statistics.getTotalMatches(),
                statistics.getTotalWins(),
                statistics.getTotalLosses(),
                statistics.getWinRate(),
                statistics.getRatingScore(),
                statistics.getLastPlayedAt());
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Khong tim thay tai khoan."));
    }

    /** Tai khoan tao truoc khi co bang player_profiles thi tao bo sung khi truy cap. */
    private PlayerProfile requireProfile(User user) {
        PlayerProfile profile = user.getPlayerProfile();
        if (profile == null) {
            profile = new PlayerProfile(user);
            user.setPlayerProfile(profile);
        }
        return profile;
    }

    private static String normalizePhone(String raw) {
        String digitsOnly = raw.replaceAll("[^0-9+]", "");
        if (digitsOnly.startsWith("+84")) {
            return "0" + digitsOnly.substring(3);
        }
        if (digitsOnly.startsWith("84") && digitsOnly.length() == 11) {
            return "0" + digitsOnly.substring(2);
        }
        return digitsOnly;
    }
}
