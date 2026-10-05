package com.courtly.seed;

import com.courtly.common.enums.DominantHand;
import com.courtly.common.enums.Gender;
import com.courtly.common.enums.PlayType;
import com.courtly.common.enums.PlayingStyle;
import com.courtly.common.enums.ResetChannel;
import com.courtly.common.enums.SkillLevel;
import com.courtly.common.enums.UserStatus;
import com.courtly.common.enums.VerificationStatus;
import com.courtly.domain.account.AuthProvider;
import com.courtly.domain.account.AuthProviderRepository;
import com.courtly.domain.account.CourtOwnerProfile;
import com.courtly.domain.account.EmailVerificationRequest;
import com.courtly.domain.account.EmailVerificationRequestRepository;
import com.courtly.domain.account.PasswordResetRequest;
import com.courtly.domain.account.PasswordResetRequestRepository;
import com.courtly.domain.account.PlayerAvailabilitySlot;
import com.courtly.domain.account.PlayerAvailabilitySlotRepository;
import com.courtly.domain.account.PlayerPreferredLocation;
import com.courtly.domain.account.PlayerPreferredLocationRepository;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.courtly.domain.notification.NotificationPreference;
import com.courtly.domain.notification.NotificationPreferenceRepository;
import com.courtly.domain.notification.NotificationType;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Buoc 2: tai khoan admin, nhan vien, chu san va nguoi choi kem ho so. */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class UserSeeder implements Seeder {

    /** Mat khau chung cho moi tai khoan mau. Chi dung o moi truong dev. */
    public static final String DEFAULT_PASSWORD = "Courtly@123";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthProviderRepository authProviderRepository;
    private final PasswordResetRequestRepository passwordResetRequestRepository;
    private final EmailVerificationRequestRepository emailVerificationRequestRepository;
    private final PlayerAvailabilitySlotRepository availabilitySlotRepository;
    private final PlayerPreferredLocationRepository preferredLocationRepository;
    private final PlayerStatisticsRepository playerStatisticsRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PasswordEncoder passwordEncoder;

    /** Ho so nguoi choi mau, da dang trinh do va phong cach de phan cum co y nghia. */
    private record PlayerSeed(String key, String fullName, String email, String phone,
                              Gender gender, LocalDate dateOfBirth, SkillLevel skillLevel,
                              double skillScore, DominantHand hand, PlayingStyle style,
                              PlayType playType, String district,
                              double latitude, double longitude) {
    }

    private static final List<PlayerSeed> PLAYERS = List.of(
            new PlayerSeed("player-01", "Nguyen Van An", "an.nguyen@example.com", "0901000001",
                    Gender.MALE, LocalDate.of(1995, 3, 12), SkillLevel.ADVANCED, 1420,
                    DominantHand.RIGHT, PlayingStyle.ATTACKING, PlayType.DOUBLE, "Hai Chau", 16.0609, 108.2237),
            new PlayerSeed("player-02", "Tran Thi Binh", "binh.tran@example.com", "0901000002",
                    Gender.FEMALE, LocalDate.of(1998, 7, 24), SkillLevel.INTERMEDIATE, 1180,
                    DominantHand.RIGHT, PlayingStyle.BALANCED, PlayType.MIXED, "Thanh Khe", 16.0655, 108.1858),
            new PlayerSeed("player-03", "Le Minh Chau", "chau.le@example.com", "0901000003",
                    Gender.MALE, LocalDate.of(1992, 11, 5), SkillLevel.PROFESSIONAL, 1680,
                    DominantHand.LEFT, PlayingStyle.ATTACKING, PlayType.SINGLE, "Son Tra", 16.0837, 108.2372),
            new PlayerSeed("player-04", "Pham Thu Dung", "dung.pham@example.com", "0901000004",
                    Gender.FEMALE, LocalDate.of(2000, 1, 18), SkillLevel.BEGINNER, 880,
                    DominantHand.RIGHT, PlayingStyle.DEFENSIVE, PlayType.DOUBLE, "Ngu Hanh Son", 16.0042, 108.2522),
            new PlayerSeed("player-05", "Hoang Quoc Dat", "dat.hoang@example.com", "0901000005",
                    Gender.MALE, LocalDate.of(1997, 5, 30), SkillLevel.INTERMEDIATE, 1210,
                    DominantHand.RIGHT, PlayingStyle.BALANCED, PlayType.DOUBLE, "Lien Chieu", 16.0747, 108.1497),
            new PlayerSeed("player-06", "Vu Ngoc Ha", "ha.vu@example.com", "0901000006",
                    Gender.FEMALE, LocalDate.of(1996, 9, 9), SkillLevel.ADVANCED, 1395,
                    DominantHand.RIGHT, PlayingStyle.ATTACKING, PlayType.MIXED, "Hai Chau", 16.0615, 108.224500),
            new PlayerSeed("player-07", "Dang Hoai Khanh", "khanh.dang@example.com", "0901000007",
                    Gender.MALE, LocalDate.of(1994, 2, 14), SkillLevel.INTERMEDIATE, 1150,
                    DominantHand.LEFT, PlayingStyle.DEFENSIVE, PlayType.SINGLE, "Cam Le", 16.0192, 108.1995),
            new PlayerSeed("player-08", "Bui Thanh Lam", "lam.bui@example.com", "0901000008",
                    Gender.MALE, LocalDate.of(1999, 12, 2), SkillLevel.BEGINNER, 920,
                    DominantHand.RIGHT, PlayingStyle.BALANCED, PlayType.DOUBLE, "Thanh Khe", 16.065, 108.18650));

    private record OwnerSeed(String key, String fullName, String email, String phone,
                             String businessName, String bankAccountNo) {
    }

    private static final List<OwnerSeed> OWNERS = List.of(
            new OwnerSeed("owner-01", "Nguyen Huu Thang", "thang.owner@example.com", "0902000001",
                    "Ho kinh doanh San Cau Long Hai Chau", "0359111001"),
            new OwnerSeed("owner-02", "Tran Quang Hieu", "hieu.owner@example.com", "0902000002",
                    "Cong ty TNHH The Thao Son Tra", "0359111002"),
            new OwnerSeed("owner-03", "Do Thi Mai", "mai.owner@example.com", "0902000003",
                    "Ho kinh doanh Green Court", "0359111003"));

    @Override
    public String name() {
        return "users + profiles";
    }

    @Override
    @Transactional
    public void seed() {
        if (userRepository.count() > 0) {
            log.info("  [users] da co du lieu, bo qua");
            return;
        }

        Role adminRole = requireRole(Role.ADMIN);
        Role staffRole = requireRole(Role.STAFF);
        Role ownerRole = requireRole(Role.OWNER);
        Role playerRole = requireRole(Role.PLAYER);
        String hashedPassword = passwordEncoder.encode(DEFAULT_PASSWORD);

        // --- Quan tri vien ---
        User admin = newUser("admin", "Quan Tri Courtly", "admin@courtly.vn", "0900000001", hashedPassword);
        admin.getUserRoles().add(new UserRole(admin, adminRole));
        userRepository.save(admin);
        seedNotificationPreferences(admin);

        // --- Nhan vien san ---
        User staff = newUser("staff-01", "Le Van Nhan", "nhan.staff@example.com", "0903000001", hashedPassword);
        staff.getUserRoles().add(new UserRole(staff, staffRole));
        userRepository.save(staff);
        seedNotificationPreferences(staff);

        // --- Chu san ---
        for (OwnerSeed seed : OWNERS) {
            User owner = newUser(seed.key(), seed.fullName(), seed.email(), seed.phone(), hashedPassword);
            owner.getUserRoles().add(new UserRole(owner, ownerRole));

            CourtOwnerProfile profile = new CourtOwnerProfile(owner);
            profile.setBusinessName(seed.businessName());
            profile.setTaxCode("01" + seed.bankAccountNo());
            profile.setBankName("MBBank");
            profile.setBankAccountNo(seed.bankAccountNo());
            profile.setBankAccountName(seed.fullName().toUpperCase());
            profile.setVerificationStatus(VerificationStatus.VERIFIED);
            profile.setVerifiedAt(Instant.now().minus(60, ChronoUnit.DAYS));
            owner.setCourtOwnerProfile(profile);

            userRepository.save(owner);
            seedNotificationPreferences(owner);
        }

        // --- Nguoi choi ---
        for (PlayerSeed seed : PLAYERS) {
            User player = newUser(seed.key(), seed.fullName(), seed.email(), seed.phone(), hashedPassword);
            player.getUserRoles().add(new UserRole(player, playerRole));

            PlayerProfile profile = new PlayerProfile(player);
            profile.setGender(seed.gender());
            profile.setDateOfBirth(seed.dateOfBirth());
            profile.setSkillLevel(seed.skillLevel());
            profile.setSkillScore(BigDecimal.valueOf(seed.skillScore()));
            profile.setDominantHand(seed.hand());
            profile.setPlayingStyle(seed.style());
            profile.setPreferredPlayType(seed.playType());
            profile.setBio("Nguoi choi khu vuc " + seed.district() + ", thuong choi buoi toi.");
            player.setPlayerProfile(profile);

            userRepository.save(player);

            seedAvailability(player, seed);
            seedPreferredLocation(player, seed);
            seedNotificationPreferences(player);

            PlayerStatistics statistics = new PlayerStatistics(player);
            statistics.setRatingScore(BigDecimal.valueOf(seed.skillScore()));
            playerStatisticsRepository.save(statistics);
        }

        seedGoogleLogin();
        seedUsedPasswordReset();
        seedUsedEmailVerification();

        log.info("  [users] 1 admin, 1 nhan vien, {} chu san, {} nguoi choi",
                OWNERS.size(), PLAYERS.size());
    }

    private User newUser(String key, String fullName, String email, String phone, String hashedPassword) {
        User user = new User();
        user.setId(SeedIds.of("user:" + key));
        user.setFullName(fullName);
        user.setEmail(email);
        user.setPhone(phone);
        user.setPasswordHash(hashedPassword);
        user.setStatus(UserStatus.ACTIVE);
        user.setEmailVerifiedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        user.setCreatedAt(Instant.now().minus(90, ChronoUnit.DAYS));
        user.setLastLoginAt(Instant.now().minus(2, ChronoUnit.HOURS));
        user.setAvatarUrl("https://i.pravatar.cc/160?u=" + key);
        return user;
    }

    /** Khung gio ranh: toi thu 3, thu 5 va sang cuoi tuan. */
    private void seedAvailability(User player, PlayerSeed seed) {
        record Slot(DayOfWeek day, LocalTime from, LocalTime to) {
        }
        List<Slot> slots = List.of(
                new Slot(DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(21, 0)),
                new Slot(DayOfWeek.THURSDAY, LocalTime.of(18, 0), LocalTime.of(21, 0)),
                new Slot(DayOfWeek.SATURDAY, LocalTime.of(7, 0), LocalTime.of(11, 0)));

        int index = 0;
        for (Slot slot : slots) {
            PlayerAvailabilitySlot entity = new PlayerAvailabilitySlot();
            entity.setId(SeedIds.of("availability:" + seed.key() + ":" + index++));
            entity.setUser(player);
            entity.setDayOfWeek((short) slot.day().getValue());
            entity.setStartTime(slot.from());
            entity.setEndTime(slot.to());
            availabilitySlotRepository.save(entity);
        }
    }

    private void seedPreferredLocation(User player, PlayerSeed seed) {
        PlayerPreferredLocation location = new PlayerPreferredLocation();
        location.setId(SeedIds.of("preferred-location:" + seed.key()));
        location.setUser(player);
        location.setLabel("Gan nha");
        location.setAddress(seed.district() + ", Da Nang");
        location.setLatitude(BigDecimal.valueOf(seed.latitude()));
        location.setLongitude(BigDecimal.valueOf(seed.longitude()));
        location.setRadiusKm(BigDecimal.valueOf(7));
        location.setDefault(true);
        preferredLocationRepository.save(location);
    }

    private void seedNotificationPreferences(User user) {
        for (String type : NotificationType.ALL) {
            NotificationPreference preference = new NotificationPreference(user, type);
            preference.setPushEnabled(NotificationType.BOOKING_CONFIRMED.equals(type));
            notificationPreferenceRepository.save(preference);
        }
    }

    /** Mot nguoi choi dang nhap bang Google, khong co mat khau noi bo (2.1.3). */
    private void seedGoogleLogin() {
        User googleUser = new User();
        googleUser.setId(SeedIds.of("user:player-google"));
        googleUser.setFullName("Ngo Gia Huy");
        googleUser.setEmail("huy.ngo@gmail.com");
        googleUser.setPasswordHash(null);
        googleUser.setStatus(UserStatus.ACTIVE);
        googleUser.setEmailVerifiedAt(Instant.now().minus(10, ChronoUnit.DAYS));
        googleUser.setAvatarUrl("https://i.pravatar.cc/160?u=player-google");
        googleUser.getUserRoles().add(new UserRole(googleUser, requireRole(Role.PLAYER)));

        PlayerProfile profile = new PlayerProfile(googleUser);
        profile.setGender(Gender.MALE);
        profile.setSkillLevel(SkillLevel.INTERMEDIATE);
        profile.setSkillScore(BigDecimal.valueOf(1100));
        profile.setDominantHand(DominantHand.RIGHT);
        profile.setPlayingStyle(PlayingStyle.BALANCED);
        profile.setPreferredPlayType(PlayType.DOUBLE);
        googleUser.setPlayerProfile(profile);

        userRepository.save(googleUser);
        seedNotificationPreferences(googleUser);
        playerStatisticsRepository.save(new PlayerStatistics(googleUser));

        AuthProvider provider = new AuthProvider();
        provider.setId(SeedIds.of("auth-provider:player-google"));
        provider.setUser(googleUser);
        provider.setProviderName(AuthProvider.GOOGLE);
        provider.setProviderUserId("103928475610293847561");
        provider.setProviderEmail(googleUser.getEmail());
        provider.setProviderEmailVerified(true);
        provider.setProviderAvatarUrl(googleUser.getAvatarUrl());
        provider.setLastUsedAt(Instant.now().minus(1, ChronoUnit.DAYS));
        authProviderRepository.save(provider);
    }

    /** Mot yeu cau dat lai mat khau da dung xong, de doi chieu luong 2.1.5. */
    private void seedUsedPasswordReset() {
        User target = userRepository.findById(SeedIds.of("user:player-01")).orElseThrow();
        Instant createdAt = Instant.now().minus(7, ChronoUnit.DAYS);

        PasswordResetRequest request = new PasswordResetRequest();
        request.setId(SeedIds.of("password-reset:player-01"));
        request.setUser(target);
        request.setChannel(ResetChannel.EMAIL);
        request.setDestination(target.getEmail());
        // Luu hash cua OTP, khong bao gio luu ma goc.
        request.setVerificationHash(passwordEncoder.encode("482913"));
        request.setCreatedAt(createdAt);
        request.setExpiresAt(createdAt.plus(15, ChronoUnit.MINUTES));
        request.setVerifiedAt(createdAt.plus(2, ChronoUnit.MINUTES));
        request.setUsedAt(createdAt.plus(3, ChronoUnit.MINUTES));
        request.setAttemptCount((short) 1);
        request.setRequestedIp("113.161.42.17");
        request.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");
        passwordResetRequestRepository.save(request);
    }

    /** Mot ma xac minh email da dung xong, de doi chieu luong dang ky 2.1.1. */
    private void seedUsedEmailVerification() {
        User target = userRepository.findById(SeedIds.of("user:player-01")).orElseThrow();
        Instant createdAt = Instant.now().minus(90, ChronoUnit.DAYS);

        EmailVerificationRequest request = new EmailVerificationRequest();
        request.setId(SeedIds.of("email-verification:player-01"));
        request.setUser(target);
        request.setEmail(target.getEmail());
        // Luu hash cua OTP, khong bao gio luu ma goc.
        request.setVerificationHash(passwordEncoder.encode("305174"));
        request.setCreatedAt(createdAt);
        request.setExpiresAt(createdAt.plus(15, ChronoUnit.MINUTES));
        request.setVerifiedAt(createdAt.plus(3, ChronoUnit.MINUTES));
        request.setUsedAt(createdAt.plus(3, ChronoUnit.MINUTES));
        request.setAttemptCount((short) 0);
        request.setRequestedIp("113.161.42.17");
        request.setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7)");
        emailVerificationRequestRepository.save(request);
    }

    private Role requireRole(String code) {
        return roleRepository.findById(SeedIds.of("role:" + code))
                .orElseThrow(() -> new IllegalStateException("Thieu vai tro '" + code
                        + "'. RoleAndPermissionSeeder phai chay truoc."));
    }
}
