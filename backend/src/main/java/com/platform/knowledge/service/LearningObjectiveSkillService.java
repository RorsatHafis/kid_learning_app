package com.platform.knowledge.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.repository.LearningObjectiveSkillRepository;
import com.platform.knowledge.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Links a curriculum-domain LearningObjective to the knowledge-domain Skills it covers. */
@Service
public class LearningObjectiveSkillService {

    private final LearningObjectiveSkillRepository repository;
    private final LearningObjectiveRepository learningObjectiveRepository;
    private final SkillRepository skillRepository;

    public LearningObjectiveSkillService(LearningObjectiveSkillRepository repository,
                                          LearningObjectiveRepository learningObjectiveRepository,
                                          SkillRepository skillRepository) {
        this.repository = repository;
        this.learningObjectiveRepository = learningObjectiveRepository;
        this.skillRepository = skillRepository;
    }

    @Transactional
    public LearningObjectiveSkill link(UUID learningObjectiveId, UUID skillId) {
        if (!learningObjectiveRepository.existsById(learningObjectiveId)) {
            throw new ResourceNotFoundException("LearningObjective", learningObjectiveId);
        }
        if (!skillRepository.existsById(skillId)) {
            throw new ResourceNotFoundException("Skill", skillId);
        }
        return repository.save(LearningObjectiveSkill.create(learningObjectiveId, skillId));
    }

    @Transactional(readOnly = true)
    public List<LearningObjectiveSkill> listSkillsForObjective(UUID learningObjectiveId) {
        return repository.findByLearningObjectiveId(learningObjectiveId);
    }

}