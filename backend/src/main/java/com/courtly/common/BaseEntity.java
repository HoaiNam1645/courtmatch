package com.courtly.common;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.proxy.HibernateProxy;
import org.springframework.data.domain.Persistable;

/**
 * Khoa chinh UUID va moc thoi gian tao.
 *
 * <p>Id duoc sinh o tang ung dung (khong dung {@code @GeneratedValue}) de seeder
 * co the gan id on dinh, giup chay lai seeder nhieu lan van ra cung bo du lieu.
 *
 * <p>Vi id da co san truoc khi luu, Spring Data se hieu nham day la ban ghi cu va goi
 * {@code merge()}. Lop nay cai {@link Persistable} de {@code save()} dung {@code persist()},
 * vua tiet kiem mot cau SELECT vua tranh loi khi cascade sang entity dung {@code @MapsId}.
 */
@MappedSuperclass
@Getter
@Setter
public abstract class BaseEntity implements Persistable<UUID> {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Transient
    private boolean newEntity = true;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostPersist
    @PostLoad
    void markNotNew() {
        this.newEntity = false;
    }

    @PrePersist
    void applyBaseDefaults() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    @Override
    public final boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (other == null) {
            return false;
        }
        Class<?> thisType = effectiveClass(this);
        Class<?> otherType = effectiveClass(other);
        if (thisType != otherType) {
            return false;
        }
        UUID otherId = ((BaseEntity) other).getId();
        return id != null && id.equals(otherId);
    }

    @Override
    public final int hashCode() {
        // Hash on dinh ke ca khi entity chua co id, tranh loi khi dung trong HashSet.
        return effectiveClass(this).hashCode();
    }

    private static Class<?> effectiveClass(Object entity) {
        return entity instanceof HibernateProxy proxy
                ? proxy.getHibernateLazyInitializer().getPersistentClass()
                : entity.getClass();
    }
}
