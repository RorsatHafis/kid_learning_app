package com.platform.content.studio;

import com.platform.content.studio.StudioDtos.*;
import com.platform.content.validation.ValidationReport;
import com.platform.content.web.ActivityContentDtos.ActivityItemResponse;
import com.platform.content.web.ActivityContentDtos.ActivityVersionDetailResponse;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.PageResponse;
import com.platform.content.entity.QuestionType;
import jakarta.validation.Valid;
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

    public StudioController(StudioService studio, JourneyBuilderService journeys) {
        this.studio = studio;
        this.journeys = journeys;
    }

    // ------------------------------------------------------------------ catalog

    @GetMapping("/subjects")
    public List<SubjectDto> subjects(@AuthenticationPrincipal UUID accountId) {
        return studio.subjects(accountId);
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
    public CatalogDto catalog(@AuthenticationPrincipal UUID accountId, @PathVariable UUID subjectId) {
        return studio.catalog(accountId, subjectId);
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
    public List<CurriculumSummary> curricula(@AuthenticationPrincipal UUID accountId) {
        return studio.curricula(accountId);
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
                                               @RequestParam(defaultValue = "0") int page,
                                               @RequestParam(defaultValue = "10") int size) {
        return studio.lessons(accountId, subjectId, q, new PageResponse.Request(page, size));
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

    // ------------------------------------------------------------------ questions

    @GetMapping("/questions")
    public PageResponse<QuestionSummary> questions(@AuthenticationPrincipal UUID accountId, @RequestParam UUID subjectId,
                                                   @RequestParam(required = false) @Size(max = 200) String q,
                                                   @RequestParam(required = false) QuestionType type,
                                                   @RequestParam(required = false) PublicationStatus status,
                                                   @RequestParam(required = false) UUID skillId,
                                                   @RequestParam(defaultValue = "false") boolean live,
                                                   @RequestParam(defaultValue = "0") int page,
                                                   @RequestParam(defaultValue = "25") int size) {
        return studio.questions(accountId, subjectId, new QuestionFilter(q, type, status, skillId, live),
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
    public ActivityItemResponse previewQuestionVersion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId) {
        return studio.previewQuestionVersion(accountId, versionId);
    }

    @PostMapping("/question-versions/{versionId}/publish")
    public QuestionDetail publishQuestionVersion(@AuthenticationPrincipal UUID accountId, @PathVariable UUID versionId) {
        return studio.publishQuestionVersion(accountId, versionId);
    }

}
