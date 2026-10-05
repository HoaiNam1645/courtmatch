package com.courtly.domain.account;

import com.courtly.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Vai tro: player, owner, staff, admin. */
@Entity
@Table(name = "roles")
@Getter
@Setter
@NoArgsConstructor
public class Role extends BaseEntity {

    public static final String PLAYER = "player";
    public static final String OWNER = "owner";
    public static final String STAFF = "staff";
    public static final String ADMIN = "admin";

    @Column(name = "code", nullable = false, unique = true, length = 50)
    private String code;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description")
    private String description;

    @OneToMany(mappedBy = "role", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<RolePermission> rolePermissions = new LinkedHashSet<>();

    public Role(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }
}
