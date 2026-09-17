package com.platform.learner.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.learner.entity.LearnerProfile;
import com.platform.learner.repository.LearnerProfileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class LearnerProfileService {

    private final LearnerProfileRepository repository;

    public LearnerProfileService(LearnerProfileRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LearnerProfile getOrCreate(UUID childId) {
        return repository.findByChildId(childId).orElseGet(() -> repository.save(LearnerProfile.createFor(childId)));
    }

    @Transactional(readOnly = true)
    public LearnerProfile getByChildId(UUID childId) {
        return repository.findByChildId(childId).orElseThrow(() -> new ResourceNotFoundException("LearnerProfile for child", childId));
    }

    @Transactional
    public LearnerProfile recordActivityCompletion(UUID childId, long additionalTimeSeconds) {
        LearnerProfile profile = getOrCreate(childId);
        profile.recordActivityCompletion(additionalTimeSeconds, Instant.now());
        return profile;
    }

}