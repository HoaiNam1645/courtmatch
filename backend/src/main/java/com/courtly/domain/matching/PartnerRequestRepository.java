package com.courtly.domain.matching;

import com.courtly.common.enums.PartnerRequestStatus;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PartnerRequestRepository extends JpaRepository<PartnerRequest, UUID> {

    @Query("""
            select count(request) > 0 from PartnerRequest request
            where request.status = :status
              and ((request.requester.id = :firstUserId and request.receiver.id = :secondUserId)
                or (request.requester.id = :secondUserId and request.receiver.id = :firstUserId))
            """)
    boolean existsBetweenUsersWithStatus(
            @Param("firstUserId") UUID firstUserId,
            @Param("secondUserId") UUID secondUserId,
            @Param("status") PartnerRequestStatus status);

    @Query("""
            select request from PartnerRequest request
            join fetch request.requester
            join fetch request.receiver
            where request.id = :requestId
            """)
    Optional<PartnerRequest> findDetailById(@Param("requestId") UUID requestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request from PartnerRequest request
            join fetch request.requester
            join fetch request.receiver
            where request.id = :requestId
            """)
    Optional<PartnerRequest> findDetailByIdForUpdate(@Param("requestId") UUID requestId);

    @Query(value = """
            select request from PartnerRequest request
            join fetch request.requester
            join fetch request.receiver
            where request.receiver.id = :receiverId
              and (:status is null or request.status = :status)
            order by request.createdAt desc, request.id desc
            """,
            countQuery = """
                    select count(request) from PartnerRequest request
                    where request.receiver.id = :receiverId
                      and (:status is null or request.status = :status)
                    """)
    Page<PartnerRequest> findReceived(
            @Param("receiverId") UUID receiverId,
            @Param("status") PartnerRequestStatus status,
            Pageable pageable);

    @Query(value = """
            select request from PartnerRequest request
            join fetch request.requester
            join fetch request.receiver
            where request.requester.id = :requesterId
              and (:status is null or request.status = :status)
            order by request.createdAt desc, request.id desc
            """,
            countQuery = """
                    select count(request) from PartnerRequest request
                    where request.requester.id = :requesterId
                      and (:status is null or request.status = :status)
                    """)
    Page<PartnerRequest> findSent(
            @Param("requesterId") UUID requesterId,
            @Param("status") PartnerRequestStatus status,
            Pageable pageable);
}
