package com.platform.knowledge.service;

import com.platform.common.entity.CatalogStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.entity.SkillGroup;
import com.platform.knowledge.entity.SkillGroupMember;
import com.platform.knowledge.repository.SkillGroupMemberRepository;
import com.platform.knowledge.repository.SkillGroupRepository;
import com.platform.knowledge.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SkillService {

    private final SkillRepository skillRepository;
    private final SkillGroupRepository skillGroupRepository;
    private final SkillGroupMemberRepository skillGroupMemberRepository;

    public SkillService(SkillRepository skillRepository, SkillGroupRepository skillGroupRepository,
                         SkillGroupMemberRepository skillGroupMemberRepository) {
        this.skillRepository = skillRepository;
        this.skillGroupRepository = skillGroupRepository;
        this.skillGroupMemberRepository = skillGroupMemberRepository;
    }

    @Transactional
    public Skill createSkill(UUID subjectId, String code, String name, String description) {
        return skillRepository.save(Skill.create(subjectId, code, name, description));
    }

    @Transactional(readOnly = true)
    public Skill getSkill(UUID id) {
        return skillRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill", id));
    }

    @Transactional(readOnly = true)
    public List<Skill> listActiveBySubject(UUID subjectId) {
        return skillRepository.findBySubjectIdAndStatus(subjectId, CatalogStatus.ACTIVE);
    }

    @Transactional
    public void archiveSkill(UUID id) {
        getSkill(id).archive();
    }

    @Transactional
    public SkillGroup createSkillGroup(UUID subjectId, String code, String name) {
        return skillGroupRepository.save(SkillGroup.create(subjectId, code, name));
    }

    @Transactional
    public SkillGroupMember addSkillToGroup(UUID skillGroupId, UUID skillId, int displayOrder) {
        if (!skillGroupRepository.existsById(skillGroupId)) {
            throw new ResourceNotFoundException("SkillGroup", skillGroupId);
        }
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill", skillId);
        }
        return skillGroupMemberRepository.save(SkillGroupMember.create(skillGroupId, skillId, displayOrder));
    }

    @Transactional(readOnly = true)
    public List<SkillGroupMember> listGroupMembers(UUID skillGroupId) {
        return skillGroupMemberRepository.findBySkillGroupIdOrderByDisplayOrderAsc(skillGroupId);
    }

}