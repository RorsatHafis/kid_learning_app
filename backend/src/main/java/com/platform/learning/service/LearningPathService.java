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

    /**
     * Appends an item at the end of the path - used both for initial curriculum sequencing and later
     * adaptive/review insertions. Position records placement order; which item is served next is
     * decided by {@link #nextOf}.
     */
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

    /** The next item the child should work on - see {@link #nextOf}. */
    @Transactional(readOnly = true)
    public LearningPathItem getNextItem(UUID learningPathId) {
        return nextOf(learningPathItemRepository.findByLearningPathIdOrderBySequenceOrderAsc(learningPathId))
                .orElseThrow(() -> new ResourceNotFoundException("Next pending LearningPathItem for path", learningPathId));
    }

    /**
     * Which step comes next (LI-1). Items are appended in the order they were placed, and the
     * curriculum is seeded in full at enrolment, so "first waiting item by position" would put
     * practice the Adaptive Engine or Smart Review just decided a child needs behind every
     * remaining curriculum step. The rule instead:
     * <ol>
     *   <li>an item already IN_PROGRESS resumes first (an attempt is open on it);</li>
     *   <li>then placed practice waiting to be done - ADAPTIVE, REVIEW, TEACHER_ASSIGNED - in the
     *       order it was placed;</li>
     *   <li>then the curriculum, in its order.</li>
     * </ol>
     * Positions never change (sequence_order is immutable); only the choice of the next one does.
     */
    public static java.util.Optional<LearningPathItem> nextOf(List<LearningPathItem> items) {
        return playOrder(items).stream()
                .filter(item -> item.getStatus() == LearningPathItemStatus.PENDING
                        || item.getStatus() == LearningPathItemStatus.IN_PROGRESS)
                .findFirst();
    }

    /**
     * The path in the order a child meets it: what is done (in the order it was completed),
     * then what is waiting in the order {@link #nextOf} will serve it. SKIPPED items are left out.
     */
    public static List<LearningPathItem> playOrder(List<LearningPathItem> items) {
        java.util.Comparator<LearningPathItem> bySequence = java.util.Comparator.comparingInt(LearningPathItem::getSequenceOrder);
        List<LearningPathItem> done = items.stream()
                .filter(item -> item.getStatus() == LearningPathItemStatus.COMPLETED)
                .sorted(java.util.Comparator.comparing(LearningPathItem::getCompletedAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())).thenComparing(bySequence))
                .toList();
        List<LearningPathItem> inProgress = items.stream()
                .filter(item -> item.getStatus() == LearningPathItemStatus.IN_PROGRESS).sorted(bySequence).toList();
        List<LearningPathItem> placedPractice = items.stream()
                .filter(item -> item.getStatus() == LearningPathItemStatus.PENDING
                        && item.getSource() != LearningPathItemSource.CURRICULUM)
                .sorted(bySequence).toList();
        List<LearningPathItem> curriculum = items.stream()
                .filter(item -> item.getStatus() == LearningPathItemStatus.PENDING
                        && item.getSource() == LearningPathItemSource.CURRICULUM)
                .sorted(bySequence).toList();
        List<LearningPathItem> ordered = new java.util.ArrayList<>(items.size());
        ordered.addAll(done);
        ordered.addAll(inProgress);
        ordered.addAll(placedPractice);
        ordered.addAll(curriculum);
        return ordered;
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