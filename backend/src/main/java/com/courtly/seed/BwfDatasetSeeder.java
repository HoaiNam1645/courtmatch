package com.courtly.seed;

import com.courtly.common.enums.Gender;
import com.courtly.common.enums.JoinedStatus;
import com.courtly.common.enums.MatchStatus;
import com.courtly.common.enums.MatchType;
import com.courtly.common.enums.PlayType;
import com.courtly.common.enums.SkillLevel;
import com.courtly.common.enums.UserStatus;
import com.courtly.domain.account.PlayerProfile;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRepository;
import com.courtly.domain.account.UserRole;
import com.courtly.domain.match.Match;
import com.courtly.domain.match.MatchGame;
import com.courtly.domain.match.MatchPlayer;
import com.courtly.domain.match.MatchRepository;
import com.courtly.domain.match.MatchResult;
import com.courtly.domain.match.PlayerRatingHistory;
import com.courtly.domain.match.PlayerRatingHistoryRepository;
import com.courtly.domain.match.PlayerStatistics;
import com.courtly.domain.match.PlayerStatisticsRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Buoc 11: nap bo du lieu tran dau that cua BWF.
 *
 * <p>Vi sao can: {@link MatchSeeder} chi co 6 tran va 8 nguoi choi - du de xem giao dien
 * nhung khong du de chay thuat toan. K-Means tren 8 diem du lieu khong co y nghia thong ke.
 * Bo du lieu nay cho 188 van dong vien va {@value #SO_TRAN_DU_KIEN} tran that, moi nguoi
 * trung vi 40 tran, dat nguong toi thieu de phan cum va ghep cap o Sprint 2.
 *
 * <p>Nguon: 105.147 tran BWF 2007-2026 trong {@code data/bwf-match-data}. Script
 * {@code data/bwf-to-seed.py} loc vong dau chinh tu 2024, chon 188 nguoi nhieu tran nhat,
 * kiem tra ty so dung luat cau long, roi tinh san diem Elo va thong ke.
 *
 * <p>Seeder nay KHONG chay thuat toan nao - moi con so deu doc tu JSON, dung theo quy tac
 * o {@code QUY-TAC-LAM-CHUC-NANG.md}. Cong thuc Elo mo ta o
 * {@code thiet-ke-sprint-2-du-lieu-va-hieu-suat.md} muc 2.2.6; khi cai dat 2.2.6 bang Java
 * thi ket qua phai trung voi du lieu o day.
 *
 * <p>Han che da biet: day la van dong vien chuyen nghiep, khong co nguoi moi choi. Dung de
 * kiem chung duong ong xu ly va thuat toan xep hang; muon danh gia chat luong phan cum thi
 * van can bo du lieu mo phong co nhan biet truoc.
 */
@Slf4j
@Component
@Order(11)
@RequiredArgsConstructor
public class BwfDatasetSeeder implements Seeder {

    private static final String FILE_NGUOI_CHOI = "seed/bwf-players.json";
    private static final String FILE_TRAN_DAU = "seed/bwf-matches.json";
    private static final int SO_TRAN_DU_KIEN = 2555;

    /** Mat khau giong moi tai khoan mau khac, de dang nhap thu. */
    private static final String MAT_KHAU = UserSeeder.DEFAULT_PASSWORD;

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final MatchRepository matchRepository;
    private final PlayerStatisticsRepository statisticsRepository;
    private final PlayerRatingHistoryRepository ratingHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjectMapper objectMapper;

    @Override
    public String name() {
        return "bo du lieu BWF (188 van dong vien, " + SO_TRAN_DU_KIEN + " tran)";
    }

    @Override
    @Transactional
    public void seed() {
        BwfSeedData.Players duLieuNguoi = doc(FILE_NGUOI_CHOI, BwfSeedData.Players.class);
        BwfSeedData.Matches duLieuTran = doc(FILE_TRAN_DAU, BwfSeedData.Matches.class);

        UUID idNguoiDauTien = SeedIds.of(duLieuNguoi.players().get(0).key());
        if (userRepository.existsById(idNguoiDauTien)) {
            log.info("  [bwf] da co du lieu, bo qua");
            return;
        }

        Instant bayGio = Instant.now();
        Map<String, User> theoKhoa = taoNguoiChoi(duLieuNguoi.players(), bayGio);
        Map<String, Match> tranTheoKhoa = taoTranDau(duLieuTran.matches(), theoKhoa, bayGio);
        taoLichSuDiem(duLieuTran.ratingHistory(), theoKhoa, tranTheoKhoa, bayGio);

        log.info("  [bwf] {} van dong vien, {} tran, {} dong lich su diem",
                theoKhoa.size(), tranTheoKhoa.size(), duLieuTran.ratingHistory().size());
    }

    // --- Nguoi choi ---------------------------------------------------------

    private Map<String, User> taoNguoiChoi(List<BwfSeedData.Player> danhSach, Instant bayGio) {
        Role vaiTro = roleRepository.findById(SeedIds.of("role:" + Role.PLAYER))
                .orElseThrow(() -> new IllegalStateException(
                        "Thieu vai tro '" + Role.PLAYER + "'. RoleAndPermissionSeeder phai chay truoc."));

        // Bam mat khau MOT lan roi dung chung: BCrypt ton khoang 50ms moi lan,
        // 188 lan se lam seeder cham them gan 10 giay ma khong duoc gi.
        String matKhauDaBam = passwordEncoder.encode(MAT_KHAU);

        Map<String, User> theoKhoa = new HashMap<>();
        for (BwfSeedData.Player p : danhSach) {
            User user = new User();
            user.setId(SeedIds.of(p.key()));
            user.setFullName(p.fullName());
            user.setEmail(p.email());
            user.setPhone(p.phone());
            user.setPasswordHash(matKhauDaBam);
            user.setStatus(UserStatus.ACTIVE);
            user.setEmailVerifiedAt(bayGio.minus(365, ChronoUnit.DAYS));
            user.setCreatedAt(bayGio.minus(730, ChronoUnit.DAYS));
            user.getUserRoles().add(new UserRole(user, vaiTro));

            PlayerProfile profile = new PlayerProfile(user);
            profile.setGender(Gender.from(p.gender()));
            profile.setSkillLevel(SkillLevel.from(p.skillLevel()));
            profile.setPreferredPlayType(PlayType.from(p.preferredPlayType()));
            // playing_style va date_of_birth de trong: bo du lieu BWF khong co hai thong
            // tin nay, va dien bua vao se lam sai ket qua phan cum.
            user.setPlayerProfile(profile);

            userRepository.save(user);
            statisticsRepository.save(thongKe(user, p.statistics(), bayGio));
            theoKhoa.put(p.key(), user);
        }
        return theoKhoa;
    }

    private PlayerStatistics thongKe(User user, BwfSeedData.Statistics s, Instant bayGio) {
        PlayerStatistics ps = new PlayerStatistics(user);
        ps.setTotalMatches(s.totalMatches());
        ps.setTotalWins(s.totalWins());
        ps.setTotalLosses(s.totalLosses());
        ps.setWinRate(new BigDecimal(s.winRate()));
        ps.setRatingScore(new BigDecimal(s.ratingScore()));
        ps.setAvgPointsScored(new BigDecimal(s.avgPointsScored()));
        ps.setAvgPointsConceded(new BigDecimal(s.avgPointsConceded()));
        ps.setLastPlayedAt(bayGio.minus(s.lastPlayedDaysAgo(), ChronoUnit.DAYS));
        return ps;
    }

    // --- Tran dau -----------------------------------------------------------

    private Map<String, Match> taoTranDau(List<BwfSeedData.Match> danhSach,
                                          Map<String, User> theoKhoa, Instant bayGio) {
        Map<String, Match> tranTheoKhoa = new HashMap<>();
        for (BwfSeedData.Match m : danhSach) {
            Match match = new Match();
            match.setId(SeedIds.of(m.key()));
            match.setMatchType(MatchType.from(m.type()));
            match.setStatus(MatchStatus.COMPLETED);
            match.setScheduledAt(bayGio.minus(m.daysAgo(), ChronoUnit.DAYS));
            match.setCreatedAt(bayGio.minus(m.daysAgo(), ChronoUnit.DAYS));
            // venue de trong: tran dien ra o giai dau that, khong phai o san nao trong
            // he thong. Ten giai luu o note cua ket qua.

            themNguoiChoi(match, m.team1(), (short) 1, theoKhoa);
            themNguoiChoi(match, m.team2(), (short) 2, theoKhoa);

            short soSet = 0;
            for (List<Integer> ty : m.games()) {
                MatchGame game = new MatchGame();
                game.setId(SeedIds.of(m.key() + ":game-" + (soSet + 1)));
                game.setMatch(match);
                game.setGameNo(++soSet);
                game.setTeam1Score(ty.get(0));
                game.setTeam2Score(ty.get(1));
                match.getGames().add(game);
            }

            MatchResult result = new MatchResult(match, (short) m.winningTeam());
            result.setConfirmedAt(bayGio.minus(m.daysAgo(), ChronoUnit.DAYS));
            result.setNote(m.note());
            match.setResult(result);

            matchRepository.save(match);
            tranTheoKhoa.put(m.key(), match);
        }
        return tranTheoKhoa;
    }

    private void themNguoiChoi(Match match, List<String> khoaNguoiChoi, short doi,
                               Map<String, User> theoKhoa) {
        short viTri = 0;
        for (String khoa : khoaNguoiChoi) {
            User user = theoKhoa.get(khoa);
            if (user == null) {
                throw new IllegalStateException("Tran tham chieu nguoi choi khong co: " + khoa);
            }
            MatchPlayer mp = new MatchPlayer();
            mp.setId(SeedIds.of(match.getId() + ":" + khoa));
            mp.setMatch(match);
            mp.setUser(user);
            mp.setTeamNo(doi);
            mp.setPositionNo(++viTri);
            mp.setJoinedStatus(JoinedStatus.JOINED);
            match.getPlayers().add(mp);
        }
    }

    // --- Lich su diem -------------------------------------------------------

    private void taoLichSuDiem(List<BwfSeedData.RatingChange> danhSach,
                               Map<String, User> theoKhoa,
                               Map<String, Match> tranTheoKhoa,
                               Instant bayGio) {
        int thuTu = 0;
        for (BwfSeedData.RatingChange r : danhSach) {
            PlayerRatingHistory dong = new PlayerRatingHistory();
            dong.setId(SeedIds.of("bwf-rating:" + (++thuTu)));
            dong.setUser(theoKhoa.get(r.playerKey()));
            dong.setMatch(tranTheoKhoa.get(r.matchKey()));
            dong.setOldRating(new BigDecimal(r.oldRating()));
            dong.setNewRating(new BigDecimal(r.newRating()));
            dong.setChangeAmount(new BigDecimal(r.changeAmount()));
            dong.setReason("match_result");
            dong.setCreatedAt(bayGio.minus(r.daysAgo(), ChronoUnit.DAYS));
            ratingHistoryRepository.save(dong);
        }
    }

    // --- Doc file -----------------------------------------------------------

    private <T> T doc(String duongDan, Class<T> kieu) {
        try (InputStream input = new ClassPathResource(duongDan).getInputStream()) {
            return objectMapper.readValue(input, kieu);
        } catch (IOException e) {
            throw new UncheckedIOException("Khong doc duoc " + duongDan, e);
        }
    }
}
