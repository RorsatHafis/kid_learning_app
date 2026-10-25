package com.platform.identity.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.platform.identity.entity.Account;
import com.platform.identity.entity.AccountStatus;
import com.platform.identity.entity.PlatformRole;

/**
 * Persistence for accounts: by normalised email, a locked read for state changes, staff searches, and whether any
 * account has a role (first administrator).
 */
public interface AccountRepository extends JpaRepository<Account, UUID> {

     Optional<Account> findByNormalizedEmail(String normalizedEmail);

     /** M9b: the account that verified this number (at most one: uq_accounts_verified_phone). */
     Optional<Account> findByPhoneE164AndPhoneVerifiedAtIsNotNull(String phoneE164);

     /** Row-locked: serializes decisions that read then write one account (resend limits, password reset). */
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("select a from Account a where a.id = :id")
     Optional<Account> findForUpdate(@Param("id") UUID id);

     /** Accounts in the given roles, optionally narrowed by an email "contains" pattern (SearchText), by email. */
     @Query("""
             select a from Account a
             where a.platformRole in :roles
               and (:pattern is null or a.normalizedEmail like :pattern)
             order by a.normalizedEmail, a.id
             """)
     Page<Account> searchByRoles(@Param("roles") Collection<PlatformRole> roles, @Param("pattern") String pattern,
                                 Pageable pageable);

     List<Account> findByPlatformRoleAndStatusOrderByCreatedAtAsc(PlatformRole platformRole, AccountStatus status);
    
    boolean existsByPlatformRole(com.platform.identity.entity.PlatformRole platformRole);

}
