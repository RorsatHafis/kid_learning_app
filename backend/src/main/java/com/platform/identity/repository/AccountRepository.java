package com.platform.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import com.platform.identity.entity.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {

     Optional<Account> findByNormalizedEmail(String normalizedEmail);

     /** Row-locked: serializes decisions that read then write one account (resend limits, password reset). */
     @Lock(LockModeType.PESSIMISTIC_WRITE)
     @Query("select a from Account a where a.id = :id")
     Optional<Account> findForUpdate(@Param("id") UUID id);
    
}
