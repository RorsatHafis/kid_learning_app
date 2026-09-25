package com.platform.child.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.child.entity.ConsentRecord;
import com.platform.child.entity.ConsentType;

public interface ConsentRecordRepository extends JpaRepository<ConsentRecord, UUID> {

    Optional<ConsentRecord> findFirstByChildIdAndConsentTypeOrderByOccurredAtDesc(UUID childId, ConsentType consentType);
    
}
