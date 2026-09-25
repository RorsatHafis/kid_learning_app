package com.platform.content.web;

import com.platform.common.entity.CatalogStatus;
import com.platform.common.entity.PublicationStatus;
import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionType;
import com.platform.content.service.ActivityService;
import com.platform.content.service.ContentAccessGuard;
import com.platform.content.service.LessonService;
import com.platform.content.service.QuestionService;
import com.platform.content.repository.LessonRepository;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.service.CurriculumService;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import com.platform.curriculum.repository.SubjectRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/teacher")
public class TeacherAuthoringController {
    private final AccountRepository accounts;
    private final SubjectRepository subjects;
    private final AgeHubRepository ageHubs;
    private final LearningObjectiveRepository objectives;
    private final SkillRepository skills;
    private final LessonService lessons;
    private final ActivityService activities;
    private final QuestionService questions;
    private final CurriculumService curricula;
    private final ContentAccessGuard contentGuard;
    private final LessonRepository lessonRepository;

    public TeacherAuthoringController(AccountRepository accounts, SubjectRepository subjects,
                                      AgeHubRepository ageHubs, LearningObjectiveRepository objectives,
                                      SkillRepository skills, LessonService lessons, ActivityService activities,
                                      QuestionService questions, CurriculumService curricula,
                                      ContentAccessGuard contentGuard, LessonRepository lessonRepository) {
        this.accounts = accounts;
        this.subjects = subjects;
        this.ageHubs = ageHubs;
        this.objectives = objectives;
        this.skills = skills;
        this.lessons = lessons;
        this.activities = activities;
        this.questions = questions;
        this.curricula = curricula;
        this.contentGuard = contentGuard;
        this.lessonRepository = lessonRepository;
    }

    private void staff(UUID id) {
        var account = accounts.findById(id).orElseThrow();
        if (account.getPlatformRole() != PlatformRole.TEACHER
                && account.getPlatformRole() != PlatformRole.PRINCIPAL
                && account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Staff access required");
        }
    }

    private void curriculumOwner(UUID id, UUID owner) {
        if (!id.equals(owner)
                && accounts.findById(id).orElseThrow().getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("You do not own this curriculum");
        }
    }

    @GetMapping("/subjects")
    public List<SubjectResponse> subjects(@AuthenticationPrincipal UUID id) {
        staff(id);
        return subjects.findAll().stream()
                .map(x -> new SubjectResponse(x.getId(), x.getCode(), x.getName())).toList();
    }

    @GetMapping("/age-hubs")
    public List<AgeHubResponse> ageHubs(@AuthenticationPrincipal UUID id) {
        staff(id);
        return ageHubs.findAllByOrderByDisplayOrderAsc().stream()
                .map(x -> new AgeHubResponse(x.getId(), x.getCode(), x.getName())).toList();
    }

    @GetMapping("/subjects/{subjectId}/skills")
    public List<SkillLookupResponse> skills(@PathVariable UUID subjectId, @AuthenticationPrincipal UUID id) {
        staff(id);
        return skills.findBySubjectIdAndStatus(subjectId, CatalogStatus.ACTIVE).stream()
                .map(x -> new SkillLookupResponse(x.getId(), x.getCode(), x.getName())).toList();
    }

    @GetMapping("/subjects/{subjectId}/objectives")
    public List<ObjectiveLookupResponse> objectives(@PathVariable UUID subjectId, @AuthenticationPrincipal UUID id) {
        staff(id);
        return objectives.findBySubjectId(subjectId).stream()
                .map(x -> new ObjectiveLookupResponse(x.getId(), x.getCode(), x.getTitle())).toList();
    }

    @GetMapping("/lessons")
    public List<LessonResponse> listLessons(@AuthenticationPrincipal UUID id) {
        contentGuard.requireAuthor(id);
        return lessonsOwned(id).stream()
                .map(LessonResponse::of).toList();
    }

    private List<com.platform.content.entity.Lesson> lessonsOwned(UUID id) {
        return lessonRepository.findByOwnerAccountId(id);
    }

    @PostMapping("/activities/{activityId}/skills/{skillId}")
    public void tagActivity(@PathVariable UUID activityId, @PathVariable UUID skillId,
                            @AuthenticationPrincipal UUID id) {
        contentGuard.requireActivityWrite(id, activityId);
        activities.tagSkill(activityId, skillId);
    }

    @PostMapping("/lessons")
    public LessonResponse lesson(@Valid @RequestBody LessonRequest r, @AuthenticationPrincipal UUID id) {
        contentGuard.requireAuthor(id);
        return LessonResponse.of(lessons.createOwnedLesson(r.subjectId(), r.title(), id));
    }

    @PostMapping("/lessons/{lessonId}/versions")
    public VersionResponse lessonVersion(@PathVariable UUID lessonId,
                                         @Valid @RequestBody LessonVersionRequest r,
                                         @AuthenticationPrincipal UUID id) {
        contentGuard.requireLessonWrite(id, lessonId);
        return VersionResponse.of(lessons.createDraftVersion(lessonId, r.body()));
    }

    @PutMapping("/lesson-versions/{versionId}")
    public void updateLessonDraft(@PathVariable UUID versionId,
                                  @Valid @RequestBody LessonVersionRequest r,
                                  @AuthenticationPrincipal UUID id) {
        var version = lessons.getVersion(versionId);
        contentGuard.requireLessonWrite(id, version.getLessonId());
        lessons.updateDraftBody(versionId, r.body());
    }

    @PostMapping("/lessons/versions/{versionId}/publish")
    public VersionResponse publishLesson(@PathVariable UUID versionId, @AuthenticationPrincipal UUID id) {
        var version = lessons.getVersion(versionId);
        contentGuard.requireLessonWrite(id, version.getLessonId());
        return VersionResponse.of(lessons.publish(versionId));
    }

    @PostMapping("/lessons/{lessonId}/activities")
    public ActivityResponse activity(@PathVariable UUID lessonId,
                                     @Valid @RequestBody ActivityRequest r,
                                     @AuthenticationPrincipal UUID id) {
        var lesson = contentGuard.requireLessonWrite(id, lessonId);
        return ActivityResponse.of(activities.createActivity(lesson.getSubjectId(), lessonId,
                r.activityType(), r.title()));
    }

    @PostMapping("/activities/{activityId}/versions")
    public VersionResponse activityVersion(@PathVariable UUID activityId,
                                           @Valid @RequestBody ActivityVersionRequest r,
                                           @AuthenticationPrincipal UUID id) {
        contentGuard.requireActivityWrite(id, activityId);
        return VersionResponse.of(activities.createDraftVersion(activityId, r.instructions(),
                r.difficultyLevel(), r.estimatedDurationSeconds()));
    }

    @PostMapping("/activities/versions/{versionId}/items")
    public ItemResponse item(@PathVariable UUID versionId, @Valid @RequestBody ItemRequest r,
                             @AuthenticationPrincipal UUID id) {
        var version = activities.getVersion(versionId);
        contentGuard.requireActivityWrite(id, version.getActivityId());
        if (version.getStatus() != PublicationStatus.DRAFT) {
            throw new IllegalStateException("Items may only be added to a DRAFT activity version");
        }
        return ItemResponse.of(activities.addItem(versionId, r.questionVersionId(),
                r.sequenceOrder(), r.points()));
    }

    @PostMapping("/activities/versions/{versionId}/publish")
    public VersionResponse publishActivity(@PathVariable UUID versionId, @AuthenticationPrincipal UUID id) {
        var version = activities.getVersion(versionId);
        contentGuard.requireActivityWrite(id, version.getActivityId());
        return VersionResponse.of(activities.publish(versionId));
    }

    @PostMapping("/questions")
    public QuestionResponse question(@Valid @RequestBody QuestionRequest r, @AuthenticationPrincipal UUID id) {
        contentGuard.requireAuthor(id);
        return QuestionResponse.of(questions.createOwnedQuestion(r.subjectId(), r.questionType(), id));
    }

    @PostMapping("/questions/{questionId}/versions")
    public VersionResponse questionVersion(@PathVariable UUID questionId,
                                            @Valid @RequestBody QuestionVersionRequest r,
                                            @AuthenticationPrincipal UUID id) {
        contentGuard.requireQuestionWrite(id, questionId);
        return VersionResponse.of(questions.createDraftVersion(questionId, r.prompt(),
                r.correctAnswer(), r.difficultyLevel()));
    }

    @PostMapping("/questions/versions/{versionId}/options")
    public OptionResponse option(@PathVariable UUID versionId, @Valid @RequestBody OptionRequest r,
                                 @AuthenticationPrincipal UUID id) {
        var version = questions.getVersion(versionId);
        contentGuard.requireQuestionWrite(id, version.getQuestionId());
        if (version.getStatus() != PublicationStatus.DRAFT) {
            throw new IllegalStateException("Options may only be added to a DRAFT question version");
        }
        return OptionResponse.of(questions.addOption(versionId, r.label(), r.correct(), r.displayOrder()));
    }

    @PostMapping("/questions/versions/{versionId}/publish")
    public VersionResponse publishQuestion(@PathVariable UUID versionId, @AuthenticationPrincipal UUID id) {
        var version = questions.getVersion(versionId);
        contentGuard.requireQuestionWrite(id, version.getQuestionId());
        return VersionResponse.of(questions.publish(versionId));
    }

    @PostMapping("/curricula")
    public CurriculumResponse curriculum(@Valid @RequestBody CurriculumRequest r,
                                          @AuthenticationPrincipal UUID id) {
        contentGuard.requireAuthor(id);
        return CurriculumResponse.of(curricula.createOwnedCurriculum(r.subjectId(), r.ageHubId(), r.name(), id));
    }

    @PostMapping("/curricula/{curriculumId}/versions")
    public VersionResponse curriculumVersion(@PathVariable UUID curriculumId, @AuthenticationPrincipal UUID id) {
        staff(id);
        Curriculum c = curricula.getCurriculum(curriculumId);
        curriculumOwner(id, c.getOwnerAccountId());
        return VersionResponse.of(curricula.createDraftVersion(curriculumId));
    }

    @PostMapping("/curricula/versions/{versionId}/objectives")
    public ObjectiveResponse objective(@PathVariable UUID versionId,
                                       @Valid @RequestBody ObjectiveRequest r,
                                       @AuthenticationPrincipal UUID id) {
        staff(id);
        var version = curricula.getVersion(versionId);
        curriculumOwner(id, curricula.getCurriculum(version.getCurriculumId()).getOwnerAccountId());
        return ObjectiveResponse.of(curricula.addObjective(versionId, r.learningObjectiveId(), r.displayOrder()));
    }

    @PostMapping("/curricula/versions/{versionId}/publish")
    public VersionResponse publishCurriculum(@PathVariable UUID versionId, @AuthenticationPrincipal UUID id) {
        staff(id);
        var version = curricula.getVersion(versionId);
        curriculumOwner(id, curricula.getCurriculum(version.getCurriculumId()).getOwnerAccountId());
        return VersionResponse.of(curricula.publish(versionId));
    }

    public record SkillLookupResponse(UUID id, String code, String name) {}
    public record SubjectResponse(UUID id, String code, String name) {}
    public record AgeHubResponse(UUID id, String code, String name) {}
    public record ObjectiveLookupResponse(UUID id, String code, String title) {}
    public record LessonRequest(@NotNull UUID subjectId, @NotBlank String title) {}
    public record LessonVersionRequest(@NotBlank String body) {}
    public record ActivityRequest(@NotBlank String activityType, @NotBlank String title) {}
    public record ActivityVersionRequest(String instructions, @Min(1) @Max(10) Integer difficultyLevel,
                                         @Positive Integer estimatedDurationSeconds) {}
    public record ItemRequest(@NotNull UUID questionVersionId, @Min(0) int sequenceOrder,
                              @DecimalMin("0.01") BigDecimal points) {}
    public record QuestionRequest(@NotNull UUID subjectId, @NotNull QuestionType questionType) {}
    public record QuestionVersionRequest(@NotBlank String prompt, String correctAnswer,
                                         @Min(1) @Max(10) Integer difficultyLevel) {}
    public record OptionRequest(@NotBlank String label, boolean correct, @Min(0) int displayOrder) {}
    public record CurriculumRequest(@NotNull UUID subjectId, @NotNull UUID ageHubId, @NotBlank String name) {}
    public record ObjectiveRequest(@NotNull UUID learningObjectiveId, @Min(0) int displayOrder) {}

    public record LessonResponse(UUID id, String title, UUID ownerAccountId) {
        static LessonResponse of(com.platform.content.entity.Lesson x) {
            return new LessonResponse(x.getId(), x.getTitle(), x.getOwnerAccountId());
        }
    }
    public record ActivityResponse(UUID id, String title, String activityType) {
        static ActivityResponse of(Activity x) {
            return new ActivityResponse(x.getId(), x.getTitle(), x.getActivityType());
        }
    }
    public record QuestionResponse(UUID id, QuestionType questionType, UUID ownerAccountId) {
        static QuestionResponse of(Question x) {
            return new QuestionResponse(x.getId(), x.getQuestionType(), x.getOwnerAccountId());
        }
    }
    public record VersionResponse(UUID id, int versionNumber, String status) {
        static VersionResponse of(com.platform.common.entity.PublishableVersion x) {
            return new VersionResponse(x.getId(), x.getVersionNumber(), x.getStatus().name());
        }
    }
    public record ItemResponse(UUID id, UUID activityVersionId, UUID questionVersionId, int sequenceOrder) {
        static ItemResponse of(ActivityItem x) {
            return new ItemResponse(x.getId(), x.getActivityVersionId(), x.getQuestionVersionId(), x.getSequenceOrder());
        }
    }
    public record OptionResponse(UUID id, String label, boolean correct, int displayOrder) {
        static OptionResponse of(com.platform.content.entity.QuestionOption x) {
            return new OptionResponse(x.getId(), x.getLabel(), x.isCorrect(), x.getDisplayOrder());
        }
    }
    public record CurriculumResponse(UUID id, String name, UUID ownerAccountId) {
        static CurriculumResponse of(Curriculum x) {
            return new CurriculumResponse(x.getId(), x.getName(), x.getOwnerAccountId());
        }
    }
    public record ObjectiveResponse(UUID id, UUID curriculumVersionId, UUID learningObjectiveId, int displayOrder) {
        static ObjectiveResponse of(com.platform.curriculum.entity.CurriculumObjective x) {
            return new ObjectiveResponse(x.getId(), x.getCurriculumVersionId(), x.getLearningObjectiveId(), x.getDisplayOrder());
        }
    }
}
