package com.platform.content.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.AssetType;
import com.platform.content.entity.ContentAsset;
import com.platform.content.repository.ContentAssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ContentAssetService {

    private final ContentAssetRepository repository;

    public ContentAssetService(ContentAssetRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ContentAsset register(AssetType assetType, String storageKey, String mimeType, Long sizeBytes) {
        return repository.save(ContentAsset.register(assetType, storageKey, mimeType, sizeBytes));
    }

    @Transactional(readOnly = true)
    public ContentAsset getById(UUID id) {
        return repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("ContentAsset", id));
    }

}