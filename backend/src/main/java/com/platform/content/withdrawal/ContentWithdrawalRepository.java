package com.platform.content.withdrawal;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ContentWithdrawalRepository extends JpaRepository<ContentWithdrawal, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from ContentWithdrawal w where w.versionId = :versionId and w.restoredAt is null")
    Optional<ContentWithdrawal> findActive(@Param("versionId") UUID versionId);

    @Query("select w from ContentWithdrawal w where w.versionId = :versionId and w.restoredAt is null")
    Optional<ContentWithdrawal> findActiveForRead(@Param("versionId") UUID versionId);

}
