package com.platform.curriculum.repository;

import com.platform.curriculum.entity.AgeHub;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AgeHubRepository extends JpaRepository<AgeHub, UUID> {

    Optional<AgeHub> findByCode(String code);

    List<AgeHub> findAllByOrderByDisplayOrderAsc();

}