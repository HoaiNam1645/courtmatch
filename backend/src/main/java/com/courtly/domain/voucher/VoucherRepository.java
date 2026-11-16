package com.courtly.domain.voucher;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, UUID> {

    @Query("SELECT v FROM Voucher v WHERE upper(v.code) = upper(:code)")
    Optional<Voucher> findByCode(@Param("code") String code);

    /** Khoa dong de kiem tra gioi han so luot dung an toan khi nhieu nguoi cung dung. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT v FROM Voucher v WHERE v.id = :id")
    Optional<Voucher> findByIdForUpdate(@Param("id") UUID id);

    @Query("SELECT v FROM Voucher v ORDER BY v.createdAt DESC, v.id DESC")
    List<Voucher> findAllNewestFirst();
}
