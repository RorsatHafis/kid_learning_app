package com.platform.streak.web;
import com.platform.family.service.FamilyAccessGuard; import com.platform.streak.service.LearnerStreakService; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*; import java.util.UUID;
@RestController @RequestMapping("/api/v1/children/{childId}/streak") public class StreakController {
 private final LearnerStreakService service; private final FamilyAccessGuard guard;
 public StreakController(LearnerStreakService service,FamilyAccessGuard guard){this.service=service;this.guard=guard;}
 @GetMapping public Response get(@PathVariable UUID childId,@AuthenticationPrincipal UUID accountId){guard.requireChildAccess(accountId,childId);var s=service.get(childId);return new Response(childId,s.getCurrentStreak(),s.getLongestStreak(),s.getLastActivityDate());}
 public record Response(UUID childId,int currentStreak,int longestStreak,java.time.LocalDate lastActivityDate){}
}
