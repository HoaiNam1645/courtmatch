package com.courtly.domain.matching;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** Moi lan chia doi cua mot nguoi (dai dien bang phuong an khuyen nghi), moi nhat truoc. */
    @Query(value = """
            select suggestion from DoubleTeamSuggestion suggestion
            where suggestion.requestedBy.id = :userId and suggestion.recommended = true
            order by suggestion.createdAt desc, suggestion.batchId
            """,
            countQuery = """
            select count(suggestion) from DoubleTeamSuggestion suggestion
            where suggestion.requestedBy.id = :userId and suggestion.recommended = true
            """)
    Page<DoubleTeamSuggestion> findRecommendedByUser(@Param("userId") UUID userId, Pageable pageable);
}
