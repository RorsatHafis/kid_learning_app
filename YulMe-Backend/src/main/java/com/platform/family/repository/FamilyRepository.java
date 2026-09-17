package com.platform.family.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.platform.family.entity.Family;

public interface FamilyRepository extends JpaRepository<Family, UUID> {
    
}
