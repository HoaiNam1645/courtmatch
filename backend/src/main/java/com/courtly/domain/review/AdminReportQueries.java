package com.courtly.domain.review;

import com.courtly.common.enums.ReportStatus;
import com.courtly.common.enums.ReportTargetType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Danh sach bao cao cho quan tri vien (2.3.36, 2.3.37); chi doc. */
public interface AdminReportQueries extends Repository<Report, UUID> {

    /**
     * Bao cao chua xu ly len dau (pending, roi reviewing), con lai moi nhat truoc.
     * keyword la mau LIKE (da them %) doi chieu voi nguoi bao cao va tai khoan duoc bao cao.
     */
    @Query(value = """
            SELECT r FROM Report r JOIN FETCH r.reporter rep
            LEFT JOIN User t ON t.id = r.targetId
            WHERE r.targetType = :targetType AND r.status IN :statuses
              AND (:anyReason = true OR r.reason = :reason)
              AND (lower(function('courtly_unaccent', rep.fullName)) LIKE lower(function('courtly_unaccent', :keyword))
                   OR lower(rep.email) LIKE lower(:keyword)
                   OR lower(function('courtly_unaccent', coalesce(t.fullName, ''))) LIKE lower(function('courtly_unaccent', :keyword))
                   OR lower(coalesce(t.email, '')) LIKE lower(:keyword))
            ORDER BY CASE WHEN r.status = com.courtly.common.enums.ReportStatus.PENDING THEN 0
                          WHEN r.status = com.courtly.common.enums.ReportStatus.REVIEWING THEN 1 ELSE 2 END,
                     r.createdAt DESC, r.id DESC
            """,
            countQuery = """
                    SELECT count(r) FROM Report r JOIN r.reporter rep
                    LEFT JOIN User t ON t.id = r.targetId
                    WHERE r.targetType = :targetType AND r.status IN :statuses
                      AND (:anyReason = true OR r.reason = :reason)
                      AND (lower(function('courtly_unaccent', rep.fullName)) LIKE lower(function('courtly_unaccent', :keyword))
                           OR lower(rep.email) LIKE lower(:keyword)
                           OR lower(function('courtly_unaccent', coalesce(t.fullName, ''))) LIKE lower(function('courtly_unaccent', :keyword))
                           OR lower(coalesce(t.email, '')) LIKE lower(:keyword))
                    """)
    Page<Report> search(@Param("targetType") ReportTargetType targetType,
                        @Param("statuses") Collection<ReportStatus> statuses,
                        @Param("anyReason") boolean anyReason,
                        @Param("reason") String reason,
                        @Param("keyword") String keyword,
                        Pageable pageable);

    /**
     * Bao cao ve danh gia doi tac (2.3.37): doi chieu voi nguoi bao cao, nguoi danh gia, nguoi duoc danh gia
     * va noi dung danh gia. anyReviewStatus = true thi khong loc theo trang thai cua danh gia.
     */
    @Query(value = """
            SELECT r FROM Report r JOIN FETCH r.reporter rep
            LEFT JOIN PartnerReview pr ON pr.id = r.targetId
            LEFT JOIN pr.reviewer rv
            LEFT JOIN pr.reviewedUser rd
            WHERE r.targetType = com.courtly.common.enums.ReportTargetType.PARTNER_REVIEW AND r.status IN :statuses
              AND (:anyReason = true OR r.reason = :reason)
              AND (:anyReviewStatus = true OR pr.status = :reviewStatus)
              AND (lower(function('courtly_unaccent', rep.fullName)) LIKE lower(function('courtly_unaccent', :keyword))
                   OR lower(function('courtly_unaccent', coalesce(rv.fullName, ''))) LIKE lower(function('courtly_unaccent', :keyword))
                   OR lower(function('courtly_unaccent', coalesce(rd.fullName, ''))) LIKE lower(function('courtly_unaccent', :keyword))
                   OR lower(function('courtly_unaccent', coalesce(pr.comment, ''))) LIKE lower(function('courtly_unaccent', :keyword)))
            ORDER BY CASE WHEN r.status = com.courtly.common.enums.ReportStatus.PENDING THEN 0
                          WHEN r.status = com.courtly.common.enums.ReportStatus.REVIEWING THEN 1 ELSE 2 END,
                     r.createdAt DESC, r.id DESC
            """,
            countQuery = """
                    SELECT count(r) FROM Report r JOIN r.reporter rep
                    LEFT JOIN PartnerReview pr ON pr.id = r.targetId
                    LEFT JOIN pr.reviewer rv
                    LEFT JOIN pr.reviewedUser rd
                    WHERE r.targetType = com.courtly.common.enums.ReportTargetType.PARTNER_REVIEW AND r.status IN :statuses
                      AND (:anyReason = true OR r.reason = :reason)
                      AND (:anyReviewStatus = true OR pr.status = :reviewStatus)
                      AND (lower(function('courtly_unaccent', rep.fullName)) LIKE lower(function('courtly_unaccent', :keyword))
                           OR lower(function('courtly_unaccent', coalesce(rv.fullName, ''))) LIKE lower(function('courtly_unaccent', :keyword))
                           OR lower(function('courtly_unaccent', coalesce(rd.fullName, ''))) LIKE lower(function('courtly_unaccent', :keyword))
                           OR lower(function('courtly_unaccent', coalesce(pr.comment, ''))) LIKE lower(function('courtly_unaccent', :keyword)))
                    """)
    Page<Report> searchPartnerReviewReports(@Param("statuses") Collection<ReportStatus> statuses,
                                            @Param("anyReason") boolean anyReason,
                                            @Param("reason") String reason,
                                            @Param("anyReviewStatus") boolean anyReviewStatus,
                                            @Param("reviewStatus") com.courtly.common.enums.ReviewStatus reviewStatus,
                                            @Param("keyword") String keyword,
                                            Pageable pageable);

    @Query("SELECT r.status AS status, count(r) AS total FROM Report r WHERE r.targetType = :targetType GROUP BY r.status")
    List<StatusCount> countByStatus(@Param("targetType") ReportTargetType targetType);

    @Query("SELECT DISTINCT r.reason FROM Report r WHERE r.targetType = :targetType ORDER BY r.reason")
    List<String> reasons(@Param("targetType") ReportTargetType targetType);

    @Query("SELECT r FROM Report r JOIN FETCH r.reporter LEFT JOIN FETCH r.handledBy WHERE r.id = :id")
    java.util.Optional<Report> findDetailById(@Param("id") UUID id);

    /** Cac bao cao khac cung nham vao mot doi tuong, moi nhat truoc. */
    @Query("""
            SELECT r FROM Report r JOIN FETCH r.reporter
            WHERE r.targetType = :targetType AND r.targetId = :targetId AND r.id <> :excludeId
            ORDER BY r.createdAt DESC, r.id DESC
            """)
    List<Report> findOthersAboutTarget(@Param("targetType") ReportTargetType targetType,
                                       @Param("targetId") UUID targetId,
                                       @Param("excludeId") UUID excludeId,
                                       Pageable pageable);

    @Query("SELECT count(r) FROM Report r WHERE r.targetType = :targetType AND r.targetId = :targetId")
    long countAboutTarget(@Param("targetType") ReportTargetType targetType, @Param("targetId") UUID targetId);

    interface StatusCount {
        ReportStatus getStatus();

        long getTotal();
    }
}
