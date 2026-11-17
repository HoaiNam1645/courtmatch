package com.courtly.api.admin;

import com.courtly.api.owner.OwnerApiTestBase;
import com.courtly.domain.account.Permission;
import com.courtly.domain.account.PermissionRepository;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RolePermission;
import com.courtly.domain.account.RoleRepository;
import com.courtly.domain.account.User;
import com.courtly.domain.account.UserRoleRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/** Nen chung cho test khu quan tri: vai tro admin day du quyen va ham tao quan tri vien. */
public abstract class AdminApiTestBase extends OwnerApiTestBase {

    protected static final List<String> ADMIN_PERMISSIONS = List.of(
            "user.manage", "user.role.assign", "owner.verify", "venue.approve", "venue.reject", "report.handle",
            "stats.system.view", "finance.fee.config", "finance.commission.view", "finance.commission.manage", "voucher.manage", "matching.view");

    @Autowired protected PermissionRepository permissionRepository;
    @Autowired protected UserRoleRepository userRoleRepository;

    /** Du bon vai tro; vai tro admin duoc cap cac quyen quan tri can cho test (rollback cuoi moi test). */
    protected void ensureAdminSetup() {
        ensureRoles();
        ensureRole(Role.ADMIN, "Quan tri vien");
        ensureRole(Role.STAFF, "Nhan vien san");
        Role admin = roleRepository.findByCode(Role.ADMIN).orElseThrow();
        for (String code : ADMIN_PERMISSIONS) {
            boolean has = admin.getRolePermissions().stream().anyMatch(item -> item.getPermission().getCode().equals(code));
            if (!has) {
                Permission permission = permissionRepository.findAll().stream()
                        .filter(item -> item.getCode().equals(code)).findFirst().orElseGet(() -> {
                            Permission created = new Permission(code, code, null);
                            created.setId(UUID.randomUUID());
                            return permissionRepository.saveAndFlush(created);
                        });
                admin.getRolePermissions().add(new RolePermission(admin, permission));
            }
        }
        roleRepository.saveAndFlush(admin);
    }

    /** He thong chi co mot chu san: go vai tro owner cua moi tai khoan khac {@code keep} (rollback cuoi test). */
    protected void keepOnlyOwner(User keep) {
        userRepository.findAll().stream()
                .filter(user -> keep == null || !user.getId().equals(keep.getId()))
                .forEach(user -> user.getUserRoles().removeIf(userRole -> Role.OWNER.equals(userRole.getRole().getCode())));
        userRepository.flush();
    }

    protected User persistAdmin(String prefix) {
        return persistUser(prefix, Role.ADMIN);
    }
}
