package com.platform.content.repository;

import com.platform.common.entity.CatalogStatus;
import com.platform.content.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuestionRepository extends JpaRepository<Question, UUID> {

    List<Question> findByOwnerAccountId(UUID ownerAccountId);

    List<Question> findBySubjectIdAndStatus(UUID subjectId, CatalogStatus status);

}
