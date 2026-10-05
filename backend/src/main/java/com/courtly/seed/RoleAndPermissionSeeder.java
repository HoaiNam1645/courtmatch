package com.courtly.seed;

import com.courtly.domain.account.Permission;
import com.courtly.domain.account.PermissionRepository;
import com.courtly.domain.account.Role;
import com.courtly.domain.account.RolePermission;
import com.courtly.domain.account.RoleRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Buoc 1: roles, permissions, role_permissions. */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class RoleAndPermissionSeeder implements Seeder {

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    /** code -> ten hien thi. */
    private static final Map<String, String> PERMISSIONS = new LinkedHashMap<>();

    static {
        PERMISSIONS.put("user.manage", "Quan ly tai khoan nguoi dung");
        PERMISSIONS.put("user.role.assign", "Phan quyen vai tro nguoi dung");
        PERMISSIONS.put("owner.verify", "Xac minh tai khoan chu san");
        PERMISSIONS.put("venue.create", "Them san cau long moi");
        PERMISSIONS.put("venue.update", "Cap nhat thong tin san");
        PERMISSIONS.put("venue.approve", "Phe duyet san cau long");
        PERMISSIONS.put("venue.reject", "Tu choi san cau long");
        PERMISSIONS.put("court.manage", "Quan ly san con va bang gia");
        PERMISSIONS.put("court.block", "Khoa va mo khoa khung gio san");
        PERMISSIONS.put("booking.create", "Tao yeu cau dat san");
        PERMISSIONS.put("booking.view_own", "Xem lich su dat san cua minh");
        PERMISSIONS.put("booking.cancel", "Huy dat san");
        PERMISSIONS.put("booking.review", "Xac nhan hoac tu choi yeu cau dat san");
        PERMISSIONS.put("payment.view", "Xem lich su thanh toan");
        PERMISSIONS.put("refund.process", "Xu ly hoan tien");
        PERMISSIONS.put("finance.fee.config", "Thiet lap phi nen tang");
        PERMISSIONS.put("finance.withdrawal.request", "Gui yeu cau rut tien");
        PERMISSIONS.put("finance.withdrawal.review", "Duyet yeu cau rut tien");
        PERMISSIONS.put("finance.payout.confirm", "Xac nhan giao dich chi tra");
        PERMISSIONS.put("match.record", "Ghi nhan ket qua tran dau");
        PERMISSIONS.put("partner.match", "Su dung tinh nang ghep cap doi tac");
        PERMISSIONS.put("report.handle", "Xu ly bao cao vi pham");
        PERMISSIONS.put("stats.system.view", "Xem thong ke he thong");
    }

    private static final Map<String, String> ROLE_NAMES = new LinkedHashMap<>();

    static {
        ROLE_NAMES.put(Role.PLAYER, "Nguoi choi");
        ROLE_NAMES.put(Role.OWNER, "Chu san");
        ROLE_NAMES.put(Role.STAFF, "Nhan vien san");
        ROLE_NAMES.put(Role.ADMIN, "Quan tri vien");
    }

    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
            Role.PLAYER, List.of(
                    "booking.create", "booking.view_own", "booking.cancel",
                    "payment.view", "match.record", "partner.match"),
            Role.OWNER, List.of(
                    "venue.create", "venue.update", "court.manage", "court.block",
                    "booking.review", "payment.view", "finance.withdrawal.request"),
            Role.STAFF, List.of(
                    "court.block", "booking.review", "payment.view"),
            Role.ADMIN, List.copyOf(PERMISSIONS.keySet()));

    @Override
    public String name() {
        return "roles + permissions";
    }

    @Override
    @Transactional
    public void seed() {
        if (roleRepository.count() > 0) {
            log.info("  [roles] da co du lieu, bo qua");
            return;
        }

        Map<String, Permission> permissions = new LinkedHashMap<>();
        PERMISSIONS.forEach((code, label) -> {
            Permission permission = new Permission(code, label, null);
            permission.setId(SeedIds.of("permission:" + code));
            permissions.put(code, permissionRepository.save(permission));
        });

        ROLE_NAMES.forEach((code, label) -> {
            Role role = new Role(code, label, "Vai tro " + label.toLowerCase());
            role.setId(SeedIds.of("role:" + code));
            for (String permissionCode : ROLE_PERMISSIONS.get(code)) {
                role.getRolePermissions().add(new RolePermission(role, permissions.get(permissionCode)));
            }
            roleRepository.save(role);
        });

        log.info("  [roles] {} vai tro, {} quyen", ROLE_NAMES.size(), permissions.size());
    }
}
