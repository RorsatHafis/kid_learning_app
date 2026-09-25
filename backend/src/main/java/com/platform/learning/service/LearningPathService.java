package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.repository.LearningPathItemRepository;
import com.platform.learning.repository.LearningPathRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class LearningPathService {

    private final LearningPathRepository learningPathRepository;
    private final LearningPathItemRepository learningPathItemRepository;

    public LearningPathService(LearningPathRepository learningPathRepository,
                                LearningPathItemRepository learningPathItemRepository) {
        this.learningPathRepository = learningPathRepository;
        this.learningPathItemRepository = learningPathItemRepository;
    }

    /**
     * Battle 5A: serializes path-item insertion per path (see
     * {@link LearningPathRepository#findByIdForUpdate}). A caller that decides
     * whether to insert based on the path's current items (AdaptiveEngine,
     * SmartReviewService) must call this BEFORE reading them, so its duplicate
     * check and its append happen under the same lock - locking only inside
     * {@link #appendItem} would still let two concurrent callers both pass the
     * check and append the same thing twice. MANDATORY: the lock is held until the
     * caller's transaction ends, so there must be one.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockPath(UUID learningPathId) {
        lockOrThrow(learningPathId);
    }

    /** Appends an item at the end of the path - used both for initial curriculum sequencing and later adaptive/review insertions. */
    @Transactional
    public LearningPathItem appendItem(UUID learningPathId, UUID activityVersionId, LearningPathItemSource source) {
        // Re-acquiring a lock this transaction already holds is a no-op in PostgreSQL.
        lockOrThrow(learningPathId);
        int nextSequenceOrder = learningPathItemRepository.findTopByLearningPathIdOrderBySequenceOrderDesc(learningPathId)
                .map(previous -> previous.getSequenceOrder() + 1)
                .orElse(0);
        return learningPathItemRepository.save(
                LearningPathItem.place(learningPathId, activityVersionId, nextSequenceOrder, source, Instant.now()));
    }

    @Transactional(readOnly = true)
    public List<LearningPathItem> listItems(UUID learningPathId) {
        return learningPathItemRepository.findByLearningPathIdOrderBySequenceOrderAsc(learningPathId);
    }

    /** The next item the child should work on - the first PENDING or IN_PROGRESS item in sequence order. */
    @Transactional(readOnly = true)
    public LearningPathItem getNextItem(UUID learningPathId) {
        return learningPathItemRepository
                .findFirstByLearningPathIdAndStatusInOrderBySequenceOrderAsc(
                        learningPathId, List.of(LearningPathItemStatus.PENDING, LearningPathItemStatus.IN_PROGRESS))
                .orElseThrow(() -> new ResourceNotFoundException("Next pending LearningPathItem for path", learningPathId));
    }

    @Transactional
    public void markStarted(UUID learningPathItemId) {
        getItem(learningPathItemId).start();
    }

    @Transactional
    public void markCompleted(UUID learningPathItemId) {
        getItem(learningPathItemId).complete(Instant.now());
    }

    @Transactional(readOnly = true)
    public LearningPathItem getItem(UUID learningPathItemId) {
        return learningPathItemRepository.findById(learningPathItemId)
                .orElseThrow(() -> new ResourceNotFoundException("LearningPathItem", learningPathItemId));
    }

    private void lockOrThrow(UUID learningPathId) {
        if (learningPathRepository.findByIdForUpdate(learningPathId).isEmpty()) {
            throw new ResourceNotFoundException("LearningPath", learningPathId);
        }
    }

}