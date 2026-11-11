package com.courtly.domain.matching;

import com.courtly.common.enums.ConnectionStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PlayerConnectionRepository extends JpaRepository<PlayerConnection, UUID> {

    boolean existsByUserIdAndPartnerIdAndStatus(
            UUID userId, UUID partnerId, ConnectionStatus status);

    @Query(value = """
            select connection from PlayerConnection connection
            join fetch connection.partner partner
            left join fetch partner.playerProfile
            where connection.user.id = :userId
              and connection.status = :status
            order by connection.createdAt desc, connection.id desc
            """,
            countQuery = """
                    select count(connection) from PlayerConnection connection
                    where connection.user.id = :userId
                      and connection.status = :status
                    """)
    Page<PlayerConnection> findByUserAndStatus(
            @Param("userId") UUID userId,
            @Param("status") ConnectionStatus status,
            Pageable pageable);
}
