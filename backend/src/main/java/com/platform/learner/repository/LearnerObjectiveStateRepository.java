package com.platform.learner.repository;

import com.platform.learner.entity.LearnerObjectiveState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LearnerObjectiveStateRepository extends JpaRepository<LearnerObjectiveState, UUID> {

    Optional<LearnerObjectiveState> findByChildIdAndLearningObjectiveId(UUID childId, UUID learningObjectiveId);

    List<LearnerObjectiveState> findByChildId(UUID childId);

}