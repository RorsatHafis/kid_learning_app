package com.platform.child.service;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.platform.child.entity.ConsentAction;
import com.platform.child.entity.ConsentRecord;
import com.platform.child.entity.ConsentType;
import com.platform.child.repository.ConsentRecordRepository;

@Service
public class ConsentService {

    private final ConsentRecordRepository repository;
 
    public ConsentService(ConsentRecordRepository repository) {
        this.repository = repository;
    }
 
    @Transactional
    public ConsentRecord grant(UUID childId, UUID grantedByAccountId, ConsentType consentType) {

        return recordChange(childId, grantedByAccountId, consentType, ConsentAction.GRANTED);

    }
 
    @Transactional
    public ConsentRecord revoke(UUID childId, UUID revokedByAccountId, ConsentType consentType) {

        return recordChange(childId, revokedByAccountId, consentType, ConsentAction.REVOKED);

    }
 
    @Transactional(readOnly = true)
    public boolean isCurrentlyGranted(UUID childId, ConsentType consentType) {

        return latest(childId, consentType)
                .map(record -> record.getAction() == ConsentAction.GRANTED)
                .orElse(false);

    }
 
    private ConsentRecord recordChange(UUID childId, UUID accountId, ConsentType consentType, ConsentAction action) {

        Optional<ConsentRecord> latest = latest(childId, consentType);
        boolean currentlyGranted = latest.map(r -> r.getAction() == ConsentAction.GRANTED).orElse(false);
        boolean requestingGrant = action == ConsentAction.GRANTED;

        if (latest.isPresent() && requestingGrant == currentlyGranted) {

            return latest.get();

        }
 
        return repository.save(ConsentRecord.record(childId, accountId, consentType, action));

    }
 
    private Optional<ConsentRecord> latest(UUID childId, ConsentType consentType) {

        return repository.findFirstByChildIdAndConsentTypeOrderByOccurredAtDesc(childId, consentType);
        
    }
    
}
