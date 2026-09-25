package com.platform.learner.repository;

import com.platform.learner.entity.LearnerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface LearnerProfileRepository extends JpaRepository<LearnerProfile, UUID> {

    Optional<LearnerProfile> findByChildId(UUID childId);

}