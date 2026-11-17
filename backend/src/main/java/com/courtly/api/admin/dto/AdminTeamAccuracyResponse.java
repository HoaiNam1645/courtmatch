package com.courtly.api.admin.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * 2.3.71 - do do chinh xac ghep doi: doi chieu phuong an chia doi da de xuat voi tran that cua cung 4 nguoi
 * (cung cach chia doi, da xac nhan ket qua, trong {@code reconcileDays} ngay sau de xuat).
 * Chi so khong tinh duoc (mau so 0) la null; {@code sufficient} = false khi chua du mau de ket luan.
 */
public record AdminTeamAccuracyResponse(LocalDate from, LocalDate to, String source, int minSample,
                                        int reconcileDays, long batches, long options,
                                        Metrics overall, Metrics recommended, Metrics others,
                                        Double recommendedChosenRate, List<Sample> samples) {

    /**
     * @param reconciled     phuong an da doi chieu duoc voi tran that
     * @param evaluated      phuong an co du doan ro rang (xac suat doi 1 khac 50%)
     * @param correct        so du doan dung doi thang
     * @param brier          sai so binh phuong trung binh cua xac suat thang (0 la hoan hao, 0.25 la doan ngau nhien)
     * @param avgBalanceGap  do chenh suc manh trung binh giua hai doi o cac tran doi chieu duoc
     * @param avgPointMargin chenh lech diem thuc te trung binh giua hai doi (tong cac vang)
     */
    public record Metrics(long reconciled, long evaluated, long correct, Double accuracy, Double brier,
                          Double avgBalanceGap, Double avgPointMargin, boolean sufficient) {
    }

    /** Mot lan doi chieu, de quan tri vien tu kiem tra. */
    public record Sample(UUID batchId, UUID matchId, int rank, boolean recommended, Double team1WinProbability,
                         int winningTeam, int predictedWinningTeam, boolean correct, Double balanceScore,
                         Instant suggestedAt, Instant playedAt) {
    }
}
