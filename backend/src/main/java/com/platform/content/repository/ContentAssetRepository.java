package com.platform.content.repository;

import com.platform.content.entity.ContentAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ContentAssetRepository extends JpaRepository<ContentAsset, UUID> {
}