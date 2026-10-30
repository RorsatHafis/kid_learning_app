package com.platform.content.studio;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.entity.CatalogStatus;
import com.platform.common.entity.PublicationStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityItem;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.ContentAsset;
import com.platform.content.entity.Lesson;
import com.platform.content.entity.LessonVisibility;
import com.platform.content.entity.Question;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.media.StorageService;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.ContentAssetRepository;
import com.platform.content.repository.LessonRepository;
import com.platform.content.service.ActivityService;
import com.platform.content.service.ContentAccessGuard;
import com.platform.content.service.QuestionService;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.subscription.service.SchoolEntitlementService;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * M5b (owner decisions D-13, D-14): a teacher shares a lesson with the colleagues of their school, and a colleague
 * copies it into their own content. Colleagues are the active staff and the principal of a school both belong to,
 * when that school is open and paid (Y44: school services need a paid school).
 *
 * <p>A copy is the colleague's own, private, unpublished work: a new lesson (crediting the original) with, for each
 * of the original's published activities, a draft activity whose questions are new draft questions copied from the
 * exact published versions - prompt, answer, media, guidance, choices, skills and translations. Its pictures and
 * recordings become the colleague's own media (the same stored file; counted in their storage allowance), so they
 * can edit it as anything they wrote. It reaches children only once they publish it and their own curriculum or
 * practice places it, through the same checks as any content - sharing never changes a child's path (N7).
 */
@Service
public class LessonSharingService {

    static final String ONLY_AUTHOR = "Only its author shares a lesson";
    static final String NOT_SHARED_PLATFORM = "Platform lessons reach every family already, so they aren't shared with a school";
    static final String NOTHING_PUBLISHED = "This lesson has no published activity to copy yet";

    public record SharedActivity(UUID id, String title, int questions) {
    }

    /** A colleague's shared lesson: its published activities, and the schools through which it is shared with you. */
    public record SharedLesson(UUID id, String title, UUID subjectId, List<String> schools, List<SharedActivity> activities) {
    }

    private final ContentAccessGuard guard;
    private final LessonRepository lessons;
    private final ActivityService activityService;
    private final ActivityVersionRepository activityVersions;
    private final ActivityItemRepository activityItems;
    private final QuestionService questionService;
    private final ContentAssetRepository assets;
    private final StorageService storage;
    private final AccountRepository accounts;
    private final SchoolEntitlementService schoolEntitlements;
    private final NamedParameterJdbcTemplate jdbc;
    private final AuditService audit;

    public LessonSharingService(ContentAccessGuard guard, LessonRepository lessons, ActivityService activityService,
                                ActivityVersionRepository activityVersions, ActivityItemRepository activityItems,
                                QuestionService questionService, ContentAssetRepository assets, StorageService storage,
                                AccountRepository accounts, SchoolEntitlementService schoolEntitlements,
                                NamedParameterJdbcTemplate jdbc, AuditService audit) {
        this.guard = guard;
        this.lessons = lessons;
        this.activityService = activityService;
        this.activityVersions = activityVersions;
        this.activityItems = activityItems;
        this.questionService = questionService;
        this.assets = assets;
        this.storage = storage;
        this.accounts = accounts;
        this.schoolEntitlements = schoolEntitlements;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    /** The author shares their lesson with their school's teachers, or keeps it to themselves again. */
    @Transactional
    public void setVisibility(UUID accountId, UUID lessonId, LessonVisibility visibility) {
        Lesson lesson = guard.requireLessonWrite(accountId, lessonId);
        if (lesson.getOwnerAccountId() == null) {
            throw new IllegalStateException(NOT_SHARED_PLATFORM);
        }
        if (!lesson.getOwnerAccountId().equals(accountId)) {
            throw new AccessDeniedException(ONLY_AUTHOR);
        }
        if (lesson.getVisibility() == visibility) {
            return;
        }
        LessonVisibility before = lesson.getVisibility();
        lesson.setVisibility(visibility);
        lessons.saveAndFlush(lesson);
        record(accountId, "LESSON_VISIBILITY_CHANGED", lessonId, Map.of("from", before.name(), "to", visibility.name()));
    }

    /** Colleagues' shared lessons in this subject that have something published to copy. */
    @Transactional(readOnly = true)
    public List<SharedLesson> shared(UUID accountId, UUID subjectId) {
        Account reader = guard.requireAuthor(accountId);
        Map<UUID, String> mySchools = paidSchoolsOf(reader.getId());
        if (reader.getPlatformRole() == PlatformRole.ADMIN || mySchools.isEmpty()) {
            return List.of();
        }
        Map<UUID, List<UUID>> schoolsOfAuthor = new HashMap<>();
        jdbc.query("""
                SELECT m.account_id, m.school_id FROM (
                    SELECT st.account_id, st.school_id FROM school_staff st WHERE st.status = 'ACTIVE'
                    UNION SELECT s.principal_account_id, s.id FROM schools s
                ) m WHERE m.school_id IN (:schools) AND m.account_id <> :me
                """, new MapSqlParameterSource("schools", mySchools.keySet()).addValue("me", reader.getId()),
                row -> {
                    schoolsOfAuthor.computeIfAbsent(row.getObject(1, UUID.class), k -> new ArrayList<>()).add(row.getObject(2, UUID.class));
                });
        if (schoolsOfAuthor.isEmpty()) {
            return List.of();
        }
        List<Lesson> candidates = jdbc.queryForList("""
                        SELECT id FROM lessons WHERE visibility = 'SCHOOL' AND status = 'ACTIVE' AND subject_id = :subject
                           AND owner_account_id IN (:authors)
                        """, new MapSqlParameterSource("subject", subjectId).addValue("authors", schoolsOfAuthor.keySet()), UUID.class)
                .stream().map(id -> lessons.findById(id).orElseThrow()).toList();
        List<SharedLesson> result = new ArrayList<>();
        for (Lesson lesson : candidates) {
            List<SharedActivity> copyable = publishedActivities(lesson).entrySet().stream()
                    .map(e -> new SharedActivity(e.getKey().getId(), e.getKey().getTitle(),
                            activityItems.findByActivityVersionIdOrderBySequenceOrderAsc(e.getValue().getId()).size()))
                    .toList();
            if (copyable.isEmpty()) {
                continue;
            }
            List<String> schools = schoolsOfAuthor.get(lesson.getOwnerAccountId()).stream().distinct().map(mySchools::get).sorted().toList();
            result.add(new SharedLesson(lesson.getId(), lesson.getTitle(), lesson.getSubjectId(), schools, copyable));
        }
        result.sort(Comparator.comparing(SharedLesson::title, String.CASE_INSENSITIVE_ORDER));
        return result;
    }

    /** Copies a colleague's shared lesson into the reader's own content; returns the new lesson. */
    @Transactional
    public StudioDtos.LessonSummary copy(UUID accountId, UUID lessonId) {
        Account copier = guard.requireAuthor(accountId);
        Lesson source = lessons.findById(lessonId).orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        // Not shared with you reads as not there: a lesson id is no proof of access.
        if (copier.getPlatformRole() == PlatformRole.ADMIN || source.getVisibility() != LessonVisibility.SCHOOL
                || source.getStatus() != CatalogStatus.ACTIVE || source.getOwnerAccountId() == null
                || source.getOwnerAccountId().equals(copier.getId()) || !colleagues(copier.getId(), source.getOwnerAccountId())) {
            throw new ResourceNotFoundException("Lesson", lessonId);
        }
        Map<Activity, ActivityVersion> published = publishedActivities(source);
        if (published.isEmpty()) {
            throw new IllegalStateException(NOTHING_PUBLISHED);
        }
        // One copy at a time per copier, like uploads (Y35): media counted against the allowance can't race.
        accounts.findForUpdate(copier.getId());
        Lesson copy = lessons.saveAndFlush(Lesson.create(source.getSubjectId(), source.getTitle(), copier.getId()).copiedFrom(source));
        Map<UUID, UUID> mediaCopies = new HashMap<>();
        Map<UUID, UUID> questionCopies = new HashMap<>();
        int questions = 0;
        for (Map.Entry<Activity, ActivityVersion> entry : published.entrySet()) {
            Activity activity = entry.getKey();
            ActivityVersion version = entry.getValue();
            Activity newActivity = activityService.createActivity(source.getSubjectId(), copy.getId(), activity.getActivityType(),
                    activity.getTitle());
            ActivityVersion draft = activityService.createDraftVersion(newActivity.getId(), version.getInstructions(),
                    version.getDifficultyLevel(), version.getEstimatedDurationSeconds());
            activityService.skillsOf(version.getId()).forEach(skillId -> activityService.tagSkill(draft.getId(), skillId));
            activityService.carryTextLanguage(draft, version);
            if (version.hasIntroduction()) {
                activityService.setIntroduction(draft.getId(), version.getIntroduction(),
                        mediaFor(version.getIntroductionImageAssetId(), copier.getId(), mediaCopies), version.getIntroductionImageDescription(),
                        mediaFor(version.getIntroductionAudioAssetId(), copier.getId(), mediaCopies));
            }
            List<ActivityService.ItemDraft> items = new ArrayList<>();
            for (ActivityItem item : activityItems.findByActivityVersionIdOrderBySequenceOrderAsc(version.getId())) {
                UUID copied = questionCopies.get(item.getQuestionVersionId());
                if (copied == null) {
                    QuestionVersion original = questionService.getVersion(item.getQuestionVersionId());
                    Question question = questionService.getQuestion(original.getQuestionId());
                    Question newQuestion = questionService.createOwnedQuestion(source.getSubjectId(), question.getQuestionType(), copier.getId());
                    // copyAsDraft brings the question's translations, its options' mapped to the copy's.
                    copied = questionService.copyAsDraft(original, newQuestion.getId(), id -> mediaFor(id, copier.getId(), mediaCopies)).getId();
                    questionCopies.put(item.getQuestionVersionId(), copied);
                    questions++;
                }
                items.add(new ActivityService.ItemDraft(copied, item.getPoints()));
            }
            activityService.replaceItems(draft.getId(), items);
            copyTranslations("ACTIVITY_VERSION", version.getId(), draft.getId());
        }
        copyTranslations("LESSON", source.getId(), copy.getId()); // its Khmer title (V85)
        record(accountId, "LESSON_COPIED", copy.getId(), Map.of("sourceLessonId", source.getId(), "activities", published.size(),
                "questions", questions, "media", mediaCopies.size()));
        return new StudioDtos.LessonSummary(copy.getId(), copy.getTitle(), copy.getSubjectId(), List.of(), published.size(), false,
                copy.getVisibility().name(), true);
    }

    // ------------------------------------------------------------------ helpers

    /** The lesson's active activities with a published version, each with its current one, by title. */
    private Map<Activity, ActivityVersion> publishedActivities(Lesson lesson) {
        Map<Activity, ActivityVersion> result = new LinkedHashMap<>();
        activityService.listByLesson(lesson.getId()).stream()
                .filter(activity -> activity.getStatus() == CatalogStatus.ACTIVE)
                .sorted(Comparator.comparing(Activity::getTitle, String.CASE_INSENSITIVE_ORDER))
                .forEach(activity -> activityVersions
                        .findFirstByActivityIdAndStatusOrderByVersionNumberDesc(activity.getId(), PublicationStatus.PUBLISHED)
                        .ifPresent(version -> result.put(activity, version)));
        return result;
    }

    /** The copier's own asset for the same file: theirs already, or a new one (which must fit their allowance). */
    private UUID mediaFor(UUID assetId, UUID copierId, Map<UUID, UUID> copies) {
        if (assetId == null) {
            return null;
        }
        return copies.computeIfAbsent(assetId, id -> {
            ContentAsset original = assets.findById(id).orElseThrow(() -> new ResourceNotFoundException("Media", id));
            if (original.getSha256() == null) {
                throw new IllegalStateException(NOTHING_PUBLISHED);
            }
            assets.lockContent(original.getSha256());
            Optional<ContentAsset> mine = assets.findBySha256AndOwnerAccountId(original.getSha256(), copierId);
            if (mine.isPresent()) {
                mine.get().restore();
                return assets.saveAndFlush(mine.get()).getId();
            }
            long size = original.getSizeBytes() == null ? 0 : original.getSizeBytes();
            storage.requireRoom(copierId, size);
            ContentAsset copy = ContentAsset.uploaded(original.getAssetType(), original.getSha256(), original.getStorageKey(),
                    original.getMimeType(), size, copierId, copierId).withDimensions(original.getWidth(), original.getHeight());
            copy.describe(original.getTitle(), original.getDescription());
            return assets.saveAndFlush(copy).getId();
        });
    }

    private void copyTranslations(String type, UUID from, UUID to) {
        jdbc.update("""
                INSERT INTO content_localizations (id, content_type, content_id, locale, field_name, localized_text)
                SELECT gen_random_uuid(), content_type, :to, locale, field_name, localized_text
                  FROM content_localizations WHERE content_type = :type AND content_id = :from
                """, Map.of("type", type, "from", from, "to", to));
    }

    /** Whether two accounts are colleagues: staff or principal of one open, paid school. */
    private boolean colleagues(UUID a, UUID b) {
        Set<UUID> theirs = schoolsOf(b);
        return paidSchoolsOf(a).keySet().stream().anyMatch(theirs::contains);
    }

    /** The open, paid schools this account is active staff or principal of, with their names. */
    private Map<UUID, String> paidSchoolsOf(UUID accountId) {
        Map<UUID, String> schools = new LinkedHashMap<>();
        jdbc.query("""
                SELECT s.id, s.name FROM schools s
                 WHERE s.status = 'ACTIVE' AND (s.principal_account_id = :a OR EXISTS (
                       SELECT 1 FROM school_staff st WHERE st.school_id = s.id AND st.account_id = :a AND st.status = 'ACTIVE'))
                """, Map.of("a", accountId), row -> {
            UUID id = row.getObject(1, UUID.class);
            if (schoolEntitlements.access(id).paid()) {
                schools.put(id, row.getString(2));
            }
        });
        return schools;
    }

    private Set<UUID> schoolsOf(UUID accountId) {
        return jdbc.queryForList("""
                SELECT s.id FROM schools s
                 WHERE s.status = 'ACTIVE' AND (s.principal_account_id = :a OR EXISTS (
                       SELECT 1 FROM school_staff st WHERE st.school_id = s.id AND st.account_id = :a AND st.status = 'ACTIVE'))
                """, Map.of("a", accountId), UUID.class).stream().collect(Collectors.toSet());
    }

    private void record(UUID accountId, String action, UUID lessonId, Map<String, Object> details) {
        Account account = accounts.findById(accountId).orElseThrow();
        audit.record(new RecordAuditEvent(null, accountId, AuditActorType.valueOf(account.getPlatformRole().name()), action, "LESSON",
                lessonId, details));
    }

}
