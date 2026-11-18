package com.courtly.domain.review;

import com.courtly.common.enums.ReportStatus;
import com.courtly.common.enums.ReportTargetType;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ReportRepository extends JpaRepository<Report, UUID> {

    /** Doi tuong (trong {@code ids}) dang co bao cao o mot trong cac trang thai cho truoc. */
    @Query("SELECT DISTINCT r.targetId FROM Report r WHERE r.targetType = :type AND r.targetId IN :ids AND r.status IN :statuses")
    List<UUID> findTargetIdsWithStatus(ReportTargetType type, Collection<UUID> ids, Collection<ReportStatus> statuses);

    boolean existsByTargetTypeAndTargetIdAndStatusIn(ReportTargetType type, UUID targetId, Collection<ReportStatus> statuses);

    boolean existsByReporterIdAndTargetTypeAndTargetIdAndStatusIn(UUID reporterId, ReportTargetType type, UUID targetId,
                                                                  Collection<ReportStatus> statuses);

    /** Bao cao cua mot nguoi ve cac doi tuong, moi nhat truoc (2.3.83: chu san xem trang thai bao cao cua minh). */
    @Query("SELECT r FROM Report r WHERE r.reporter.id = :reporterId AND r.targetType = :type AND r.targetId IN :ids ORDER BY r.createdAt DESC")
    List<Report> findByReporterAndTargets(UUID reporterId, ReportTargetType type, Collection<UUID> ids);
}
