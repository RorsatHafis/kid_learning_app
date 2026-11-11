package com.platform.school.attendance;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** M12: connecting a school's student record with a family's child - the family's consent (see {@link StudentLinkService}). */
@RestController
public class StudentLinkController {

    private final StudentLinkService links;

    public StudentLinkController(StudentLinkService links) {
        this.links = links;
    }

    public record AskRequest(@NotNull UUID childId) {
    }

    @GetMapping("/api/v1/schools/classes/{classId}/student-links")
    public StudentLinkService.ClassLinks forClass(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId) {
        return links.forClass(accountId, classId);
    }

    @PostMapping("/api/v1/schools/classes/{classId}/students/{studentId}/link")
    public StudentLinkService.ClassLinks ask(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId, @PathVariable UUID studentId,
                                             @Valid @RequestBody AskRequest request) {
        return links.ask(accountId, classId, studentId, request.childId());
    }

    @DeleteMapping("/api/v1/schools/classes/{classId}/students/{studentId}/link")
    public StudentLinkService.ClassLinks end(@AuthenticationPrincipal UUID accountId, @PathVariable UUID classId, @PathVariable UUID studentId) {
        return links.end(accountId, classId, studentId);
    }

    @GetMapping("/api/v1/children/{childId}/school-links")
    public List<StudentLinkService.FamilyLink> forChild(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId) {
        return links.forChild(accountId, childId);
    }

    @PostMapping("/api/v1/children/{childId}/school-links/{linkId}/connect")
    public List<StudentLinkService.FamilyLink> connect(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId, @PathVariable UUID linkId) {
        return links.decide(accountId, childId, linkId, true);
    }

    @PostMapping("/api/v1/children/{childId}/school-links/{linkId}/decline")
    public List<StudentLinkService.FamilyLink> decline(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId, @PathVariable UUID linkId) {
        return links.decide(accountId, childId, linkId, false);
    }

    @DeleteMapping("/api/v1/children/{childId}/school-links/{linkId}")
    public List<StudentLinkService.FamilyLink> disconnect(@AuthenticationPrincipal UUID accountId, @PathVariable UUID childId,
                                                          @PathVariable UUID linkId) {
        return links.disconnect(accountId, childId, linkId);
    }
}
