package com.platform.content.studio;

import com.platform.content.studio.StudioDtos.*;
import com.platform.content.validation.ValidationReport;
import com.platform.content.web.ActivityContentDtos.ActivityItemResponse;
import com.platform.content.web.ActivityContentDtos.ActivityVersionDetailResponse;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.PageResponse;
import com.platform.content.entity.QuestionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Content Studio API. Every call is authorized in {@link StudioService} against the
 * signed-in account: staff only, own content only (ADMIN: platform content), catalog
 * changes ADMIN only. Publishing refuses drafts with blocking validation issues (409
 * with the report).
 */
@RestController
@RequestMapping("/api/v1/studio")
public class StudioController {

    private final StudioService studio;
    private final JourneyBuilderService journeys;
    private final ContentLifecycleService lifecycle;

    public StudioController(StudioService studio, JourneyBuilderService journeys, ContentLifecycleService lifecycle) {
        this.studio = studio;
        this.journeys = journeys;
        this.lifecycle = lifecycle;
    }

    // ------------------------------------------------------------------ catalog

    @GetMapping("/subjects")
    public List<SubjectDto> subjects(@AuthenticationPrincipal UUID accountId, @RequestParam(defaultValue = "false") boolean archived) {
        return studio.subjects(accountId, archived);
    }

    @PostMapping("/subjects")
    @ResponseStatus(HttpStatus.CREATED)
    public SubjectDto createSubject(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody CreateSubjectRequest request) {
        return studio.createSubject(accountId, request);
    }

    @GetMapping("/age-hubs")
    public List<AgeHubDto> ageHubs(@AuthenticationPrincipal UUID accountId) {
        return studio.ageHubs(accountId);
    }

    @GetMapping("/subjects/{subjectId}/catalog")
    public CatalogDto catalog(@AuthenticationPrincipal UUID accountId, @PathVariable UUID subjectId,
                              @RequestParam(defaultValue = "false") boolean archived) {
        return studio.catalog(accountId, subjectId, archived);
    }

    @PostMapping("/subjects/{subjectId}/skills")
    @ResponseStatus(HttpStatus.CREATED)
    public SkillDto createSkill(@AuthenticationPrincipal UUID accountId, @PathVariable UUID subjectId,
                                @Valid @RequestBody CreateSkillRequest request) {
        return studio.createSkill(accountId, subjectId, request);
    }

    @PostMapping("/subjects/{subjectId}/objectives")
    @ResponseStatus(HttpStatus.CREATED)
    public ObjectiveDto createObjective(@AuthenticationPrincipal UUID accountId, @PathVariable UUID subjectId,
                                        @Valid @RequestBody CreateObjectiveRequest request) {
        return studio.createObjective(accountId, subjectId, request);
    }

    @PostMapping("/objectives/{objectiveId}/skills/{skillId}")
    public ObjectiveDto linkObjectiveSkill(@AuthenticationPrincipal UUID accountId, @PathVariable UUID objectiveId,
                                           @PathVariable UUID skillId) {
        return studio.linkObjectiveSkill(accountId, objectiveId, skillId);
    }

    @PostMapping("/skills/{skillId}/prerequisites/{prerequisiteSkillId}")
    public SkillDto addPrerequisite(@AuthenticationPrincipal UUID accountId, @PathVariable UUID skillId,
                                    @PathVariable UUID prerequisiteSkillId) {
        return studio.addPrerequisite(accountId, skillId, prerequisiteSkillId);
    }

    // ------------------------------------------------------------------ curricula

    @GetMapping("/curricula")
    public List<CurriculumSummary> curricula(@AuthenticationPrincipal UUID accountId,
                                             @RequestParam(defaultValue = "false") boolean archived) {
        return studio.curricula(accountId, archived);
    }

    @PostMapping("/curricula")
    @ResponseStatus(HttpStatus.CREATED)
    public CurriculumDetail createCurriculum(@AuthenticationPrincipal UUID accountId,
                                             @Valid @RequestBody CreateCurriculumRequest request) {
        return studio.createCurriculum(accountId, request);
    }

    @GetMapping("/curricula/{curriculumId}")
    public CurriculumDetail curriculum(@AuthenticationPrincipal UUID accountId, @PathVariable UUID curriculumId) {
        return studio.curriculum(accountId, curriculumId);
    }

    @PostMapping("/curricula/{curriculumId}/draft")
    public CurriculumDetail openCurriculumDraft(@AuthenticationPrincipal UUID accountId, @PathVariable UUID curriculumId) {
        return studio.openCurriculumDraft(accountId, curriculumId);
    }

    @PutMapping("/curriculum-versions/{versionId}/objectives")
    public CurriculumDetail replaceCurriculumObjectives(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId,
                                                        @Valid @RequestBody ReplaceObjectivesRequest request) {
        return studio.replaceCurriculumObjectives(accountId, versionId, request.objectiveIds());
    }

    @GetMapping("/curriculum-versions/{versionId}/validation")
    public ValidationReport validateCurriculumVersion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId) {
        return studio.validateCurriculumVersion(accountId, versionId);
    }

    /** The version as the journey a child will meet: chapters, their steps, what backs each skill, and the gaps. */
    @GetMapping("/curriculum-versions/{versionId}/journey")
    public JourneyBuilderService.JourneyMap journey(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId) {
        return journeys.journey(accountId, versionId);
    }

    /** The same, for chapters in an order the author hasn't saved yet: nothing is written. */
    @PostMapping("/curriculum-versions/{versionId}/journey/preview")
    public JourneyBuilderService.JourneyMap previewJourney(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId,
                                                           @Valid @RequestBody ReplaceObjectivesRequest request) {
        return journeys.preview(accountId, versionId, request.objectiveIds());
    }

    @PostMapping("/curriculum-versions/{versionId}/publish")
    public CurriculumDetail publishCurriculumVersion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId) {
        return studio.publishCurriculumVersion(accountId, versionId);
    }

    // ------------------------------------------------------------------ lessons & activities

    @GetMapping("/lessons")
    public PageResponse<LessonSummary> lessons(@AuthenticationPrincipal UUID accountId,
                                               @RequestParam(required = false) UUID subjectId,
                                               @RequestParam(required = false) @Size(max = 200) String q,
                                               @RequestParam(defaultValue = "false") boolean archived,
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return studio.lessons(accountId, subjectId, q, archived, new PageResponse.Request(page, size));
    }

    @PostMapping("/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    public LessonSummary createLesson(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody CreateLessonRequest request) {
        return studio.createLesson(accountId, request);
    }

    @PostMapping("/lessons/{lessonId}/activities")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityDetail createActivity(@AuthenticationPrincipal UUID accountId, @PathVariable UUID lessonId,
                                         @Valid @RequestBody CreateActivityRequest request) {
        return studio.createActivity(accountId, lessonId, request);
    }

    @GetMapping("/activities/{activityId}")
    public ActivityDetail activity(@AuthenticationPrincipal UUID accountId, @PathVariable UUID activityId) {
        return studio.activity(accountId, activityId);
    }

    public record DuplicateActivityRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 200) String title) {
    }

    /** A new activity in the same lesson starting as a copy of this one (its latest version), as a draft. */
    @PostMapping("/activities/{activityId}/duplicate")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public ActivityDetail duplicateActivity(@AuthenticationPrincipal UUID accountId, @PathVariable UUID activityId,
                                            @jakarta.validation.Valid @org.springframework.web.bind.annotation.RequestBody DuplicateActivityRequest request) {
        return studio.duplicateActivity(accountId, activityId, request.title());
    }

    @PostMapping("/activities/{activityId}/draft")
    public ActivityDetail openActivityDraft(@AuthenticationPrincipal UUID accountId, @PathVariable UUID activityId) {
        return studio.openActivityDraft(accountId, activityId);
    }

    @PostMapping("/activities/{activityId}/skills/{skillId}")
    public ActivityDetail tagActivitySkill(@AuthenticationPrincipal UUID accountId, @PathVariable UUID activityId,
                                           @PathVariable UUID skillId) {
        return studio.tagActivitySkill(accountId, activityId, skillId);
    }

    @DeleteMapping("/activities/{activityId}/skills/{skillId}")
    public ActivityDetail untagActivitySkill(@AuthenticationPrincipal UUID accountId, @PathVariable UUID activityId,
                                             @PathVariable UUID skillId) {
        return studio.untagActivitySkill(accountId, activityId, skillId);
    }

    @PutMapping("/activity-versions/{versionId}")
    public ActivityDetail reviseActivityDraft(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId,
                                              @Valid @RequestBody ReviseActivityDraftRequest request) {
        return studio.reviseActivityDraft(accountId, versionId, request);
    }

    /** What the activity teaches before it asks (its draft). */
    @PutMapping("/activity-versions/{versionId}/introduction")
    public ActivityDetail setActivityIntroduction(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId,
                                                  @Valid @RequestBody StudioDtos.IntroductionRequest request) {
        return studio.setActivityIntroduction(accountId, versionId, request);
    }

    @PutMapping("/activity-versions/{versionId}/items")
    public ActivityDetail replaceActivityItems(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId,
                                               @Valid @RequestBody ReplaceItemsRequest request) {
        return studio.replaceActivityItems(accountId, versionId, request.items());
    }

    @GetMapping("/activity-versions/{versionId}/validation")
    public ValidationReport validateActivityVersion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId) {
        return studio.validateActivityVersion(accountId, versionId);
    }

    @GetMapping("/activity-versions/{versionId}/preview")
    public ActivityVersionDetailResponse previewActivityVersion(@AuthenticationPrincipal UUID accountId,
                                                                @PathVariable UUID versionId) {
        return studio.previewActivityVersion(accountId, versionId);
    }

    @PostMapping("/activity-versions/{versionId}/publish")
    public ActivityDetail publishActivityVersion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId) {
        return studio.publishActivityVersion(accountId, versionId);
    }

    /** Y42: a new question written in the activity, added at its end (subject and default skills: the activity's). */
    @PostMapping("/activity-versions/{versionId}/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public ActivityDetail addNewQuestion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId,
                                         @Valid @RequestBody CreateQuestionRequest request) {
        return studio.addNewQuestion(accountId, versionId, request);
    }

    /** Y42: the activity and its draft questions, published together or not at all. */
    @PostMapping("/activity-versions/{versionId}/publish-with-questions")
    public ActivityDetail publishActivityWithQuestions(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId) {
        return studio.publishActivityWithQuestions(accountId, versionId);
    }

    // ------------------------------------------------------------------ questions

    @GetMapping("/questions")
    public PageResponse<QuestionSummary> questions(@AuthenticationPrincipal UUID accountId, @RequestParam UUID subjectId,
                                                   @RequestParam(required = false) @Size(max = 200) String q,
                                                   @RequestParam(required = false) QuestionType type,
                                                   @RequestParam(required = false) PublicationStatus status,
                                                   @RequestParam(required = false) UUID skillId,
                                                   @RequestParam(defaultValue = "false") boolean live,
                                                   @RequestParam(defaultValue = "false") boolean archived,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size) {
        return studio.questions(accountId, subjectId, new QuestionFilter(q, type, status, skillId, live, archived),
                new PageResponse.Request(page, size));
    }

    @PostMapping("/questions")
    @ResponseStatus(HttpStatus.CREATED)
    public QuestionDetail createQuestion(@AuthenticationPrincipal UUID accountId, @Valid @RequestBody CreateQuestionRequest request) {
        return studio.createQuestion(accountId, request);
    }

    @GetMapping("/questions/{questionId}")
    public QuestionDetail question(@AuthenticationPrincipal UUID accountId, @PathVariable UUID questionId) {
        return studio.question(accountId, questionId);
    }

    /** A new question starting as a copy of this one (its latest version), as a draft of the same author's. */
    @PostMapping("/questions/{questionId}/duplicate")
    @org.springframework.web.bind.annotation.ResponseStatus(org.springframework.http.HttpStatus.CREATED)
    public QuestionDetail duplicateQuestion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID questionId) {
        return studio.duplicateQuestion(accountId, questionId);
    }

    @PostMapping("/questions/{questionId}/draft")
    public QuestionDetail openQuestionDraft(@AuthenticationPrincipal UUID accountId, @PathVariable UUID questionId) {
        return studio.openQuestionDraft(accountId, questionId);
    }

    @PostMapping("/questions/{questionId}/skills/{skillId}")
    public QuestionDetail tagQuestionSkill(@AuthenticationPrincipal UUID accountId, @PathVariable UUID questionId,
                                           @PathVariable UUID skillId) {
        return studio.tagQuestionSkill(accountId, questionId, skillId);
    }

    @DeleteMapping("/questions/{questionId}/skills/{skillId}")
    public QuestionDetail untagQuestionSkill(@AuthenticationPrincipal UUID accountId, @PathVariable UUID questionId,
                                             @PathVariable UUID skillId) {
        return studio.untagQuestionSkill(accountId, questionId, skillId);
    }

    @PutMapping("/question-versions/{versionId}")
    public QuestionDetail reviseQuestionDraft(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId,
                                              @Valid @RequestBody ReviseQuestionDraftRequest request) {
        return studio.reviseQuestionDraft(accountId, versionId, request);
    }

    @GetMapping("/question-versions/{versionId}/validation")
    public ValidationReport validateQuestionVersion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId) {
        return studio.validateQuestionVersion(accountId, versionId);
    }

    @GetMapping("/question-versions/{versionId}/preview")
    public ActivityItemResponse previewQuestionVersion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId,
                                                       @org.springframework.web.bind.annotation.RequestParam(required = false)
                                                       @jakarta.validation.constraints.Pattern(regexp = "en|km") String language) {
        return studio.previewQuestionVersion(accountId, versionId, language);
    }

    @PostMapping("/question-versions/{versionId}/publish")
    public QuestionDetail publishQuestionVersion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId) {
        return studio.publishQuestionVersion(accountId, versionId);
    }

    // ================================================================== X14: edit details, archive, restore

    /** New details for something the Studio manages: its name or title, and (catalog) a description. */
    public record DetailsRequest(@NotBlank @Size(max = 200) String name, @Size(max = 2000) String description) {
    }

    @PutMapping("/subjects/{subjectId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSubject(@AuthenticationPrincipal UUID accountId, @PathVariable UUID subjectId, @Valid @RequestBody DetailsRequest request) {
        lifecycle.updateSubject(accountId, subjectId, request.name(), request.description());
    }

    @PostMapping("/subjects/{subjectId}/archive")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveSubject(@AuthenticationPrincipal UUID accountId, @PathVariable UUID subjectId) {
        lifecycle.archiveSubject(accountId, subjectId);
    }

    @PostMapping("/subjects/{subjectId}/restore")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restoreSubject(@AuthenticationPrincipal UUID accountId, @PathVariable UUID subjectId) {
        lifecycle.restoreSubject(accountId, subjectId);
    }

    @PutMapping("/skills/{skillId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateSkill(@AuthenticationPrincipal UUID accountId, @PathVariable UUID skillId, @Valid @RequestBody DetailsRequest request) {
        lifecycle.updateSkill(accountId, skillId, request.name(), request.description());
    }

    @PostMapping("/skills/{skillId}/{action:archive|restore}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveSkill(@AuthenticationPrincipal UUID accountId, @PathVariable UUID skillId, @PathVariable String action) {
        lifecycle.archiveSkill(accountId, skillId, "archive".equals(action));
    }

    @PutMapping("/objectives/{objectiveId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateObjective(@AuthenticationPrincipal UUID accountId, @PathVariable UUID objectiveId,
                                @Valid @RequestBody DetailsRequest request) {
        lifecycle.updateObjective(accountId, objectiveId, request.name(), request.description());
    }

    @PostMapping("/objectives/{objectiveId}/{action:archive|restore}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveObjective(@AuthenticationPrincipal UUID accountId, @PathVariable UUID objectiveId, @PathVariable String action) {
        lifecycle.archiveObjective(accountId, objectiveId, "archive".equals(action));
    }

    @PutMapping("/curricula/{curriculumId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void renameCurriculum(@AuthenticationPrincipal UUID accountId, @PathVariable UUID curriculumId,
                                 @Valid @RequestBody DetailsRequest request) {
        lifecycle.renameCurriculum(accountId, curriculumId, request.name());
    }

    @PostMapping("/curricula/{curriculumId}/{action:archive|restore}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveCurriculum(@AuthenticationPrincipal UUID accountId, @PathVariable UUID curriculumId, @PathVariable String action) {
        lifecycle.archiveCurriculum(accountId, curriculumId, "archive".equals(action));
    }

    @PutMapping("/lessons/{lessonId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retitleLesson(@AuthenticationPrincipal UUID accountId, @PathVariable UUID lessonId, @Valid @RequestBody DetailsRequest request) {
        lifecycle.retitleLesson(accountId, lessonId, request.name());
    }

    @PostMapping("/lessons/{lessonId}/{action:archive|restore}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveLesson(@AuthenticationPrincipal UUID accountId, @PathVariable UUID lessonId, @PathVariable String action) {
        lifecycle.archiveLesson(accountId, lessonId, "archive".equals(action));
    }

    @PutMapping("/activities/{activityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void retitleActivity(@AuthenticationPrincipal UUID accountId, @PathVariable UUID activityId,
                                @Valid @RequestBody DetailsRequest request) {
        lifecycle.retitleActivity(accountId, activityId, request.name());
    }

    @PostMapping("/activities/{activityId}/{action:archive|restore}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveActivity(@AuthenticationPrincipal UUID accountId, @PathVariable UUID activityId, @PathVariable String action) {
        lifecycle.archiveActivity(accountId, activityId, "archive".equals(action));
    }

    @PostMapping("/questions/{questionId}/{action:archive|restore}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archiveQuestion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID questionId, @PathVariable String action) {
        lifecycle.archiveQuestion(accountId, questionId, "archive".equals(action));
    }

}
