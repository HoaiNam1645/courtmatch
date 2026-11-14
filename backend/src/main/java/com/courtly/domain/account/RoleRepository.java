package com.courtly.domain.account;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByCode(String code);

    /** So nguoi dung theo tung vai tro (2.3.29). */
    @Query("select ur.role.code as code, count(ur) as total from UserRole ur group by ur.role.code")
    List<RoleUserCount> countUsersByRole();

    /** Co nguoi dung nao khac dang giu vai tro nay khong (2.3.29: chi mot chu san). */
    @Query("select count(ur) > 0 from UserRole ur where ur.role.code = :code and ur.user.id <> :userId")
    boolean existsOtherHolder(@org.springframework.data.repository.query.Param("code") String code,
                              @org.springframework.data.repository.query.Param("userId") java.util.UUID userId);

    interface RoleUserCount {
        String getCode();

        long getTotal();
    }
}
