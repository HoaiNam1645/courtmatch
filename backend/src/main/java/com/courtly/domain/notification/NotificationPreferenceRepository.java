package com.courtly.domain.notification;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreference, NotificationPreferenceId> {

    @Query("select p from NotificationPreference p where p.id.userId = :userId")
    List<NotificationPreference> findByUserId(@Param("userId") UUID userId);
}
