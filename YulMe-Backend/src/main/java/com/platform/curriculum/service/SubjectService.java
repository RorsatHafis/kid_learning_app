package com.platform.curriculum.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.SubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SubjectService {

    private final SubjectRepository repository;

    public SubjectService(SubjectRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public Subject create(String code, String name, String description) {
        return repository.save(Subject.create(code, name, description));
    }

    @Transactional(readOnly = true)
    public Subject getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject", id));
    }

    @Transactional(readOnly = true)
    public List<Subject> listActive() {
        return repository.findByStatus(com.platform.common.entity.CatalogStatus.ACTIVE);
    }

    @Transactional
    public Subject rename(UUID id, String newName, String newDescription) {
        Subject subject = getById(id);
        subject.rename(newName, newDescription);
        return subject;
    }

    @Transactional
    public void archive(UUID id) {
        getById(id).archive();
    }

}