package com.platform.learning.service;

import java.util.Optional;
import java.util.UUID;

/**
 * Names the class whose teacher placed practice on a child's path, for the family's explanation
 * ("Grade 1 A · Wat Phnom Primary"). Implemented by the school module, so the learning module
 * doesn't depend on it.
 */
public interface PlacementNames {

    Optional<String> classLabel(UUID classId);
}
