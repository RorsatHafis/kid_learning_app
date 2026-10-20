package com.platform.curriculum.repository;

import com.platform.curriculum.entity.AgeHub;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for age bands, by code and in display order.
 */
public interface AgeHubRepository extends JpaRepository<AgeHub, UUID> {

    Optional<AgeHub> findByCode(String code);

    List<AgeHub> findAllByOrderByDisplayOrderAsc();

    /**
     * The age group a child of {@code age} belongs to: the first, in display order, that includes it.
     * Automatic enrolment and the family catalog use this one rule.
     */
    default Optional<AgeHub> findForAge(int age) {
        return findAllByOrderByDisplayOrderAsc().stream().filter(hub -> hub.includesAge(age)).findFirst();
    }

}