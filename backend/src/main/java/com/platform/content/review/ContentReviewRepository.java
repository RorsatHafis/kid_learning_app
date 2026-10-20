package com.platform.content.review;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

/**
 * Persistence for content reviews, with locked reads so one version's review can't be decided twice at once.
 */
public interface ContentReviewRepository extends JpaRepository<ContentReview, UUID> {

    /** The latest review of a version, whatever its state. */
    Optional<ContentReview> findFirstByVersionIdOrderByRequestedAtDesc(UUID versionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ContentReview r where r.versionId = :versionId and r.status = com.platform.content.review.ContentReview.Status.OPEN")
    Optional<ContentReview> findOpenForUpdate(@Param("versionId") UUID versionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ContentReview r where r.id = :id")
    Optional<ContentReview> findByIdForUpdate(@Param("id") UUID id);

}
