package com.platform.learning.service;

import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.learning.entity.LearningSession;
import com.platform.learning.repository.LearningSessionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LearningSessionService {

    private final LearningSessionRepository repository;
    private final ChildRepository childRepository;

    public LearningSessionService(LearningSessionRepository repository, ChildRepository childRepository) {
        this.repository = repository;
        this.childRepository = childRepository;
    }

    /**
     * Starts a new session. If the child already has an open (unended) session,
     * returns that one instead of opening a second - handles the "resume after
     * interruption" and "duplicate start request" cases Section 23 calls out
     * (a client that lost connectivity and retries "start session" shouldn't
     * fragment one real session into two).
     */
    @Transactional
    public LearningSession start(UUID childId, Instant startedAt) {
        if (!childRepository.existsById(childId)) {
            throw new ResourceNotFoundException("Child", childId);
        }

        List<LearningSession> open = repository.findByChildIdAndEndedAtIsNull(childId);
        if (!open.isEmpty()) {
            return open.get(0);
        }

        return repository.save(LearningSession.start(childId, startedAt));
    }

    @Transactional
    public void end(UUID sessionId, Instant endedAt) {
        getSession(sessionId).end(endedAt);
    }

    @Transactional(readOnly = true)
    public LearningSession getSession(UUID sessionId) {
        return repository.findById(sessionId).orElseThrow(() -> new ResourceNotFoundException("LearningSession", sessionId));
    }

}