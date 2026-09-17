package com.platform.curriculum.repository;

import com.platform.common.entity.CatalogStatus;
import com.platform.curriculum.entity.Subject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SubjectRepository extends JpaRepository<Subject, UUID> {

    Optional<Subject> findByCode(String code);

    List<Subject> findByStatus(CatalogStatus status);

}