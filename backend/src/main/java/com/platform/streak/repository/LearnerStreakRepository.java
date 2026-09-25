package com.platform.streak.repository;
import com.platform.streak.entity.LearnerStreak; import jakarta.persistence.LockModeType; import org.springframework.data.jpa.repository.JpaRepository; import org.springframework.data.jpa.repository.Lock; import org.springframework.data.jpa.repository.Modifying; import org.springframework.data.jpa.repository.Query; import org.springframework.data.repository.query.Param; import java.util.Optional; import java.util.UUID;
public interface LearnerStreakRepository extends JpaRepository<LearnerStreak,UUID>{ Optional<LearnerStreak> findByChildId(UUID childId);
 /** Battle 5D: create-if-absent decided by PostgreSQL on the child_id unique constraint, so two concurrent first completions for one child can't both insert (the loser used to fail the whole completion). */
 @Modifying(flushAutomatically=true) @Query(value="INSERT INTO learner_streaks (id, child_id) VALUES (:id, :childId) ON CONFLICT (child_id) DO NOTHING",nativeQuery=true) int insertIfAbsent(@Param("id") UUID id,@Param("childId") UUID childId);
 /** Battle 5D: row lock held until the completion commits, so concurrent completions update the streak one at a time instead of failing on its version. */
 @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select s from LearnerStreak s where s.childId = :childId") Optional<LearnerStreak> findByChildIdForUpdate(@Param("childId") UUID childId); }
