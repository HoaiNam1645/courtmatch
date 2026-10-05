package com.courtly.seed;

import java.util.List;

/**
 * Cau truc cua hai file {@code classpath:seed/bwf-players.json} va
 * {@code classpath:seed/bwf-matches.json}.
 *
 * <p>Hai file do sinh bang {@code data/bwf-to-seed.py} tu bo du lieu BWF that
 * (105.147 tran, 2007-2026). Moi con so - ke ca diem Elo va thong ke - deu duoc
 * tinh san o script, seeder chi chen vao database.
 */
public final class BwfSeedData {

    private BwfSeedData() {
    }

    public record Players(List<Player> players) {
    }

    public record Matches(List<Match> matches, List<RatingChange> ratingHistory) {
    }

    /**
     * @param key               khoa on dinh, dua vao {@link SeedIds} de ra UUID
     * @param lastPlayedDaysAgo so ngay tinh nguoc tu luc chay seeder
     */
    public record Player(String key,
                         String fullName,
                         String email,
                         String phone,
                         String gender,
                         String skillLevel,
                         String preferredPlayType,
                         Statistics statistics) {
    }

    public record Statistics(int totalMatches,
                             int totalWins,
                             int totalLosses,
                             String winRate,
                             String ratingScore,
                             String avgPointsScored,
                             String avgPointsConceded,
                             int lastPlayedDaysAgo) {
    }

    /**
     * @param games danh sach ty so tung set, moi phan tu la {diem doi 1, diem doi 2}
     * @param note  ten giai va vong dau that, thay cho venue vi tran khong dien ra
     *              o san nao trong he thong
     */
    public record Match(String key,
                        String type,
                        int daysAgo,
                        List<String> team1,
                        List<String> team2,
                        List<List<Integer>> games,
                        int winningTeam,
                        String note) {
    }

    public record RatingChange(String playerKey,
                               String matchKey,
                               String oldRating,
                               String newRating,
                               String changeAmount,
                               int daysAgo) {
    }
}
