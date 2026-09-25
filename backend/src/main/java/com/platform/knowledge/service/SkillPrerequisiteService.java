package com.platform.knowledge.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.knowledge.entity.SkillPrerequisite;
import com.platform.knowledge.exception.CyclicPrerequisiteException;
import com.platform.knowledge.repository.SkillPrerequisiteRepository;
import com.platform.knowledge.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SkillPrerequisiteService {

    private final SkillPrerequisiteRepository repository;
    private final SkillRepository skillRepository;

    public SkillPrerequisiteService(SkillPrerequisiteRepository repository, SkillRepository skillRepository) {
        this.repository = repository;
        this.skillRepository = skillRepository;
    }

    @Transactional
    public SkillPrerequisite addPrerequisite(UUID skillId, UUID prerequisiteSkillId) {
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill", skillId);
        }
        if (!skillRepository.existsById(prerequisiteSkillId)) {
            throw new ResourceNotFoundException("Skill", prerequisiteSkillId);
        }
        if (wouldCreateCycle(skillId, prerequisiteSkillId)) {
            throw new CyclicPrerequisiteException(skillId, prerequisiteSkillId);
        }
        return repository.save(SkillPrerequisite.create(skillId, prerequisiteSkillId));
    }

    @Transactional(readOnly = true)
    public List<SkillPrerequisite> listPrerequisites(UUID skillId) {
        return repository.findBySkillId(skillId);
    }

    @Transactional(readOnly = true)
    public List<SkillPrerequisite> listDependents(UUID prerequisiteSkillId) {
        return repository.findByPrerequisiteSkillId(prerequisiteSkillId);
    }

    private boolean wouldCreateCycle(UUID skillId, UUID candidatePrerequisiteId) {
        Set<UUID> visited = new HashSet<>();
        Deque<UUID> toVisit = new ArrayDeque<>();
        toVisit.push(candidatePrerequisiteId);

        while (!toVisit.isEmpty()) {
            UUID current = toVisit.pop();
            if (current.equals(skillId)) {
                return true;
            }
            if (!visited.add(current)) {
                continue;
            }
            for (SkillPrerequisite edge : repository.findBySkillId(current)) {
                toVisit.push(edge.getPrerequisiteSkillId());
            }
        }
        return false;
    }

}