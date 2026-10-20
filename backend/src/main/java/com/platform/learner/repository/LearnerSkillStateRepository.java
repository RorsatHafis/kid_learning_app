package com.platform.learner.repository;

import com.platform.learner.entity.LearnerSkillState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for a child's current mastery of each skill.
 */
public interface LearnerSkillStateRepository extends JpaRepository<LearnerSkillState, UUID> {

    Optional<LearnerSkillState> findByChildIdAndSkillId(UUID childId, UUID skillId);

    List<LearnerSkillState> findByChildId(UUID childId);

    /** Several children at once (a class's skill progress, X20). */
    List<LearnerSkillState> findByChildIdIn(Collection<UUID> childIds);

}