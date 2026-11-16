package com.courtly.domain.notification;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    /** Thong bao cua mot nguoi dung, moi nhat truoc; loc theo loai va chi chua doc (2.3.61). */
    @Query(value = """
            select n from Notification n
            where n.user.id = :userId
              and (:type is null or n.type = :type)
              and (:unreadOnly = false or n.readAt is null)
            order by n.createdAt desc, n.id desc
            """,
            countQuery = """
                    select count(n) from Notification n
                    where n.user.id = :userId
                      and (:type is null or n.type = :type)
                      and (:unreadOnly = false or n.readAt is null)
                    """)
    Page<Notification> findForUser(@Param("userId") UUID userId, @Param("type") String type,
                                   @Param("unreadOnly") boolean unreadOnly, Pageable pageable);

    long countByUserIdAndReadAtIsNull(UUID userId);

    Optional<Notification> findByIdAndUserId(UUID id, UUID userId);

    /** Danh dau tat ca (hoac mot loai) la da doc; tra ve so thong bao vua doi. */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Notification n set n.readAt = :now
            where n.user.id = :userId and n.readAt is null and (:type is null or n.type = :type)
            """)
    int markAllRead(@Param("userId") UUID userId, @Param("type") String type, @Param("now") java.time.Instant now);
}
