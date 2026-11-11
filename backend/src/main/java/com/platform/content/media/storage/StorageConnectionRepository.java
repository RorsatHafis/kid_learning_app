package com.platform.content.media.storage;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StorageConnectionRepository extends JpaRepository<StorageConnection, UUID> {

    List<StorageConnection> findBySchoolIdOrderByCreatedAtDesc(UUID schoolId);
}
