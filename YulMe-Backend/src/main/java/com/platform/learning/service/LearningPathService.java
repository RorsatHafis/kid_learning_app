package com.platform.learning.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.learning.entity.LearningPathItem;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.repository.LearningPathItemRepository;
import com.platform.learning.repository.LearningPathRepository;
import org.springframework.stereotype.Service;
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

    /** Appends an item at the end of the path - used both for initial curriculum sequencing and later adaptive/review insertions. */
    @Transactional
    public LearningPathItem appendItem(UUID learningPathId, UUID activityVersionId, LearningPathItemSource source) {
        if (!learningPathRepository.existsById(learningPathId)) {
            throw new ResourceNotFoundException("LearningPath", learningPathId);
        }
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

}