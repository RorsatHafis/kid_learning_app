package com.platform.challenge.web;
import com.platform.challenge.service.ChallengeService; import com.platform.family.service.FamilyAccessGuard; import org.springframework.security.core.annotation.AuthenticationPrincipal; import org.springframework.web.bind.annotation.*; import java.util.*;
@RestController @RequestMapping("/api/v1/children/{childId}/challenges") public class ChildChallengeController {
 private final FamilyAccessGuard family; private final ChallengeService service; public ChildChallengeController(FamilyAccessGuard f,ChallengeService s){family=f;service=s;}
 @GetMapping("/{challengeId}/progress") public ChallengeService.Progress progress(@PathVariable UUID childId,@PathVariable UUID challengeId,@AuthenticationPrincipal UUID id){family.requireChildAccess(id,childId);return service.progress(childId,challengeId);}
}
