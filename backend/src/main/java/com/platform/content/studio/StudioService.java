package com.platform.content.studio;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.entity.CatalogStatus;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.PageResponse;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.common.web.SearchText;
import com.platform.content.entity.Activity;
import com.platform.content.entity.AssetType;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivityVersionSkill;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.Lesson;
import com.platform.content.entity.Question;
import com.platform.content.entity.MatchSide;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.SortRole;
import com.platform.content.entity.QuestionVersionSkill;
import com.platform.content.entity.QuestionType;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.media.MediaService;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.LessonRepository;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.repository.QuestionVersionSkillRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.content.service.ActivityService;
import com.platform.content.service.ContentAccessGuard;
import com.platform.content.service.LessonService;
import com.platform.content.service.QuestionService;
import com.platform.content.studio.StudioDtos.*;
import com.platform.content.validation.ContentValidator;
import com.platform.content.validation.ValidationReport;
import com.platform.content.web.ActivityContentDtos.ActivityItemResponse;
import com.platform.content.web.ActivityContentDtos.ActivityVersionDetailResponse;
import com.platform.content.web.ActivityContentDtos;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.CurriculumObjective;
import com.platform.curriculum.entity.CurriculumVersion;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.AgeHubRepository;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.curriculum.service.CurriculumService;
import com.platform.curriculum.service.LearningObjectiveService;
import com.platform.curriculum.service.SubjectService;
import com.platform.identity.entity.Account;
import com.platform.knowledge.entity.LearningObjectiveSkill;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.entity.SkillPrerequisite;
import com.platform.knowledge.repository.LearningObjectiveSkillRepository;
import com.platform.knowledge.repository.SkillPrerequisiteRepository;
import com.platform.knowledge.repository.SkillRepository;
import com.platform.knowledge.service.LearningObjectiveSkillService;
import com.platform.knowledge.service.SkillPrerequisiteService;
import com.platform.knowledge.service.SkillService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * The Content Studio (directive §18): what an author reads and changes, always within
 * what that author may edit - ADMIN works on platform content, anyone else on their
 * own ({@link ContentAccessGuard}). Publishing itself stays in the domain services,
 * which refuse anything {@link ContentValidator} reports as blocking.
 */
@Service
public class StudioService {

    private static final String PRACTICE = "PRACTICE";

    private final ContentAccessGuard guard;
    private final AuditService audit;
    private final ContentValidator validator;
    private final SubjectRepository subjects;
    private final SubjectService subjectService;
    private final AgeHubRepository ageHubs;
    private final SkillRepository skills;
    private final SkillService skillService;
    private final SkillPrerequisiteRepository prerequisites;
    private final SkillPrerequisiteService prerequisiteService;
    private final LearningObjectiveRepository objectives;
    private final LearningObjectiveService objectiveService;
    private final LearningObjectiveSkillRepository objectiveSkills;
    private final LearningObjectiveSkillService objectiveSkillService;
    private final CurriculumRepository curricula;
    private final CurriculumService curriculumService;
    private final LessonRepository lessons;
    private final LessonService lessonService;
    private final ActivityRepository activities;
    private final ActivityService activityService;
    private final ActivityVersionSkillRepository activitySkills;
    private final ActivityItemRepository activityItems;
    private final QuestionRepository questions;
    private final QuestionService questionService;
    private final QuestionVersionSkillRepository questionSkills;
    private final QuestionVersionRepository questionVersions;
    private final ActivityVersionRepository activityVersions;
    private final MediaService media;
    private final com.platform.content.review.ContentReviewService reviews;
    private final org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc;

    public StudioService(ContentAccessGuard guard, AuditService audit, ContentValidator validator,
                         SubjectRepository subjects, SubjectService subjectService, AgeHubRepository ageHubs,
                         SkillRepository skills, SkillService skillService, SkillPrerequisiteRepository prerequisites,
                         SkillPrerequisiteService prerequisiteService, LearningObjectiveRepository objectives,
                         LearningObjectiveService objectiveService, LearningObjectiveSkillRepository objectiveSkills,
                         LearningObjectiveSkillService objectiveSkillService, CurriculumRepository curricula,
                         CurriculumService curriculumService, LessonRepository lessons, LessonService lessonService,
                         ActivityRepository activities, ActivityService activityService,
                         ActivityVersionSkillRepository activitySkills, ActivityItemRepository activityItems,
                         QuestionRepository questions, QuestionService questionService,
                         QuestionVersionSkillRepository questionSkills, QuestionVersionRepository questionVersions,
                         ActivityVersionRepository activityVersions, MediaService media,
                         com.platform.content.review.ContentReviewService reviews,
                         org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate jdbc) {
        this.reviews = reviews;
        this.jdbc = jdbc;
        this.guard = guard;
        this.audit = audit;
        this.validator = validator;
        this.subjects = subjects;
        this.subjectService = subjectService;
        this.ageHubs = ageHubs;
        this.skills = skills;
        this.skillService = skillService;
        this.prerequisites = prerequisites;
        this.prerequisiteService = prerequisiteService;
        this.objectives = objectives;
        this.objectiveService = objectiveService;
        this.objectiveSkills = objectiveSkills;
        this.objectiveSkillService = objectiveSkillService;
        this.curricula = curricula;
        this.curriculumService = curriculumService;
        this.lessons = lessons;
        this.lessonService = lessonService;
        this.activities = activities;
        this.activityService = activityService;
        this.activitySkills = activitySkills;
        this.activityItems = activityItems;
        this.questions = questions;
        this.questionService = questionService;
        this.questionSkills = questionSkills;
        this.questionVersions = questionVersions;
        this.activityVersions = activityVersions;
        this.media = media;
    }

    // ================================================================== catalog

    @Transactional(readOnly = true)
    public List<SubjectDto> subjects(UUID accountId) {
        return subjects(accountId, false);
    }

    /** X14: {@code archived} lists the archived subjects instead (to restore). */
    @Transactional(readOnly = true)
    public List<SubjectDto> subjects(UUID accountId, boolean archived) {
        guard.requireAuthor(accountId);
        return subjects.findByStatus(archived ? CatalogStatus.ARCHIVED : CatalogStatus.ACTIVE).stream()
                .sorted(Comparator.comparing(Subject::getName, String.CASE_INSENSITIVE_ORDER))
                .map(StudioService::subjectDto).toList();
    }

    @Transactional(readOnly = true)
    public List<AgeHubDto> ageHubs(UUID accountId) {
        guard.requireAuthor(accountId);
        return ageHubs.findAllByOrderByDisplayOrderAsc().stream()
                .map(hub -> new AgeHubDto(hub.getId(), hub.getCode(), hub.getName(), hub.getMinAge(), hub.getMaxAge()))
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogDto catalog(UUID accountId, UUID subjectId) {
        return catalog(accountId, subjectId, false);
    }

    /** X14: {@code archived} shows the subject's archived skills and objectives instead (to restore). */
    @Transactional(readOnly = true)
    public CatalogDto catalog(UUID accountId, UUID subjectId, boolean archived) {
        guard.requireAuthor(accountId);
        Subject subject = subjectService.getById(subjectId);
        CatalogStatus shown = archived ? CatalogStatus.ARCHIVED : CatalogStatus.ACTIVE;
        List<SkillDto> skillDtos = skills.findBySubjectIdAndStatus(subjectId, shown).stream()
                .sorted(Comparator.comparing(Skill::getName, String.CASE_INSENSITIVE_ORDER))
                .map(this::skillDto).toList();
        List<ObjectiveDto> objectiveDtos = objectives.findBySubjectId(subjectId).stream()
                .filter(objective -> objective.getStatus() == shown)
                .sorted(Comparator.comparing(LearningObjective::getTitle, String.CASE_INSENSITIVE_ORDER))
                .map(this::objectiveDto).toList();
        return new CatalogDto(subjectDto(subject), skillDtos, objectiveDtos);
    }

    @Transactional
    public SubjectDto createSubject(UUID accountId, CreateSubjectRequest request) {
        Account admin = guard.requireCatalogAdmin(accountId);
        Subject subject = subjectService.create(request.code(), request.name().strip(), blankToNull(request.description()));
        record(admin, "CATALOG_SUBJECT_CREATED", "SUBJECT", subject.getId(), Map.of("code", subject.getCode()));
        return subjectDto(subject);
    }

    @Transactional
    public SkillDto createSkill(UUID accountId, UUID subjectId, CreateSkillRequest request) {
        Account admin = guard.requireCatalogAdmin(accountId);
        subjectService.getById(subjectId);
        Skill skill = skillService.createSkill(subjectId, request.code(), request.name().strip(), blankToNull(request.description()));
        record(admin, "CATALOG_SKILL_CREATED", "SKILL", skill.getId(), Map.of("code", skill.getCode()));
        return skillDto(skill);
    }

    @Transactional
    public ObjectiveDto createObjective(UUID accountId, UUID subjectId, CreateObjectiveRequest request) {
        Account admin = guard.requireCatalogAdmin(accountId);
        subjectService.getById(subjectId);
        LearningObjective objective = objectiveService.create(subjectId, request.code(), request.title().strip(),
                blankToNull(request.description()));
        record(admin, "CATALOG_OBJECTIVE_CREATED", "LEARNING_OBJECTIVE", objective.getId(), Map.of("code", objective.getCode()));
        return objectiveDto(objective);
    }

    /**
     * Adds a skill to an objective. Links are not removable here: an objective's skills
     * shape the paths of children already enrolled and their parents' objective
     * standing, so removing one needs a deliberate decision (recorded in the progress
     * document), not an editor button.
     */
    @Transactional
    public ObjectiveDto linkObjectiveSkill(UUID accountId, UUID objectiveId, UUID skillId) {
        Account admin = guard.requireCatalogAdmin(accountId);
        LearningObjective objective = objectiveService.getById(objectiveId);
        Skill skill = skillService.getSkill(skillId);
        if (!Objects.equals(objective.getSubjectId(), skill.getSubjectId())) {
            throw new IllegalArgumentException("An objective's skills must belong to the objective's subject");
        }
        boolean linked = objectiveSkills.findByLearningObjectiveId(objectiveId).stream()
                .anyMatch(link -> link.getSkillId().equals(skillId));
        if (!linked) {
            objectiveSkillService.link(objectiveId, skillId);
            record(admin, "CATALOG_OBJECTIVE_SKILL_LINKED", "LEARNING_OBJECTIVE", objectiveId, Map.of("skillId", skillId));
        }
        return objectiveDto(objective);
    }

    /** Cycle-checked by {@link SkillPrerequisiteService}; idempotent. */
    @Transactional
    public SkillDto addPrerequisite(UUID accountId, UUID skillId, UUID prerequisiteSkillId) {
        Account admin = guard.requireCatalogAdmin(accountId);
        Skill skill = skillService.getSkill(skillId);
        Skill prerequisite = skillService.getSkill(prerequisiteSkillId);
        if (!Objects.equals(skill.getSubjectId(), prerequisite.getSubjectId())) {
            throw new IllegalArgumentException("A prerequisite must belong to the same subject");
        }
        boolean present = prerequisites.findBySkillId(skillId).stream()
                .anyMatch(link -> link.getPrerequisiteSkillId().equals(prerequisiteSkillId));
        if (!present) {
            prerequisiteService.addPrerequisite(skillId, prerequisiteSkillId);
            record(admin, "CATALOG_PREREQUISITE_ADDED", "SKILL", skillId, Map.of("prerequisiteSkillId", prerequisiteSkillId));
        }
        return skillDto(skill);
    }

    // ================================================================== curricula

    @Transactional(readOnly = true)
    public List<CurriculumSummary> curricula(UUID accountId) {
        return curricula(accountId, false);
    }

    /** X14: {@code archived} lists the archived curricula instead (no longer offered to families; to restore). */
    @Transactional(readOnly = true)
    public List<CurriculumSummary> curricula(UUID accountId, boolean archived) {
        UUID owner = guard.authoringOwner(guard.requireAuthor(accountId));
        List<Curriculum> owned = owner == null ? curricula.findByOwnerAccountIdIsNull() : curricula.findByOwnerAccountId(owner);
        CatalogStatus shown = archived ? CatalogStatus.ARCHIVED : CatalogStatus.ACTIVE;
        return owned.stream()
                .filter(curriculum -> curriculum.getStatus() == shown)
                .sorted(Comparator.comparing(Curriculum::getName, String.CASE_INSENSITIVE_ORDER))
                .map(curriculum -> new CurriculumSummary(curriculum.getId(), curriculum.getName(), curriculum.getSubjectId(),
                        curriculum.getAgeHubId(), curriculumService.listVersions(curriculum.getId()).stream()
                        .map(VersionSummary::of).toList(), curriculum.getStatus() == CatalogStatus.ARCHIVED))
                .toList();
    }

    @Transactional(readOnly = true)
    public CurriculumDetail curriculum(UUID accountId, UUID curriculumId) {
        Curriculum curriculum = guard.requireCurriculumWrite(accountId, curriculumId);
        List<CurriculumVersionDetail> versions = curriculumService.listVersions(curriculumId).stream()
                .map(version -> new CurriculumVersionDetail(version.getId(), version.getVersionNumber(), version.getStatus(),
                        version.getPublishedAt(), curriculumService.listObjectives(version.getId()).stream()
                        .map(CurriculumObjective::getLearningObjectiveId).toList()))
                .toList();
        return new CurriculumDetail(curriculum.getId(), curriculum.getName(), curriculum.getSubjectId(),
                curriculum.getAgeHubId(), versions, curriculum.getStatus() == CatalogStatus.ARCHIVED);
    }

    @Transactional
    public CurriculumDetail createCurriculum(UUID accountId, CreateCurriculumRequest request) {
        Account author = guard.requireAuthor(accountId);
        subjectService.getById(request.subjectId());
        if (!ageHubs.existsById(request.ageHubId())) {
            throw new ResourceNotFoundException("AgeHub", request.ageHubId());
        }
        UUID owner = guard.authoringOwner(author);
        Curriculum curriculum = owner == null
                ? curriculumService.createCurriculum(request.subjectId(), request.ageHubId(), request.name().strip())
                : curriculumService.createOwnedCurriculum(request.subjectId(), request.ageHubId(), request.name().strip(), owner);
        curriculumService.createDraftVersion(curriculum.getId());
        return curriculum(accountId, curriculum.getId());
    }

    @Transactional
    public CurriculumDetail openCurriculumDraft(UUID accountId, UUID curriculumId) {
        guard.requireCurriculumWrite(accountId, curriculumId);
        curriculumService.openDraft(curriculumId);
        return curriculum(accountId, curriculumId);
    }

    @Transactional
    public CurriculumDetail replaceCurriculumObjectives(UUID accountId, UUID curriculumVersionId, List<UUID> objectiveIds) {
        CurriculumVersion version = curriculumService.getVersion(curriculumVersionId);
        guard.requireCurriculumWrite(accountId, version.getCurriculumId());
        curriculumService.replaceObjectives(curriculumVersionId, objectiveIds);
        return curriculum(accountId, version.getCurriculumId());
    }

    @Transactional(readOnly = true)
    public ValidationReport validateCurriculumVersion(UUID accountId, UUID curriculumVersionId) {
        guard.requireCurriculumWrite(accountId, curriculumService.getVersion(curriculumVersionId).getCurriculumId());
        return validator.curriculumVersion(curriculumVersionId);
    }

    @Transactional
    public CurriculumDetail publishCurriculumVersion(UUID accountId, UUID curriculumVersionId) {
        return publishCurriculumVersion(accountId, curriculumVersionId, null);
    }

    /** Y14: {@code approval} comes only from an approved review; without one, the review gate decides. */
    @Transactional
    public CurriculumDetail publishCurriculumVersion(UUID accountId, UUID curriculumVersionId, com.platform.content.review.ContentReviewService.Approval approval) {
        CurriculumVersion version = curriculumService.getVersion(curriculumVersionId);
        guard.requireCurriculumWrite(accountId, version.getCurriculumId());
        reviews.requirePublishable(com.platform.content.review.ContentReview.Kind.CURRICULUM, curriculumVersionId, approval);
        CurriculumVersion published = curriculumService.publish(curriculumVersionId);
        record(guard.requireAuthor(accountId), "CONTENT_PUBLISHED", "CURRICULUM_VERSION", published.getId(),
                Map.of("curriculumId", published.getCurriculumId(), "versionNumber", published.getVersionNumber()));
        return curriculum(accountId, version.getCurriculumId());
    }

    // ================================================================== lessons & activities

    /**
     * One page of the caller's lessons, optionally in one subject, each with its activities. A search
     * matches lesson and activity titles; a lesson found through its activities lists just those.
     */
    @Transactional(readOnly = true)
    public PageResponse<LessonSummary> lessons(UUID accountId, UUID subjectId, String text, PageResponse.Request request) {
        return lessons(accountId, subjectId, text, false, request);
    }

    /**
     * X14: the active view shows active lessons with their active activities; {@code archived} shows what was
     * archived - archived lessons (with all their activities) and archived activities of active lessons.
     */
    @Transactional(readOnly = true)
    public PageResponse<LessonSummary> lessons(UUID accountId, UUID subjectId, String text, boolean archived,
                                               PageResponse.Request request) {
        UUID owner = guard.authoringOwner(guard.requireAuthor(accountId));
        String pattern = SearchText.containsPattern(text);
        long total = lessons.countStudio(owner, subjectId, pattern, archived);
        List<UUID> ids = total == 0 ? List.of() : lessons.searchStudio(owner, subjectId, pattern, archived, request.size(),
                request.offset());
        if (ids.isEmpty()) {
            return new PageResponse<>(List.of(), request.page(), request.size(), total);
        }
        Map<UUID, Lesson> byId = lessons.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Lesson::getId, lesson -> lesson));
        List<Activity> all = activities.findByLessonIdIn(ids);
        List<UUID> activityIds = all.stream().map(Activity::getId).toList();
        List<ActivityVersion> allVersions = activityIds.isEmpty() ? List.of()
                : activityVersions.findByActivityIdInOrderByVersionNumberAsc(activityIds);
        Map<UUID, List<VersionSummary>> versionsOf = allVersions.stream()
                .collect(java.util.stream.Collectors.groupingBy(ActivityVersion::getActivityId,
                        java.util.stream.Collectors.mapping(VersionSummary::of, java.util.stream.Collectors.toList())));
        // What each activity practises: its latest version's tags (PD-08).
        Map<UUID, UUID> latestVersionOf = new java.util.HashMap<>();
        allVersions.forEach(v -> latestVersionOf.put(v.getActivityId(), v.getId()));
        Map<UUID, List<UUID>> tagsOfVersion = latestVersionOf.isEmpty() ? Map.of()
                : activitySkills.findByActivityVersionIdIn(latestVersionOf.values()).stream()
                .collect(java.util.stream.Collectors.groupingBy(ActivityVersionSkill::getActivityVersionId,
                        java.util.stream.Collectors.mapping(ActivityVersionSkill::getSkillId, java.util.stream.Collectors.toList())));
        Map<UUID, List<UUID>> skillsOf = new java.util.HashMap<>();
        latestVersionOf.forEach((activityId, versionId) -> skillsOf.put(activityId, tagsOfVersion.getOrDefault(versionId, List.of())));
        Map<UUID, List<Activity>> byLesson = all.stream().collect(java.util.stream.Collectors.groupingBy(Activity::getLessonId));
        Map<UUID, StudioDtos.Support> supportOf = supportOf(allVersions, latestVersionOf);
        String needle = pattern == null ? null : text.strip().toLowerCase(java.util.Locale.ROOT);
        List<LessonSummary> page = ids.stream().map(byId::get).filter(Objects::nonNull).map(lesson -> {
            boolean lessonArchived = lesson.getStatus() == CatalogStatus.ARCHIVED;
            List<Activity> own = byLesson.getOrDefault(lesson.getId(), List.of()).stream()
                    .filter(activity -> archived ? lessonArchived || activity.getStatus() == CatalogStatus.ARCHIVED
                            : activity.getStatus() == CatalogStatus.ACTIVE)
                    .toList();
            boolean lessonMatches = needle == null || lesson.getTitle().toLowerCase(java.util.Locale.ROOT).contains(needle);
            List<ActivitySummary> shown = own.stream()
                    .filter(activity -> lessonMatches || activity.getTitle().toLowerCase(java.util.Locale.ROOT).contains(needle))
                    .sorted(Comparator.comparing(Activity::getTitle, String.CASE_INSENSITIVE_ORDER))
                    .map(activity -> new ActivitySummary(activity.getId(), activity.getTitle(), activity.getActivityType(),
                            skillsOf.getOrDefault(activity.getId(), List.of()), versionsOf.getOrDefault(activity.getId(), List.of()),
                            activity.getStatus() == CatalogStatus.ARCHIVED, supportOf.get(activity.getId())))
                    .toList();
            return new LessonSummary(lesson.getId(), lesson.getTitle(), lesson.getSubjectId(), shown, own.size(), lessonArchived,
                    lesson.getVisibility().name(), lesson.getCopiedFromLessonId() != null || lesson.getCopiedFromAccountId() != null);
        }).toList();
        return new PageResponse<>(page, request.page(), request.size(), total);
    }

    @Transactional
    public LessonSummary createLesson(UUID accountId, CreateLessonRequest request) {
        Account author = guard.requireAuthor(accountId);
        subjectService.getById(request.subjectId());
        Lesson lesson = lessonService.createOwnedLesson(request.subjectId(), request.title().strip(), guard.authoringOwner(author));
        return new LessonSummary(lesson.getId(), lesson.getTitle(), lesson.getSubjectId(), List.of(), 0, false,
                lesson.getVisibility().name(), false);
    }

    /** An activity with its skills and a first draft, in one step. Activities are PRACTICE: no other type is defined yet. */
    @Transactional
    public ActivityDetail createActivity(UUID accountId, UUID lessonId, CreateActivityRequest request) {
        Lesson lesson = guard.requireLessonWrite(accountId, lessonId);
        requireSkillsOfSubject(request.skillIds(), lesson.getSubjectId());
        Activity activity = activityService.createActivity(lesson.getSubjectId(), lessonId, PRACTICE, request.title().strip());
        ActivityVersion draft = activityService.createDraftVersion(activity.getId(), null, null, null);
        request.skillIds().forEach(skillId -> activityService.tagSkill(draft.getId(), skillId));
        return activity(accountId, activity.getId());
    }

    @Transactional(readOnly = true)
    public ActivityDetail activity(UUID accountId, UUID activityId) {
        Activity activity = guard.requireActivityWrite(accountId, activityId);
        List<ActivityVersionDetail> versions = activityService.listVersions(activityId).stream()
                .map(version -> new ActivityVersionDetail(version.getId(), version.getVersionNumber(), version.getStatus(),
                        version.getPublishedAt(), version.getInstructions(), version.getDifficultyLevel(),
                        version.getEstimatedDurationSeconds(),
                        activityItems.findByActivityVersionIdOrderBySequenceOrderAsc(version.getId()).stream()
                                .map(this::itemDetail).toList(), activityService.skillsOf(version.getId()),
                        introductionOf(version)))
                .toList();
        return new ActivityDetail(activity.getId(), activity.getTitle(), activity.getActivityType(), activity.getLessonId(),
                activity.getSubjectId(), activitySkillIds(activityId), versions, activity.getStatus() == CatalogStatus.ARCHIVED);
    }

    @Transactional
    public ActivityDetail openActivityDraft(UUID accountId, UUID activityId) {
        guard.requireActivityWrite(accountId, activityId);
        activityService.openDraft(activityId);
        return activity(accountId, activityId);
    }

    @Transactional
    public ActivityDetail tagActivitySkill(UUID accountId, UUID activityId, UUID skillId) {
        Activity activity = guard.requireActivityWrite(accountId, activityId);
        requireSkillsOfSubject(List.of(skillId), activity.getSubjectId());
        activityService.tagSkill(openActivityDraftId(activityId), skillId);
        return activity(accountId, activityId);
    }

    @Transactional
    public ActivityDetail untagActivitySkill(UUID accountId, UUID activityId, UUID skillId) {
        guard.requireActivityWrite(accountId, activityId);
        activityService.untagSkill(openActivityDraftId(activityId), skillId);
        return activity(accountId, activityId);
    }

    /** PD-08: skills change on the open draft only; a published version keeps what it was published with. */
    private UUID openActivityDraftId(UUID activityId) {
        return activityVersions.findTopByActivityIdOrderByVersionNumberDesc(activityId)
                .filter(version -> version.getStatus() == PublicationStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("Start a new version to change the skills this activity practises: a published version keeps the skills it was published with."))
                .getId();
    }

    /** L8: each activity's latest version, as the help a child gets in it. One statement for the page. */
    private Map<UUID, StudioDtos.Support> supportOf(List<ActivityVersion> allVersions, Map<UUID, UUID> latestVersionOf) {
        if (latestVersionOf.isEmpty()) {
            return Map.of();
        }
        Map<UUID, int[]> counts = new java.util.HashMap<>();
        jdbc.query("""
                SELECT ai.activity_version_id, count(*), count(qv.prompt_audio_asset_id), count(qv.explanation), count(qv.hint)
                FROM activity_items ai JOIN question_versions qv ON qv.id = ai.question_version_id
                WHERE ai.activity_version_id IN (:versions)
                GROUP BY ai.activity_version_id
                """, Map.of("versions", latestVersionOf.values()), rs -> {
            counts.put(rs.getObject(1, UUID.class), new int[] {rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5)});
        });
        Map<UUID, ActivityVersion> byId = allVersions.stream()
                .collect(java.util.stream.Collectors.toMap(ActivityVersion::getId, version -> version));
        Map<UUID, StudioDtos.Support> support = new java.util.HashMap<>();
        latestVersionOf.forEach((activityId, versionId) -> {
            int[] c = counts.getOrDefault(versionId, new int[4]);
            ActivityVersion version = byId.get(versionId);
            support.put(activityId, new StudioDtos.Support(c[0], c[1], c[2], c[3], version != null && version.hasIntroduction()));
        });
        return support;
    }

    /** An activity version's introduction for the Studio, its media as signed links; null when it has none. */
    private StudioDtos.IntroductionDetail introductionOf(ActivityVersion version) {
        if (!version.hasIntroduction()) {
            return null;
        }
        Map<UUID, MediaService.MediaRef> refs = media.refs(java.util.stream.Stream.of(version.getIntroductionImageAssetId(),
                version.getIntroductionAudioAssetId()).filter(Objects::nonNull).toList());
        return new StudioDtos.IntroductionDetail(version.getIntroduction(), refs.get(version.getIntroductionImageAssetId()),
                version.getIntroductionImageDescription(), refs.get(version.getIntroductionAudioAssetId()));
    }

    /**
     * What an activity teaches before it asks, on its draft: text, a picture (with its description) and a recording,
     * each optional. Its media follow the same rule as a question's: the activity's owner may use them, of the kind
     * the field needs, not archived (unless the draft already had it).
     */
    @Transactional
    public ActivityDetail setActivityIntroduction(UUID accountId, UUID activityVersionId, StudioDtos.IntroductionRequest request) {
        ActivityVersion version = activityService.getVersion(activityVersionId);
        Activity activity = guard.requireActivityWrite(accountId, version.getActivityId());
        // An activity's content belongs to its lesson's owner (N7); one without a lesson is platform content.
        UUID owner = activity.getLessonId() == null ? null
                : lessons.findById(activity.getLessonId()).map(Lesson::getOwnerAccountId).orElse(null);
        java.util.Set<UUID> onDraft = java.util.stream.Stream.of(version.getIntroductionImageAssetId(), version.getIntroductionAudioAssetId())
                .filter(Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        media.requireAttachable(request.imageAssetId(), AssetType.IMAGE, owner, onDraft);
        media.requireAttachable(request.audioAssetId(), AssetType.AUDIO, owner, onDraft);
        activityService.setIntroduction(activityVersionId, request.text(), request.imageAssetId(), request.imageDescription(),
                request.audioAssetId());
        return activity(accountId, version.getActivityId());
    }

    @Transactional
    public ActivityDetail reviseActivityDraft(UUID accountId, UUID activityVersionId, ReviseActivityDraftRequest request) {
        ActivityVersion version = activityService.getVersion(activityVersionId);
        guard.requireActivityWrite(accountId, version.getActivityId());
        activityService.reviseDraft(activityVersionId, blankToNull(request.instructions()), request.difficultyLevel(),
                request.estimatedDurationSeconds());
        return activity(accountId, version.getActivityId());
    }

    @Transactional
    public ActivityDetail replaceActivityItems(UUID accountId, UUID activityVersionId, List<ItemRequest> items) {
        ActivityVersion version = activityService.getVersion(activityVersionId);
        guard.requireActivityWrite(accountId, version.getActivityId());
        activityService.replaceItems(activityVersionId, items.stream()
                .map(item -> new ActivityService.ItemDraft(item.questionVersionId(), item.points())).toList());
        return activity(accountId, version.getActivityId());
    }

    @Transactional(readOnly = true)
    public ValidationReport validateActivityVersion(UUID accountId, UUID activityVersionId) {
        guard.requireActivityWrite(accountId, activityService.getVersion(activityVersionId).getActivityId());
        return validator.activityVersion(activityVersionId);
    }

    @Transactional
    public ActivityDetail publishActivityVersion(UUID accountId, UUID activityVersionId) {
        return publishActivityVersion(accountId, activityVersionId, null);
    }

    /** Y14: {@code approval} comes only from an approved review; without one, the review gate decides. */
    @Transactional
    public ActivityDetail publishActivityVersion(UUID accountId, UUID activityVersionId, com.platform.content.review.ContentReviewService.Approval approval) {
        ActivityVersion version = activityService.getVersion(activityVersionId);
        guard.requireActivityWrite(accountId, version.getActivityId());
        reviews.requirePublishable(com.platform.content.review.ContentReview.Kind.ACTIVITY, activityVersionId, approval);
        ActivityVersion published = activityService.publish(activityVersionId);
        record(guard.requireAuthor(accountId), "CONTENT_PUBLISHED", "ACTIVITY_VERSION", published.getId(),
                Map.of("activityId", published.getActivityId(), "versionNumber", published.getVersionNumber()));
        return activity(accountId, version.getActivityId());
    }

    /**
     * Y42: a new question written inside an activity - created like any question (for the author, in the
     * activity's subject; tagged with the activity's skills unless others are given) and added at the end of
     * the activity's draft, in one transaction: either both happen or neither does.
     */
    @Transactional
    public ActivityDetail addNewQuestion(UUID accountId, UUID activityVersionId, CreateQuestionRequest request) {
        ActivityVersion version = activityService.getVersion(activityVersionId);
        Activity activity = guard.requireActivityWrite(accountId, version.getActivityId());
        List<UUID> skills = request.skillIds() == null || request.skillIds().isEmpty()
                ? activityService.skillsOf(activityVersionId) : request.skillIds();
        QuestionDetail created = createQuestion(accountId, new CreateQuestionRequest(activity.getSubjectId(), request.questionType(),
                request.prompt(), request.correctAnswer(), request.difficultyLevel(), request.options(), request.pairs(), request.items(),
                request.promptImageAssetId(), request.promptImageDescription(), request.promptAudioAssetId(), skills, request.groups(),
                request.sortItems(), request.hint(), request.explanation()));
        List<ActivityService.ItemDraft> items = new java.util.ArrayList<>(activityItems.findByActivityVersionIdOrderBySequenceOrderAsc(activityVersionId)
                .stream().map(item -> new ActivityService.ItemDraft(item.getQuestionVersionId(), item.getPoints())).toList());
        items.add(new ActivityService.ItemDraft(created.versions().get(0).id(), null));
        activityService.replaceItems(activityVersionId, items);
        return activity(accountId, activity.getId());
    }

    /**
     * Y42: publish an activity together with the questions in it that are still drafts - one step for the
     * author, one transaction for the platform. Each question goes through the same guard, review gate and
     * checks as publishing it alone, then the activity does; if any one of them is refused, nothing is
     * published. Where content review is required, the review gate refuses here as anywhere.
     */
    @Transactional
    public ActivityDetail publishActivityWithQuestions(UUID accountId, UUID activityVersionId) {
        ActivityVersion version = activityService.getVersion(activityVersionId);
        guard.requireActivityWrite(accountId, version.getActivityId());
        for (ActivityItem item : activityItems.findByActivityVersionIdOrderBySequenceOrderAsc(activityVersionId)) {
            if (questionService.getVersion(item.getQuestionVersionId()).getStatus() == PublicationStatus.DRAFT) {
                publishQuestionVersion(accountId, item.getQuestionVersionId(), null);
            }
        }
        return publishActivityVersion(accountId, activityVersionId, null);
    }

    /** Exactly what a child is served for this version - the learner DTO - for the author to try before publishing. */
    @Transactional(readOnly = true)
    public ActivityVersionDetailResponse previewActivityVersion(UUID accountId, UUID activityVersionId) {
        ActivityVersion version = activityService.getVersion(activityVersionId);
        Activity activity = guard.requireActivityWrite(accountId, version.getActivityId());
        List<ActivityItemResponse> items = activityItems.findByActivityVersionIdOrderBySequenceOrderAsc(activityVersionId).stream()
                .map(item -> learnerItem(item.getId(), item.getSequenceOrder(), questionService.getVersion(item.getQuestionVersionId())))
                .toList();
        Map<UUID, MediaService.MediaRef> introMedia = media.refs(java.util.stream.Stream.of(version.getIntroductionImageAssetId(),
                version.getIntroductionAudioAssetId()).filter(Objects::nonNull).toList());
        return new ActivityVersionDetailResponse(version.getId(), activity.getId(), activity.getTitle(),
                version.getInstructions(), version.getDifficultyLevel(), version.getEstimatedDurationSeconds(), items,
                com.platform.content.web.ActivityContentDtos.LearnerIntroduction.of(version, introMedia));
    }

    // ================================================================== questions

    /**
     * One page of the caller's question bank for a subject, searched and filtered in the database.
     * A subject's bank grows with every activity written for it, so it is never loaded whole.
     */
    @Transactional(readOnly = true)
    public PageResponse<QuestionSummary> questions(UUID accountId, UUID subjectId, QuestionFilter filter,
                                                   PageResponse.Request request) {
        UUID owner = guard.authoringOwner(guard.requireAuthor(accountId));
        String type = filter.type() == null ? null : filter.type().name();
        String status = filter.status() == null ? null : filter.status().name();
        String pattern = SearchText.containsPattern(filter.text());
        long total = questions.countBank(subjectId, owner, type, status, filter.skillId(), filter.live(), pattern, filter.archived());
        List<UUID> ids = total == 0 ? List.of() : questions.searchBank(subjectId, owner, type, status, filter.skillId(),
                filter.live(), pattern, filter.archived(), request.size(), request.offset());
        return new PageResponse<>(questionSummaries(ids), request.page(), request.size(), total);
    }

    /** Summaries for the given questions, in the given order, in three queries whatever the page size. */
    private List<QuestionSummary> questionSummaries(List<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<UUID, Question> byId = questions.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Question::getId, question -> question));
        Map<UUID, List<QuestionVersion>> versionsOf = questionVersions.findByQuestionIdInOrderByVersionNumberAsc(ids).stream()
                .collect(java.util.stream.Collectors.groupingBy(QuestionVersion::getQuestionId));
        List<UUID> latestIds = versionsOf.values().stream().map(versions -> versions.get(versions.size() - 1).getId()).toList();
        Map<UUID, List<UUID>> skillsOfVersion = latestIds.isEmpty() ? Map.of() : questionSkills.findByQuestionVersionIdIn(latestIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(QuestionVersionSkill::getQuestionVersionId,
                        java.util.stream.Collectors.mapping(QuestionVersionSkill::getSkillId, java.util.stream.Collectors.toList())));
        Map<UUID, List<UUID>> skillsOf = new java.util.HashMap<>();
        versionsOf.forEach((questionId, versions) ->
                skillsOf.put(questionId, skillsOfVersion.getOrDefault(versions.get(versions.size() - 1).getId(), List.of())));
        return ids.stream().map(byId::get).filter(Objects::nonNull).map(question -> {
            List<QuestionVersion> versions = versionsOf.getOrDefault(question.getId(), List.of());
            QuestionVersion latest = versions.isEmpty() ? null : versions.get(versions.size() - 1);
            UUID publishedId = versions.stream().filter(v -> v.getStatus() == PublicationStatus.PUBLISHED)
                    .map(QuestionVersion::getId).findFirst().orElse(null);
            return new QuestionSummary(question.getId(), question.getSubjectId(), question.getQuestionType(),
                    skillsOf.getOrDefault(question.getId(), List.of()), latest == null ? null : latest.getPrompt(), publishedId,
                    versions.stream().map(VersionSummary::of).toList(), question.getStatus() == CatalogStatus.ARCHIVED);
        }).toList();
    }

    @Transactional(readOnly = true)
    public QuestionDetail question(UUID accountId, UUID questionId) {
        Question question = guard.requireQuestionWrite(accountId, questionId);
        boolean ordering = question.getQuestionType() == QuestionType.ORDERING;
        boolean sorting = question.getQuestionType() == QuestionType.SORTING;
        return new QuestionDetail(question.getId(), question.getSubjectId(), question.getQuestionType(),
                questionSkillIds(questionId), questionService.listVersions(questionId).stream()
                .map(version -> {
                    List<QuestionOption> options = questionService.listOptions(version.getId());
                    Map<UUID, MediaService.MediaRef> refs = media.refs(ActivityContentDtos.mediaOf(version, options));
                    List<QuestionOption> plain = options.stream()
                            .filter(option -> option.getMatchSide() == null && option.getSortRole() == null).toList();
                    return new QuestionVersionDetail(version.getId(), version.getVersionNumber(), version.getStatus(),
                            version.getPublishedAt(), version.getPrompt(), version.getCorrectAnswer(), version.getDifficultyLevel(),
                            refs.get(version.getPromptImageAssetId()), version.getPromptImageDescription(),
                            refs.get(version.getPromptAudioAssetId()),
                            ordering ? List.of() : plain.stream().map(option -> new OptionDetail(option.getId(), option.getLabel(),
                                    option.isCorrect(), refs.get(option.getImageAssetId()), refs.get(option.getAudioAssetId()))).toList(),
                            matchPairs(options, refs),
                            ordering ? plain.stream().map(item -> new OrderItemDetail(item.getId(), item.getLabel(),
                                    refs.get(item.getImageAssetId()), refs.get(item.getAudioAssetId()))).toList() : List.of(),
                            questionService.skillsOf(version.getId()),
                            sorting ? options.stream().filter(o -> o.getSortRole() == SortRole.GROUP)
                                    .sorted(java.util.Comparator.comparing(QuestionOption::getSortGroup))
                                    .map(group -> new SortGroupDetail(group.getId(), group.getLabel(), group.getSortGroup(),
                                            refs.get(group.getImageAssetId()))).toList() : List.of(),
                            sorting ? options.stream().filter(o -> o.getSortRole() == SortRole.ITEM)
                                    .map(item -> new SortItemDetail(item.getId(), item.getLabel(), item.getSortGroup(),
                                            refs.get(item.getImageAssetId()), refs.get(item.getAudioAssetId()))).toList() : List.of(),
                            version.getHint(), version.getExplanation());
                })
                .toList(), question.getStatus() == CatalogStatus.ARCHIVED);
    }

    /** A question with its skills, a first draft and (multiple choice) its answer choices, in one step. */
    @Transactional
    public QuestionDetail createQuestion(UUID accountId, CreateQuestionRequest request) {
        Account author = guard.requireAuthor(accountId);
        subjectService.getById(request.subjectId());
        requireSkillsOfSubject(request.skillIds(), request.subjectId());
        Question question = questionService.createOwnedQuestion(request.subjectId(), request.questionType(),
                guard.authoringOwner(author));
        QuestionVersion draft = questionService.createDraftVersion(question.getId(), request.prompt().strip(),
                answerFor(request.questionType(), request.correctAnswer()), request.difficultyLevel());
        request.skillIds().forEach(skillId -> questionService.tagSkill(draft.getId(), skillId));
        writeDraftContent(question, draft.getId(), request.promptImageAssetId(), request.promptImageDescription(),
                request.promptAudioAssetId(), request.options(), request.pairs(), request.items(), request.groups(), request.sortItems());
        questionService.setGuidance(draft.getId(), request.hint(), request.explanation());
        return question(accountId, question.getId());
    }

    @Transactional
    public QuestionDetail openQuestionDraft(UUID accountId, UUID questionId) {
        guard.requireQuestionWrite(accountId, questionId);
        questionService.openDraft(questionId);
        return question(accountId, questionId);
    }

    @Transactional
    public QuestionDetail reviseQuestionDraft(UUID accountId, UUID questionVersionId, ReviseQuestionDraftRequest request) {
        QuestionVersion version = questionService.getVersion(questionVersionId);
        Question question = guard.requireQuestionWrite(accountId, version.getQuestionId());
        questionService.reviseDraft(questionVersionId, request.prompt().strip(),
                answerFor(question.getQuestionType(), request.correctAnswer()), request.difficultyLevel());
        writeDraftContent(question, questionVersionId, request.promptImageAssetId(), request.promptImageDescription(),
                request.promptAudioAssetId(), request.options(), request.pairs(), request.items(), request.groups(), request.sortItems());
        questionService.setGuidance(questionVersionId, request.hint(), request.explanation());
        return question(accountId, question.getId());
    }

    /**
     * A draft's media and answer items, as its type has them. Every asset must be one this
     * question's content may use, of the kind the field needs (X11), checked before anything is written.
     */
    private void writeDraftContent(Question question, UUID draftId, UUID promptImage, String promptImageDescription, UUID promptAudio,
                                   List<OptionRequest> options, List<MatchPairRequest> pairs, List<OrderItemRequest> items,
                                   List<SortGroupRequest> groups, List<SortItemRequest> sortItems) {
        UUID owner = question.getOwnerAccountId();
        // X13: files this draft already uses stay usable on it even if archived since.
        java.util.Set<UUID> onDraft = ActivityContentDtos.mediaOf(questionService.getVersion(draftId), questionService.listOptions(draftId));
        media.requireAttachable(promptImage, AssetType.IMAGE, owner, onDraft);
        media.requireAttachable(promptAudio, AssetType.AUDIO, owner, onDraft);
        QuestionType type = question.getQuestionType();
        if (type == QuestionType.MULTIPLE_CHOICE && options != null) {
            options.forEach(option -> {
                media.requireAttachable(option.imageAssetId(), AssetType.IMAGE, owner, onDraft);
                media.requireAttachable(option.audioAssetId(), AssetType.AUDIO, owner, onDraft);
            });
        }
        if (type == QuestionType.MATCHING && pairs != null) {
            pairs.forEach(pair -> {
                media.requireAttachable(pair.leftImageAssetId(), AssetType.IMAGE, owner, onDraft);
                media.requireAttachable(pair.rightImageAssetId(), AssetType.IMAGE, owner, onDraft);
            });
        }
        if (type == QuestionType.ORDERING && items != null) {
            items.forEach(item -> {
                media.requireAttachable(item.imageAssetId(), AssetType.IMAGE, owner, onDraft);
                media.requireAttachable(item.audioAssetId(), AssetType.AUDIO, owner, onDraft);
            });
        }
        boolean sorting = type == QuestionType.SORTING && groups != null && sortItems != null;
        if (sorting) {
            groups.forEach(group -> media.requireAttachable(group.imageAssetId(), AssetType.IMAGE, owner, onDraft));
            sortItems.forEach(item -> {
                media.requireAttachable(item.imageAssetId(), AssetType.IMAGE, owner, onDraft);
                media.requireAttachable(item.audioAssetId(), AssetType.AUDIO, owner, onDraft);
            });
        }
        questionService.setPromptMedia(draftId, promptImage, promptImageDescription, promptAudio);
        if (type == QuestionType.MULTIPLE_CHOICE && options != null) {
            questionService.replaceOptions(draftId, optionDrafts(options));
        }
        if (type == QuestionType.MATCHING && pairs != null) {
            questionService.replaceMatchPairs(draftId, pairDrafts(pairs));
        }
        if (sorting) {
            // Completeness (publish) checks the groups are used and every item's group exists.
            questionService.replaceSorting(draftId,
                    groups.stream().map(group -> new QuestionService.SortGroupDraft(group.label(), group.imageAssetId())).toList(),
                    sortItems.stream().map(item -> new QuestionService.SortItemDraft(item.label(), item.group(), item.imageAssetId(),
                            item.audioAssetId())).toList());
        }
        if (type == QuestionType.ORDERING && items != null) {
            questionService.replaceOrderItems(draftId, items.stream()
                    .map(item -> new QuestionService.OrderItemDraft(item.label(), item.imageAssetId(), item.audioAssetId())).toList());
        }
    }

    @Transactional
    public QuestionDetail tagQuestionSkill(UUID accountId, UUID questionId, UUID skillId) {
        Question question = guard.requireQuestionWrite(accountId, questionId);
        requireSkillsOfSubject(List.of(skillId), question.getSubjectId());
        questionService.tagSkill(openQuestionDraftId(questionId), skillId);
        return question(accountId, questionId);
    }

    @Transactional
    public QuestionDetail untagQuestionSkill(UUID accountId, UUID questionId, UUID skillId) {
        guard.requireQuestionWrite(accountId, questionId);
        questionService.untagSkill(openQuestionDraftId(questionId), skillId);
        return question(accountId, questionId);
    }

    /** PD-08: skills change on the open draft only; answers to a published version count for what it was published with. */
    private UUID openQuestionDraftId(UUID questionId) {
        return questionVersions.findTopByQuestionIdOrderByVersionNumberDesc(questionId)
                .filter(version -> version.getStatus() == PublicationStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("Start a new version to change the skills this question gives evidence for: a published version keeps the skills it was published with."))
                .getId();
    }

    @Transactional(readOnly = true)
    public ValidationReport validateQuestionVersion(UUID accountId, UUID questionVersionId) {
        guard.requireQuestionWrite(accountId, questionService.getVersion(questionVersionId).getQuestionId());
        return validator.questionVersion(questionVersionId);
    }

    @Transactional
    public QuestionDetail publishQuestionVersion(UUID accountId, UUID questionVersionId) {
        return publishQuestionVersion(accountId, questionVersionId, null);
    }

    /** Y14: {@code approval} comes only from an approved review; without one, the review gate decides. */
    @Transactional
    public QuestionDetail publishQuestionVersion(UUID accountId, UUID questionVersionId, com.platform.content.review.ContentReviewService.Approval approval) {
        QuestionVersion version = questionService.getVersion(questionVersionId);
        guard.requireQuestionWrite(accountId, version.getQuestionId());
        reviews.requirePublishable(com.platform.content.review.ContentReview.Kind.QUESTION, questionVersionId, approval);
        QuestionVersion published = questionService.publish(questionVersionId);
        record(guard.requireAuthor(accountId), "CONTENT_PUBLISHED", "QUESTION_VERSION", published.getId(),
                Map.of("questionId", published.getQuestionId(), "versionNumber", published.getVersionNumber()));
        return question(accountId, version.getQuestionId());
    }

    /** The question exactly as a child would be served it (no answer key). */
    @Transactional(readOnly = true)
    public ActivityItemResponse previewQuestionVersion(UUID accountId, UUID questionVersionId) {
        QuestionVersion version = questionService.getVersion(questionVersionId);
        guard.requireQuestionWrite(accountId, version.getQuestionId());
        return learnerItem(version.getId(), 0, version);
    }

    // ================================================================== helpers

    private ItemDetail itemDetail(ActivityItem item) {
        QuestionVersion version = questionVersions.findById(item.getQuestionVersionId())
                .orElseThrow(() -> new ResourceNotFoundException("QuestionVersion", item.getQuestionVersionId()));
        Question question = questionService.getQuestion(version.getQuestionId());
        QuestionVersion current = version.getStatus() == PublicationStatus.RETIRED
                ? questionVersions.findFirstByQuestionIdAndStatusOrderByVersionNumberDesc(question.getId(), PublicationStatus.PUBLISHED)
                        .orElse(null)
                : null;
        return new ItemDetail(version.getId(), question.getId(), question.getQuestionType(), version.getPrompt(),
                version.getVersionNumber(), version.getStatus(), item.getPoints(), questionService.skillsOf(version.getId()),
                current == null ? null : current.getId(), current == null ? null : current.getVersionNumber());
    }

    private ActivityItemResponse learnerItem(UUID itemId, int sequenceOrder, QuestionVersion version) {
        Question question = questionService.getQuestion(version.getQuestionId());
        List<QuestionOption> options = questionService.listOptions(version.getId());
        return ActivityItemResponse.forLearner(itemId, sequenceOrder, question.getQuestionType(), version, options,
                media.refs(ActivityContentDtos.mediaOf(version, options)));
    }

    private void requireSkillsOfSubject(List<UUID> skillIds, UUID subjectId) {
        for (UUID skillId : skillIds) {
            Skill skill = skillService.getSkill(skillId);
            if (!Objects.equals(skill.getSubjectId(), subjectId) || skill.getStatus() != CatalogStatus.ACTIVE) {
                throw new IllegalArgumentException("Skills must be active skills of the content's subject");
            }
        }
    }

    /** What an activity practises: its latest version's skills (the open draft's, or the published one's). */
    private List<UUID> activitySkillIds(UUID activityId) {
        return activityVersions.findTopByActivityIdOrderByVersionNumberDesc(activityId)
                .map(version -> activityService.skillsOf(version.getId())).orElse(List.of());
    }

    /** What a question's answers are evidence for: its latest version's skills. */
    private List<UUID> questionSkillIds(UUID questionId) {
        return questionVersions.findTopByQuestionIdOrderByVersionNumberDesc(questionId)
                .map(version -> questionService.skillsOf(version.getId())).orElse(List.of());
    }

    private SkillDto skillDto(Skill skill) {
        return new SkillDto(skill.getId(), skill.getCode(), skill.getName(), skill.getDescription(), skill.getStatus(),
                prerequisites.findBySkillId(skill.getId()).stream().map(SkillPrerequisite::getPrerequisiteSkillId).toList());
    }

    private ObjectiveDto objectiveDto(LearningObjective objective) {
        return new ObjectiveDto(objective.getId(), objective.getCode(), objective.getTitle(), objective.getDescription(),
                objectiveSkills.findByLearningObjectiveId(objective.getId()).stream()
                        .map(LearningObjectiveSkill::getSkillId).toList(), objective.getStatus() == CatalogStatus.ARCHIVED);
    }

    private static SubjectDto subjectDto(Subject subject) {
        return new SubjectDto(subject.getId(), subject.getCode(), subject.getName(), subject.getDescription(),
                subject.getStatus() == CatalogStatus.ARCHIVED);
    }

    /** Multiple choice is answered by option id; its correct answer lives on the options, not the text field. */
    private static List<QuestionService.MatchPairDraft> pairDrafts(List<MatchPairRequest> pairs) {
        return pairs.stream().map(pair -> new QuestionService.MatchPairDraft(pair.left(), pair.right(), pair.leftImageAssetId(),
                pair.rightImageAssetId())).toList();
    }

    /** The author's view of a MATCHING version: its pairs in order (empty for other types). */
    private static List<MatchPairDetail> matchPairs(List<QuestionOption> options, Map<UUID, MediaService.MediaRef> refs) {
        List<QuestionOption> lefts = options.stream().filter(o -> o.getMatchSide() == MatchSide.LEFT)
                .sorted(java.util.Comparator.comparing(QuestionOption::getMatchPair)).toList();
        return lefts.stream().map(left -> options.stream()
                        .filter(o -> o.getMatchSide() == MatchSide.RIGHT && o.getMatchPair().equals(left.getMatchPair()))
                        .findFirst()
                        .map(right -> new MatchPairDetail(left.getId(), left.getLabel(), right.getId(), right.getLabel(),
                                refs.get(left.getImageAssetId()), refs.get(right.getImageAssetId())))
                        .orElse(new MatchPairDetail(left.getId(), left.getLabel(), null, null, refs.get(left.getImageAssetId()), null)))
                .toList();
    }

    private static String answerFor(QuestionType type, String correctAnswer) {
        if (type == QuestionType.MULTIPLE_CHOICE || type == QuestionType.MATCHING || type == QuestionType.ORDERING
                || type == QuestionType.SORTING) {
            return null;
        }
        return blankToNull(correctAnswer == null ? null : correctAnswer.strip());
    }

    private static List<QuestionService.OptionDraft> optionDrafts(List<OptionRequest> options) {
        return options.stream().map(option -> new QuestionService.OptionDraft(option.label(), option.correct(), option.imageAssetId(),
                option.audioAssetId())).toList();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private void record(Account actor, String action, String resourceType, UUID resourceId, Map<String, Object> metadata) {
        audit.record(new RecordAuditEvent(null, actor.getId(), AuditActorType.valueOf(actor.getPlatformRole().name()),
                action, resourceType, resourceId, metadata));
    }

}
