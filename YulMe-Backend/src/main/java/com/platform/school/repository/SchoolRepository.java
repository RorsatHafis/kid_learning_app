package com.platform.school.repository;
import com.platform.school.entity.School; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
public interface SchoolRepository extends JpaRepository<School, UUID> { List<School> findByPrincipalAccountId(UUID principalAccountId); }
