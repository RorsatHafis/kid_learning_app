package com.platform.content.repository;

import com.platform.content.entity.ActivitySkill;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ActivitySkillRepository extends JpaRepository<ActivitySkill, UUID> {

    List<ActivitySkill> findByActivityId(UUID activityId);

    List<ActivitySkill> findBySkillId(UUID skillId);

}