package com.platform.learner.repository;

import com.platform.learner.entity.LearnerSkillHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Persistence for the history of a child's skill mastery: each change in order, and the evidence and mastery each
 * skill had at a past moment (for 'what changed' views).
 */
public interface LearnerSkillHistoryRepository extends JpaRepository<LearnerSkillHistory, UUID> {

    List<LearnerSkillHistory> findByChildIdAndSkillIdOrderByOccurredAtDesc(UUID childId, UUID skillId);

    /** A skill's evidence as it stood at a moment: how many answers, and the estimate after the last of them. */
    interface EvidenceAt {
        UUID getSkillId();

        long getEvidence();

        BigDecimal getMastery();
    }

    /**
     * Every skill the child had evidence on by {@code at}, as it stood then: one row per skill, read in
     * one statement. A history row with a source is one piece of evidence (MasteryEngine#recordEvidence);
     * the estimate is the one after the latest row.
     */
    @Query(nativeQuery = true, value = """
            select skill_id as skillId,
                   count(*) filter (where triggered_by_type is not null) as evidence,
                   (array_agg(mastery_probability order by occurred_at desc, id desc))[1] as mastery
            from learner_skill_history
            where child_id = :childId and occurred_at <= :at
            group by skill_id
            """)
    List<EvidenceAt> evidenceAt(@Param("childId") UUID childId, @Param("at") Instant at);

    /** One skill's history for several children, oldest first (a class's children on a skill). */
    List<LearnerSkillHistory> findBySkillIdAndChildIdInOrderByOccurredAtAsc(UUID skillId, Collection<UUID> childIds);

}
