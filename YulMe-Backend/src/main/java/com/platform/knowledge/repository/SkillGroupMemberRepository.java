package com.platform.knowledge.repository;

import com.platform.knowledge.entity.SkillGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SkillGroupMemberRepository extends JpaRepository<SkillGroupMember, UUID> {

    List<SkillGroupMember> findBySkillGroupIdOrderByDisplayOrderAsc(UUID skillGroupId);

}