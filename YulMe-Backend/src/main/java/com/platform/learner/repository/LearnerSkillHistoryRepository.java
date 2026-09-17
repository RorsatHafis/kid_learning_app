package com.platform.learner.repository;

import com.platform.learner.entity.LearnerSkillHistory;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface LearnerSkillHistoryRepository extends JpaRepository<LearnerSkillHistory, UUID> {

    List<LearnerSkillHistory> findByChildIdAndSkillIdOrderByOccurredAtDesc(UUID childId, UUID skillId);

}