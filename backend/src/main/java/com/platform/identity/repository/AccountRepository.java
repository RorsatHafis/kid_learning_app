package com.platform.identity.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.identity.entity.Account;

public interface AccountRepository extends JpaRepository<Account, UUID> {

     Optional<Account> findByNormalizedEmail(String normalizedEmail);
    
}
