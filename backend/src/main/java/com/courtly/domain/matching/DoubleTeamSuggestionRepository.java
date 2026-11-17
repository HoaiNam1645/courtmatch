package com.courtly.domain.matching;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DoubleTeamSuggestionRepository extends JpaRepository<DoubleTeamSuggestion, UUID> {

    /** Cac phuong an cua mot batch do dung nguoi yeu cau tao, tot nhat truoc. */
    @Query("""
            select suggestion from DoubleTeamSuggestion suggestion
            join fetch suggestion.model
            where suggestion.batchId = :batchId and suggestion.requestedBy.id = :userId
            order by suggestion.rankNo
            """)
    List<DoubleTeamSuggestion> findBatch(@Param("batchId") UUID batchId, @Param("userId") UUID userId);
}
