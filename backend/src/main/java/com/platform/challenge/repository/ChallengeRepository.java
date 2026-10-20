package com.platform.challenge.repository; import com.platform.challenge.entity.Challenge; import org.springframework.data.jpa.repository.JpaRepository; import java.util.*;
/**
 * Persistence for challenges, found by owner and status.
 */
public interface ChallengeRepository extends JpaRepository<Challenge,UUID>{List<Challenge> findByOwnerAccountIdAndStatus(UUID owner,String status);}
