package com.platform.curriculum.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class LearningObjectiveService {

    private final LearningObjectiveRepository repository;

    public LearningObjectiveService(LearningObjectiveRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public LearningObjective create(UUID subjectId, String code, String title, String description) {
        return repository.save(LearningObjective.create(subjectId, code, title, description));
    }

    @Transactional(readOnly = true)
    public LearningObjective getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("LearningObjective", id));
    }

    @Transactional(readOnly = true)
    public List<LearningObjective> listBySubject(UUID subjectId) {
        return repository.findBySubjectId(subjectId);
    }

}