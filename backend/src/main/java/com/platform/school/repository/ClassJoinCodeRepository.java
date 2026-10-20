package com.platform.school.repository;

import com.platform.school.entity.ClassJoinCode;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for class join codes: by code, and a class's code that is still valid.
 */
public interface ClassJoinCodeRepository extends JpaRepository<ClassJoinCode, UUID> {

    Optional<ClassJoinCode> findByCode(String code);

    /** The class's code that hasn't been revoked, if any (at most one, V43); it may have expired. */
    Optional<ClassJoinCode> findBySchoolClassIdAndRevokedAtIsNull(UUID schoolClassId);
}
