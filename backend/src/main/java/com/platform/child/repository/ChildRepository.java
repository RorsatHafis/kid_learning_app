package com.platform.child.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.child.entity.Child;

public interface ChildRepository extends JpaRepository<Child, UUID> {

    List<Child> findByFamilyId(UUID familyId);
    
}
