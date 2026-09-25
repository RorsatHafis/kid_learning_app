package com.platform.parent.repository;

import com.platform.parent.entity.ParentInsight;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ParentInsightRepository extends JpaRepository<ParentInsight, UUID> {

    List<ParentInsight> findByChildIdOrderByOccurredAtDesc(UUID childId);

}
