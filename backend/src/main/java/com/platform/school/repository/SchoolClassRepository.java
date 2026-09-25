package com.platform.school.repository;
import com.platform.school.entity.SchoolClass; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> { List<SchoolClass> findBySchoolId(UUID schoolId); }
