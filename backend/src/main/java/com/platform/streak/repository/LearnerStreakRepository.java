package com.platform.streak.repository;
import com.platform.streak.entity.LearnerStreak; import org.springframework.data.jpa.repository.JpaRepository; import java.util.Optional; import java.util.UUID;
public interface LearnerStreakRepository extends JpaRepository<LearnerStreak,UUID>{ Optional<LearnerStreak> findByChildId(UUID childId); }
