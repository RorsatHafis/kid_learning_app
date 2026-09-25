package com.platform.streak.service;
import com.platform.child.repository.ChildRepository; import com.platform.common.web.ResourceNotFoundException; import com.platform.streak.entity.LearnerStreak; import com.platform.streak.repository.LearnerStreakRepository; import org.springframework.stereotype.Service; import org.springframework.transaction.annotation.Transactional; import java.time.*; import java.util.UUID;
@Service public class LearnerStreakService {
 private final LearnerStreakRepository repo; private final ChildRepository children;
 public LearnerStreakService(LearnerStreakRepository repo,ChildRepository children){this.repo=repo;this.children=children;}
 @Transactional public LearnerStreak recordLearning(UUID childId,Instant at){if(!children.existsById(childId))throw new ResourceNotFoundException("Child",childId); LearnerStreak s=repo.findByChildId(childId).orElseGet(()->repo.save(LearnerStreak.create(childId))); s.record(at.atZone(ZoneOffset.UTC).toLocalDate()); return s;}
 @Transactional(readOnly=true) public LearnerStreak get(UUID childId){if(!children.existsById(childId))throw new ResourceNotFoundException("Child",childId);return repo.findByChildId(childId).orElseGet(()->LearnerStreak.create(childId));}
}
