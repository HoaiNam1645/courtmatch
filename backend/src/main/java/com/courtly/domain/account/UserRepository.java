package com.courtly.domain.account;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    /** Email luu nguyen ban nhung so sanh khong phan biet hoa thuong, khop unique index lower(email). */
    @Query("SELECT u FROM User u WHERE lower(u.email) = lower(:email)")
    Optional<User> findByEmailIgnoreCase(@Param("email") String email);

    Optional<User> findByPhone(String phone);

    @Query("SELECT count(u) > 0 FROM User u WHERE lower(u.email) = lower(:email)")
    boolean existsByEmailIgnoreCase(@Param("email") String email);

    boolean existsByPhone(String phone);

    @Query("""
            SELECT count(u) > 0 FROM User u
            JOIN u.userRoles ur
            WHERE u.id = :userId AND ur.role.code = 'player'
            """)
    boolean hasPlayerRole(@Param("userId") UUID userId);

    @Query(value = """
            SELECT DISTINCT u FROM User u
            JOIN u.userRoles ur
            WHERE u.id <> :currentUserId
              AND u.status = com.courtly.common.enums.UserStatus.ACTIVE
              AND ur.role.code = 'player'
              AND (u.email IS NULL OR lower(u.email) NOT LIKE '%@bwf.example')
              AND (lower(function('courtly_unaccent', u.fullName))
                       LIKE lower(function('courtly_unaccent', :keyword))
                   OR lower(coalesce(u.email, '')) LIKE lower(:keyword))
            ORDER BY u.fullName ASC
            """,
            countQuery = """
                    SELECT count(DISTINCT u) FROM User u
                    JOIN u.userRoles ur
                    WHERE u.id <> :currentUserId
                      AND u.status = com.courtly.common.enums.UserStatus.ACTIVE
                      AND ur.role.code = 'player'
                      AND (u.email IS NULL OR lower(u.email) NOT LIKE '%@bwf.example')
                      AND (lower(function('courtly_unaccent', u.fullName))
                               LIKE lower(function('courtly_unaccent', :keyword))
                           OR lower(coalesce(u.email, '')) LIKE lower(:keyword))
                    """)
    Page<User> searchActivePlayers(@Param("currentUserId") UUID currentUserId,
                                   @Param("keyword") String keyword,
                                   Pageable pageable);

    @Query("SELECT u.status FROM User u WHERE u.id = :id")
    Optional<com.courtly.common.enums.UserStatus> findStatusById(@Param("id") UUID id);

    /** Danh sach tai khoan cho quan tri vien (2.3.28); keyword la mau LIKE (da them %). */
    @Query(value = """
            SELECT u FROM User u
            WHERE u.status IN :statuses
              AND (:anyRole = true OR EXISTS (SELECT 1 FROM UserRole ur WHERE ur.user = u AND ur.role.code IN :roles))
              AND (lower(function('courtly_unaccent', u.fullName)) LIKE lower(function('courtly_unaccent', :keyword))
                   OR lower(coalesce(u.email, '')) LIKE lower(:keyword)
                   OR coalesce(u.phone, '') LIKE :keyword)
            ORDER BY u.createdAt DESC, u.id DESC
            """,
            countQuery = """
                    SELECT count(u) FROM User u
                    WHERE u.status IN :statuses
                      AND (:anyRole = true OR EXISTS (SELECT 1 FROM UserRole ur WHERE ur.user = u AND ur.role.code IN :roles))
                      AND (lower(function('courtly_unaccent', u.fullName)) LIKE lower(function('courtly_unaccent', :keyword))
                           OR lower(coalesce(u.email, '')) LIKE lower(:keyword)
                           OR coalesce(u.phone, '') LIKE :keyword)
                    """)
    Page<User> searchForAdmin(@Param("statuses") java.util.Collection<com.courtly.common.enums.UserStatus> statuses,
                              @Param("anyRole") boolean anyRole,
                              @Param("roles") java.util.Collection<String> roles,
                              @Param("keyword") String keyword,
                              Pageable pageable);

    @Query("SELECT u.status AS status, count(u) AS total FROM User u GROUP BY u.status")
    java.util.List<StatusCount> countByStatus();

    interface StatusCount {
        com.courtly.common.enums.UserStatus getStatus();

        long getTotal();
    }
}
