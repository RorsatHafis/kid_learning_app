package com.platform.identity.repository;

import com.platform.identity.entity.AccountActionPurpose;
import com.platform.identity.entity.AccountActionToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountActionTokenRepository extends JpaRepository<AccountActionToken, UUID> {

    /** Row-locked, so two uses of one link are serialized and only the first succeeds. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from AccountActionToken t where t.tokenHash = :hash and t.purpose = :purpose")
    Optional<AccountActionToken> findForUse(@Param("hash") String tokenHash, @Param("purpose") AccountActionPurpose purpose);

    List<AccountActionToken> findByAccountIdAndPurposeAndConsumedAtIsNullAndInvalidatedAtIsNull(UUID accountId,
                                                                                                 AccountActionPurpose purpose);

    long countByAccountIdAndPurposeAndIssuedAtAfter(UUID accountId, AccountActionPurpose purpose, Instant after);

    Optional<AccountActionToken> findFirstByAccountIdAndPurposeAndIssuedAtAfterOrderByIssuedAtAsc(UUID accountId,
                                                                                                 AccountActionPurpose purpose,
                                                                                                 Instant after);

    Optional<AccountActionToken> findFirstByAccountIdAndPurposeOrderByIssuedAtDesc(UUID accountId,
                                                                                   AccountActionPurpose purpose);

}
