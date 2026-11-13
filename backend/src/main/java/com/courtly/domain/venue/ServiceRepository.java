package com.courtly.domain.venue;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceRepository extends JpaRepository<Service, UUID> {

    /** Danh muc tien ich cho form san (2.3.3). */
    List<Service> findAllByOrderByNameAsc();

    List<Service> findByCodeIn(Collection<String> codes);
}
