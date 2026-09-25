package com.platform.learner.repository;

import com.platform.learner.entity.LearnerSkillState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearnerSkillStateRepository extends JpaRepository<LearnerSkillState, UUID> {

    Optional<LearnerSkillState> findByChildIdAndSkillId(UUID childId, UUID skillId);

    List<LearnerSkillState> findByChildId(UUID childId);

}