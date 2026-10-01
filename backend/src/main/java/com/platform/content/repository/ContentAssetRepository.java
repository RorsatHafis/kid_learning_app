package com.platform.content.repository;

import com.platform.content.entity.ContentAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ContentAssetRepository extends JpaRepository<ContentAsset, UUID> {

    /** Platform media (no owner) already uploaded with these bytes. */
    Optional<ContentAsset> findBySha256AndOwnerAccountIdIsNull(String sha256);

    Optional<ContentAsset> findBySha256AndOwnerAccountId(String sha256, UUID ownerAccountId);
}
