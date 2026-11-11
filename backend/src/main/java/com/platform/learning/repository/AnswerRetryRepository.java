package com.platform.learning.repository;

import com.platform.learning.entity.AnswerRetry;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AnswerRetryRepository extends JpaRepository<AnswerRetry, UUID> {

    Optional<AnswerRetry> findByAnswerRecordId(UUID answerRecordId);

    List<AnswerRetry> findByAnswerRecordIdIn(Collection<UUID> answerRecordIds);
}
