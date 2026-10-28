package com.platform.school.repository;

import com.platform.school.entity.AcademicTerm;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AcademicTermRepository extends JpaRepository<AcademicTerm, UUID> {

    List<AcademicTerm> findByAcademicYearIdOrderByStartsOn(UUID academicYearId);

    List<AcademicTerm> findByAcademicYearIdInOrderByStartsOn(Collection<UUID> academicYearIds);
}
