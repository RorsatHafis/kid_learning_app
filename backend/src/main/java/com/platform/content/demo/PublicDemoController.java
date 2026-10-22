package com.platform.content.demo;

import com.platform.content.web.ActivityContentDtos.ActivityVersionDetailResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * The public demo (M2). {@code /api/v1/demo/**} needs no account (and stores nothing); the check is rate limited per
 * client. Choosing the demo is for administrators.
 */
@RestController
public class PublicDemoController {

    private final PublicDemoService demo;

    public PublicDemoController(PublicDemoService demo) {
        this.demo = demo;
    }

    public record CheckRequest(@NotNull UUID activityVersionId, @NotNull UUID questionVersionId,
                               @NotNull @Size(max = 2000) String submittedAnswer) {
    }

    public record ChooseRequest(@NotNull @Size(max = 10) List<@NotNull UUID> activityVersionIds) {
    }

    @GetMapping("/api/v1/demo/activities")
    public List<ActivityVersionDetailResponse> activities() {
        return demo.activities();
    }

    @PostMapping("/api/v1/demo/check")
    public PublicDemoService.DemoAnswer check(@Valid @RequestBody CheckRequest request) {
        return demo.check(request.activityVersionId(), request.questionVersionId(), request.submittedAnswer());
    }

    @GetMapping("/api/v1/admin/demo-activities")
    public List<PublicDemoService.Candidate> candidates(@AuthenticationPrincipal UUID adminId,
                                                        @RequestParam(required = false) @Size(max = 100) String q) {
        return demo.candidates(adminId, q);
    }

    @PutMapping("/api/v1/admin/demo-activities")
    public List<UUID> choose(@AuthenticationPrincipal UUID adminId, @Valid @RequestBody ChooseRequest request) {
        return demo.choose(adminId, request.activityVersionIds());
    }

}
