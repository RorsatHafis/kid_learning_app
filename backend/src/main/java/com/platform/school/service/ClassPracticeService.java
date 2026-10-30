package com.platform.school.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.repository.ActivityItemRepository;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.ActivityVersionSkillRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.learner.service.EvidenceStandard.EvidenceLevel;
import com.platform.learner.service.EvidenceStandard.SkillStanding;
import com.platform.learner.service.StandingHistory;
import com.platform.learning.entity.Enrollment;
import com.platform.learning.entity.EnrollmentStatus;
import com.platform.learning.entity.LearningPath;
import com.platform.learning.entity.LearningPathItemSource;
import com.platform.learning.entity.LearningPathItemStatus;
import com.platform.learning.repository.EnrollmentRepository;
import com.platform.learning.repository.LearningPathItemRepository;
import com.platform.learning.repository.LearningPathItemRepository.ClassPractice;
import com.platform.learning.repository.LearningPathRepository;
import com.platform.learning.service.LearningPathService;
import com.platform.learning.service.PathContentScope;
import com.platform.learning.service.SkillProgressService.CurriculumProgress;
import com.platform.learning.service.SkillProgressService.SkillProgress;
import com.platform.school.service.SchoolLearningService.ClassSnapshot;
import com.platform.school.service.SchoolLearningService.MemberRow;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A class's follow-up on one skill (X10): who stands where on it and on what evidence, the
 * practice the class can place for it, and what the class already placed and what came of it.
 *
 * <p>Practice is placed on the child's path in the class's own curriculum as a TEACHER_ASSIGNED
 * step: served before the remaining curriculum like the platform's own placed practice, scored,
 * evidenced and adapted exactly the same way. The class only ever acts inside what the family
 * granted (PD-01): its members, its assigned curricula, content those curricula may use. When the
 * class's access to a child ends, its unstarted practice is withdrawn (SchoolJoinService).
 */
@Service
public class ClassPracticeService {

    /** One request places practice for at most this many children. */
    static final int MAX_CHILDREN = 60;

    private final SchoolAccessGuard guard;
    private final SchoolLearningService learning;
    private final ActivityVersionSkillRepository activitySkills;
    private final ActivityRepository activities;
    private final ActivityVersionRepository activityVersions;
    private final ActivityItemRepository activityItems;
    private final EnrollmentRepository enrollments;
    private final LearningPathRepository paths;
    private final LearningPathItemRepository pathItems;
    private final LearningPathService pathService;
    private final PathContentScope contentScope;
    private final AccountRepository accounts;
    private final AuditService audit;
    private final StandingHistory standingHistory;

    /** Activities named in the reader's language where their Khmer is complete, like the child's path. */
    private final com.platform.content.service.ActivityTranslations activityTranslations;

    public ClassPracticeService(SchoolAccessGuard guard, SchoolLearningService learning, ActivityVersionSkillRepository activitySkills,
                                ActivityRepository activities, ActivityVersionRepository activityVersions,
                                ActivityItemRepository activityItems, EnrollmentRepository enrollments,
                                LearningPathRepository paths, LearningPathItemRepository pathItems, LearningPathService pathService,
                                PathContentScope contentScope, AccountRepository accounts, AuditService audit,
                                StandingHistory standingHistory,
                                com.platform.content.service.ActivityTranslations activityTranslations) {
        this.activityTranslations = activityTranslations;
        this.guard = guard;
        this.learning = learning;
        this.activitySkills = activitySkills;
        this.activities = activities;
        this.activityVersions = activityVersions;
        this.activityItems = activityItems;
        this.enrollments = enrollments;
        this.paths = paths;
        this.pathItems = pathItems;
        this.pathService = pathService;
        this.contentScope = contentScope;
        this.accounts = accounts;
        this.audit = audit;
        this.standingHistory = standingHistory;
    }

    // ------------------------------------------------------------------ the view

    /**
     * Practice the class placed for this skill for one child, and where it stands. {@code standingWhenAssigned}
     * (X12): the child's standing on the skill when it was placed - with today's standing, what happened afterwards.
     */
    public record PlacedPractice(UUID activityId, String activityTitle, LearningPathItemStatus status, Instant assignedAt,
                                 Instant completedAt, SkillStanding standingWhenAssigned) {
    }

    /** One member on this skill: their standing and the evidence behind it, and the class's practice. */
    public record ChildOnSkill(UUID childId, String displayName, SkillStanding standing, EvidenceLevel evidenceLevel,
                               int scoredAnswers, int correctAnswers, boolean enrolled, PlacedPractice latestPractice) {
    }

    /** An activity the class could place: published, practising the skill, allowed by the curriculum's content. */
    public record PracticeOption(UUID activityId, String title, int questionCount, Integer difficultyLevel) {
    }

    public record SkillFollowUp(UUID skillId, String skillName, String objectiveTitle, UUID curriculumId, String curriculumName,
                                List<ChildOnSkill> children, List<PracticeOption> practice) {
    }

    @Transactional(readOnly = true)
    public SkillFollowUp skill(UUID accountId, UUID classId, UUID skillId) {
        guard.requireClassStaff(accountId, classId);
        ClassSnapshot snapshot = learning.snapshot(classId);
        Located located = locate(snapshot, skillId);
        Map<UUID, PlacedPractice> latest = withStandingWhenAssigned(skillId, latestPractice(classId, skillId));

        List<ChildOnSkill> children = new ArrayList<>();
        for (MemberRow member : snapshot.members()) {
            Optional<SkillProgress> progress = skillOf(snapshot.progressByMember().get(member.childId()), located.curriculumId(), skillId);
            children.add(new ChildOnSkill(member.childId(), member.displayName(),
                    progress.map(SkillProgress::standing).orElse(SkillStanding.NOT_STARTED),
                    progress.map(SkillProgress::evidenceLevel).orElse(EvidenceLevel.NONE),
                    progress.map(SkillProgress::scoredAnswers).orElse(0), progress.map(SkillProgress::correctAnswers).orElse(0),
                    progress.isPresent(), latest.get(member.childId())));
        }
        // Those who need it most first; within a standing, by name - an order to act on, never a ranking.
        children.sort(Comparator.comparingInt((ChildOnSkill c) -> priority(c.standing()))
                .thenComparing(ChildOnSkill::displayName, String.CASE_INSENSITIVE_ORDER));
        return new SkillFollowUp(skillId, located.skillName(), located.objectiveTitle(), located.curriculumId(),
                located.curriculumName(), children, options(skillId, located.enrollmentId()));
    }

    // ------------------------------------------------------------------ the action

    public record AssignResult(List<UUID> assigned, List<UUID> alreadyWaiting, List<UUID> notOnThisPath) {
    }

    /**
     * Places one activity, practising this skill, on each chosen member's path in the class's
     * curriculum. Idempotent per child: practice still waiting for the same activity isn't placed
     * twice (a retry or double click changes nothing). Refused as a whole when the activity isn't
     * one the class may place, or a child isn't a member.
     */
    @Transactional
    public AssignResult assign(UUID accountId, UUID classId, UUID skillId, UUID activityId, List<UUID> childIds) {
        if (guard.requireClassStaff(accountId, classId).isArchived()) {
            throw new IllegalStateException(SchoolService.ARCHIVED_CLASS);
        }
        Set<UUID> chosen = new LinkedHashSet<>(childIds);
        if (chosen.isEmpty() || chosen.size() > MAX_CHILDREN) {
            throw new IllegalArgumentException("Choose between 1 and " + MAX_CHILDREN + " children");
        }
        ClassSnapshot snapshot = learning.snapshot(classId);
        Located located = locate(snapshot, skillId);
        Map<UUID, MemberRow> members = new HashMap<>();
        snapshot.members().forEach(member -> members.put(member.childId(), member));
        for (UUID childId : chosen) {
            if (!members.containsKey(childId)) {
                throw new ResourceNotFoundException("Class member", childId);
            }
        }
        ActivityVersion version = activitySkills.findPublishedPractising(skillId).stream()
                .filter(v -> v.getActivityId().equals(activityId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("That activity doesn't practise this skill, or isn't published"));

        List<UUID> assigned = new ArrayList<>();
        List<UUID> alreadyWaiting = new ArrayList<>();
        List<UUID> notOnThisPath = new ArrayList<>();
        for (UUID childId : chosen) {
            Optional<LearningPath> path = pathFor(snapshot.progressByMember().get(childId), located.curriculumId());
            if (path.isEmpty() || !contentScope.admitsActivity(contentScope.forPath(path.get().getId()), activityId)) {
                notOnThisPath.add(childId);
                continue;
            }
            // Under the path's lock: a concurrent request for the same child sees this one's item.
            pathService.lockPath(path.get().getId());
            boolean waiting = pathItems.findByLearningPathIdOrderBySequenceOrderAsc(path.get().getId()).stream()
                    .anyMatch(item -> item.getSource() == LearningPathItemSource.TEACHER_ASSIGNED
                            && item.getActivityVersionId().equals(version.getId())
                            && (item.getStatus() == LearningPathItemStatus.PENDING
                            || item.getStatus() == LearningPathItemStatus.IN_PROGRESS));
            if (waiting) {
                alreadyWaiting.add(childId);
                continue;
            }
            pathService.appendTeacherPractice(path.get().getId(), version.getId(), classId, accountId, skillId);
            assigned.add(childId);
        }
        if (!assigned.isEmpty()) {
            Account actor = accounts.findById(accountId).orElseThrow();
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("skillId", skillId.toString());
            metadata.put("activityId", activityId.toString());
            metadata.put("children", assigned.size());
            metadata.put("alreadyWaiting", alreadyWaiting.size());
            audit.record(new RecordAuditEvent(null, accountId,
                    actor.getPlatformRole() == PlatformRole.PRINCIPAL ? AuditActorType.PRINCIPAL : AuditActorType.TEACHER,
                    "CLASS_PRACTICE_ASSIGNED", "SCHOOL_CLASS", classId, metadata));
        }
        return new AssignResult(assigned, alreadyWaiting, notOnThisPath);
    }

    // ------------------------------------------------------------------ helpers

    /** Where the skill sits in the class's assigned curricula, as the class's own read reports it. */
    private record Located(UUID curriculumId, String curriculumName, String objectiveTitle, String skillName, UUID enrollmentId) {
    }

    private static Located locate(ClassSnapshot snapshot, UUID skillId) {
        for (List<CurriculumProgress> memberProgress : snapshot.progressByMember().values()) {
            for (CurriculumProgress progress : memberProgress) {
                for (var objective : progress.objectives()) {
                    for (SkillProgress skill : objective.skills()) {
                        if (skill.skillId().equals(skillId)) {
                            return new Located(progress.curriculum().id(), progress.curriculum().name(), objective.title(),
                                    skill.name(), progress.enrollmentId());
                        }
                    }
                }
            }
        }
        // Not a skill of anything this class assigns (or nobody has started it): nothing to follow up.
        throw new ResourceNotFoundException("Skill in this class", skillId);
    }

    private static Optional<SkillProgress> skillOf(List<CurriculumProgress> memberProgress, UUID curriculumId, UUID skillId) {
        if (memberProgress == null) {
            return Optional.empty();
        }
        return memberProgress.stream()
                .filter(progress -> progress.curriculum().id().equals(curriculumId))
                .flatMap(progress -> progress.objectives().stream())
                .flatMap(objective -> objective.skills().stream())
                .filter(skill -> skill.skillId().equals(skillId))
                .findFirst();
    }

    /** The member's path in the class's curriculum, while that journey is one the child is on. */
    private Optional<LearningPath> pathFor(List<CurriculumProgress> memberProgress, UUID curriculumId) {
        if (memberProgress == null) {
            return Optional.empty();
        }
        return memberProgress.stream()
                .filter(progress -> progress.curriculum().id().equals(curriculumId))
                .findFirst()
                .flatMap(progress -> enrollments.findById(progress.enrollmentId()))
                .filter(enrollment -> enrollment.getStatus() == EnrollmentStatus.ACTIVE
                        || enrollment.getStatus() == EnrollmentStatus.COMPLETED)
                .map(Enrollment::getId)
                .flatMap(paths::findByEnrollmentId);
    }

    /** Activities the class may place for this skill, judged by the curriculum's content scope. */
    private List<PracticeOption> options(UUID skillId, UUID enrollmentId) {
        Optional<PathContentScope.Scope> scope = enrollments.findById(enrollmentId)
                .map(enrollment -> contentScope.forCurriculumVersion(enrollment.getCurriculumVersionId()));
        if (scope.isEmpty()) {
            return List.of();
        }
        List<PracticeOption> options = new ArrayList<>();
        for (ActivityVersion version : activitySkills.findPublishedPractising(skillId)) {
            if (!contentScope.admitsActivity(scope.get(), version.getActivityId())) {
                continue;
            }
            activities.findById(version.getActivityId()).ifPresent(activity -> options.add(new PracticeOption(activity.getId(),
                    activityTranslations.nameForReader(activity, version), activityItems.findByActivityVersionIdOrderBySequenceOrderAsc(version.getId()).size(),
                    version.getDifficultyLevel())));
        }
        return options;
    }

    /** Each child's most recent practice the class placed for this skill. */
    private Map<UUID, PlacedPractice> latestPractice(UUID classId, UUID skillId) {
        Map<UUID, PlacedPractice> latest = new HashMap<>();
        Map<UUID, String> titles = new HashMap<>();
        Map<UUID, ActivityVersion> versionsById = new HashMap<>();
        for (ClassPractice practice : pathItems.findClassPractice(classId)) {
            if (!skillId.equals(practice.skillId()) || latest.containsKey(practice.childId())) {
                continue; // newest first: the first one per child is the latest
            }
            ActivityVersion version = versionsById.computeIfAbsent(practice.activityVersionId(),
                    id -> activityVersions.findById(id).orElseThrow());
            UUID activityId = version.getActivityId();
            String title = titles.computeIfAbsent(version.getId(), id -> activities.findById(activityId)
                    .map(a -> activityTranslations.nameForReader(a, version)).orElse(null));
            latest.put(practice.childId(), new PlacedPractice(activityId, title, practice.status(), practice.addedAt(),
                    practice.completedAt(), null));
        }
        return latest;
    }

    /** Each child's standing as it was when their practice was placed, from the evidence history (one query). */
    private Map<UUID, PlacedPractice> withStandingWhenAssigned(UUID skillId, Map<UUID, PlacedPractice> latest) {
        Map<UUID, Instant> placedAt = new HashMap<>();
        latest.forEach((childId, practice) -> placedAt.put(childId, practice.assignedAt()));
        Map<UUID, StandingHistory.Moment> then = standingHistory.forSkillAt(skillId, placedAt);
        Map<UUID, PlacedPractice> result = new HashMap<>();
        latest.forEach((childId, p) -> result.put(childId, new PlacedPractice(p.activityId(), p.activityTitle(), p.status(),
                p.assignedAt(), p.completedAt(), then.getOrDefault(childId, StandingHistory.Moment.NONE).standing())));
        return result;
    }

    private static int priority(SkillStanding standing) {
        return switch (standing) {
            case NEEDS_PRACTICE -> 0;
            case TOO_EARLY_TO_TELL -> 1;
            case PROGRESSING -> 2;
            case NOT_STARTED -> 3;
            case SECURE -> 4;
        };
    }
}
