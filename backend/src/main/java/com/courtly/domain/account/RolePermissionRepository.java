package com.courtly.domain.account;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.UUID;
import org.springframework.stereotype.Repository;

@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, RolePermissionId> {

    /** Nguoi dung co quyen chi tiet nay qua mot trong cac vai tro cua minh (2.3.30). */
    @Query("""
            select count(rp) > 0 from RolePermission rp
            where rp.permission.code = :permissionCode
              and rp.role.id in (select ur.role.id from UserRole ur where ur.user.id = :userId)
            """)
    boolean userHasPermission(@Param("userId") UUID userId, @Param("permissionCode") String permissionCode);
}
