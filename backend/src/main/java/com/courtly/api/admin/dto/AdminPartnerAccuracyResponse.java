package com.courtly.api.admin.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * 2.3.70 - do do chinh xac ghep cap bang ket qua that cua cac goi y. Ty le la phan tram (1 chu so thap phan),
 * null khi mau so bang 0; {@code sufficient} = false khi chua du mau de ket luan.
 */
public record AdminPartnerAccuracyResponse(LocalDate from, LocalDate to, String source, int minSample,
                                           int attributionDays, Metrics overall,
                                           List<Group> byRank, List<Group> byScore) {

    /**
     * @param suggestions    so goi y trong khoang
     * @param requested      goi y da co yeu cau ghep cap gui di (trong cua so quy dinh)
     * @param answered       yeu cau da duoc tra loi (accepted + rejected)
     * @param playedTogether yeu cau accepted ma hai nguoi co tran chung da hoan thanh sau do
     */
    public record Metrics(long suggestions, long requested, Double requestRate, long accepted, long rejected,
                          long expired, long cancelled, long pending, long answered, Double acceptanceRate,
                          long playedTogether, Double playedRate, boolean sufficient) {
    }

    public record Group(String key, String label, Metrics metrics) {
    }
}
