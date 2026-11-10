package com.platform.identity.registration;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RegistrationVerificationRepository extends JpaRepository<RegistrationVerification, UUID> {

    /** Locked, so two opens of the same link can't both yield a grant. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from RegistrationVerification v where v.linkTokenHash = :hash")
    Optional<RegistrationVerification> findByLinkForUpdate(@Param("hash") String hash);

    /** Locked, so two registrations with the same grant can't both create an account. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select v from RegistrationVerification v where v.grantHash = :hash")
    Optional<RegistrationVerification> findByGrantForUpdate(@Param("hash") String hash);

    Optional<RegistrationVerification> findFirstByNormalizedEmailOrderByCreatedAtDesc(String normalizedEmail);

    long countByNormalizedEmailAndCreatedAtAfter(String normalizedEmail, Instant after);
}
