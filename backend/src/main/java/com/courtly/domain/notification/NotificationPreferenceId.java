package com.courtly.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreferenceId implements Serializable {

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "type", nullable = false, length = 80)
    private String type;

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof NotificationPreferenceId that)) {
            return false;
        }
        return Objects.equals(userId, that.userId) && Objects.equals(type, that.type);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, type);
    }
}
