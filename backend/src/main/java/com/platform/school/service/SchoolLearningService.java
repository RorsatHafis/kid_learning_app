package com.platform.school.service;

import com.platform.child.entity.Child;
import com.platform.child.repository.ChildRepository;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.learner.service.EvidenceStandard.EvidenceLevel;
import com.platform.learner.service.EvidenceStandard.SkillStanding;
import com.platform.learning.service.LearningJourneyService;
import com.platform.learning.service.LearningJourneyService.ChapterState;
import com.platform.learning.service.LearningJourneyService.EnrollmentSummary;
import com.platform.learning.service.SkillProgressService;
import com.platform.learning.service.SkillProgressService.CurriculumProgress;
import com.platform.learning.service.SkillProgressService.ObjectiveStanding;
import com.platform.school.entity.ClassCurriculumAssignment;
import com.platform.school.entity.ClassCurriculumAssignmentStatus;
import com.platform.school.entity.ClassEnrollment;
import com.platform.school.repository.ClassCurriculumAssignmentRepository;
import com.platform.school.repository.ClassEnrollmentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * What a class's staff see of its children (PD-01, PLATFORM-EXPANSION.md §4): each member's name
 * and, for the curricula the class assigned only, their journey (steps, chapters) and skill
 * standings with the evidence behind them. Other journeys, parent insights, the activity history,
 * age and family details are never read here. Members are children whose family's grant is ACTIVE.
 */
@Service
public class SchoolLearningService {

    private final SchoolAccessGuard guard;
    private final ClassEnrollmentRepository classEnrollments;
    private final ClassCurriculumAssignmentRepository classCurricula;
    private final CurriculumRepository curricula;
    private final SubjectRepository subjects;
    private final ChildRepository children;
    private final LearningJourneyService journeys;
    private final SkillProgressService skills;

    private final com.platform.content.service.CatalogNames catalogNames;

    public SchoolLearningService(SchoolAccessGuard guard, ClassEnrollmentRepository classEnrollments,
                                 ClassCurriculumAssignmentRepository classCurricula, CurriculumRepository curricula,
                                 SubjectRepository subjects, ChildRepository children, LearningJourneyService journeys,
                                 SkillProgressService skills, com.platform.content.service.CatalogNames catalogNames) {
        this.catalogNames = catalogNames;
        this.guard = guard;
        this.classEnrollments = classEnrollments;
        this.classCurricula = classCurricula;
        this.curricula = curricula;
        this.subjects = subjects;
        this.children = children;
        this.journeys = journeys;
        this.skills = skills;
    }

    public record ClassCurriculum(UUID curriculumId, String name, String subjectName) {
    }

    public record ChapterView(int position, String title, int completedSteps, int totalSteps, ChapterState state) {
    }

    /** A member's journey in one assigned curriculum; {@code started} is false when they have none in it. */
    public record JourneyView(UUID curriculumId, String curriculumName, boolean started, int completedSteps, int totalSteps,
                              ChapterView currentChapter, int chapterCount, List<ChapterView> chapters) {
    }

    public record Member(UUID childId, String displayName, Instant joinedAt) {
    }

    public record MemberRow(UUID childId, String displayName, Instant joinedAt, List<JourneyView> journeys) {
    }

    /** How many members stand where on one skill; members without the journey count as not started. */
    public record SkillTally(UUID skillId, String name, int notStarted, int tooEarlyToTell, int needsPractice,
                             int progressing, int secure) {
    }

    public record ObjectiveTally(String title, List<SkillTally> skills) {
    }

    public record CurriculumTally(UUID curriculumId, String name, List<ObjectiveTally> objectives) {
    }

    public record ClassLearning(List<ClassCurriculum> curricula, List<MemberRow> members, List<CurriculumTally> skills) {
    }

    public record SkillView(UUID skillId, String name, SkillStanding standing, EvidenceLevel evidenceLevel,
                            int scoredAnswers, int correctAnswers) {
    }

    public record ObjectiveView(String title, ObjectiveStanding standing, List<SkillView> skills) {
    }

    public record CurriculumSkills(UUID curriculumId, String name, List<ObjectiveView> objectives) {
    }

    public record MemberLearning(UUID childId, String displayName, Instant joinedAt, List<JourneyView> journeys,
                                 List<CurriculumSkills> skills) {
    }

    // ------------------------------------------------------------------ reads

    @Transactional(readOnly = true)
    public List<Member> roster(UUID accountId, UUID classId) {
        guard.requireClassStaff(accountId, classId);
        return members(classId);
    }

    @Transactional(readOnly = true)
    public List<ClassCurriculum> assigned(UUID accountId, UUID classId) {
        guard.requireClassStaff(accountId, classId);
        return assignedCurricula(classId);
    }

    /** Every member's journeys in the assigned curricula, and how the class stands on each skill. */
    @Transactional(readOnly = true)
    public ClassLearning classLearning(UUID accountId, UUID classId) {
        guard.requireClassStaff(accountId, classId);
        ClassSnapshot snapshot = snapshot(classId);
        List<CurriculumTally> tallies = snapshot.assigned().stream()
                .map(curriculum -> tally(curriculum, snapshot.progressByMember()))
                .toList();
        return new ClassLearning(snapshot.assigned(), snapshot.members(), tallies);
    }

    /**
     * A class's assigned curricula and, for each member, their journeys and skill progress in them -
     * the one read behind the class workspace and the staff overview. Callers check the reader
     * first ({@link SchoolAccessGuard#requireClassStaff}); it isn't part of the public API.
     */
    record ClassSnapshot(List<ClassCurriculum> assigned, List<MemberRow> members,
                         Map<UUID, List<CurriculumProgress>> progressByMember) {
    }

    ClassSnapshot snapshot(UUID classId) {
        List<ClassCurriculum> assigned = assignedCurricula(classId);
        Set<UUID> assignedIds = assigned.stream().map(ClassCurriculum::curriculumId).collect(Collectors.toSet());
        List<Member> members = members(classId);
        // Every member's paths and skills read together (X20): a fixed number of statements however big the class.
        List<UUID> childIds = members.stream().map(Member::childId).toList();
        Map<UUID, List<EnrollmentSummary>> summaries = journeys.summariesFor(childIds);
        Map<UUID, List<CurriculumProgress>> progress = skills.progressForAll(childIds);
        List<MemberRow> rows = new ArrayList<>();
        Map<UUID, List<CurriculumProgress>> progressByMember = new LinkedHashMap<>();
        for (Member member : members) {
            rows.add(new MemberRow(member.childId(), member.displayName(), member.joinedAt(),
                    journeyViews(summaries.getOrDefault(member.childId(), List.of()), assigned)));
            progressByMember.put(member.childId(), latestPerCurriculum(progress.getOrDefault(member.childId(), List.of()), assignedIds));
        }
        return new ClassSnapshot(assigned, rows, progressByMember);
    }

    /** One member, in more detail: their assigned journeys and every skill standing in them. */
    @Transactional(readOnly = true)
    public MemberLearning memberLearning(UUID accountId, UUID classId, UUID childId) {
        guard.requireClassStaff(accountId, classId);
        Member member = members(classId).stream().filter(m -> m.childId().equals(childId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Class member", childId));
        List<ClassCurriculum> assigned = assignedCurricula(classId);
        Set<UUID> assignedIds = assigned.stream().map(ClassCurriculum::curriculumId).collect(Collectors.toSet());
        List<CurriculumSkills> skillViews = latestPerCurriculum(skills.progressFor(childId), assignedIds).stream()
                .map(progress -> new CurriculumSkills(progress.curriculum().id(), progress.curriculum().name(),
                        progress.objectives().stream().map(objective -> new ObjectiveView(objective.title(), objective.standing(),
                                objective.skills().stream().map(skill -> new SkillView(skill.skillId(), skill.name(), skill.standing(),
                                        skill.evidenceLevel(), skill.scoredAnswers(), skill.correctAnswers())).toList())).toList()))
                .toList();
        return new MemberLearning(member.childId(), member.displayName(), member.joinedAt(),
                journeyViews(journeys.summariesFor(List.of(childId)).getOrDefault(childId, List.of()), assigned), skillViews);
    }

    // ------------------------------------------------------------------ assembly

    private List<Member> members(UUID classId) {
        List<ClassEnrollment> enrollments = classEnrollments.findMembers(classId);
        Map<UUID, Child> byId = children.findAllById(enrollments.stream().map(ClassEnrollment::getChildId).toList()).stream()
                .collect(Collectors.toMap(Child::getId, child -> child));
        return enrollments.stream()
                .filter(enrollment -> byId.containsKey(enrollment.getChildId()))
                .map(enrollment -> new Member(enrollment.getChildId(), byId.get(enrollment.getChildId()).getDisplayName(),
                        enrollment.getUpdatedAt()))
                .sorted(Comparator.comparing(Member::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<ClassCurriculum> assignedCurricula(UUID classId) {
        List<ClassCurriculum> assigned = new ArrayList<>();
        for (ClassCurriculumAssignment assignment : classCurricula.findBySchoolClassIdAndStatusOrderByCreatedAtAsc(
                classId, ClassCurriculumAssignmentStatus.ACTIVE)) {
            Curriculum curriculum = curricula.findById(assignment.getCurriculumId()).orElseThrow();
            // In the reader's language where its Khmer is written (LOCALIZATION.md, catalog names).
            String subjectName = subjects.findById(curriculum.getSubjectId())
                    .map(subject -> catalogNames.name(com.platform.content.entity.LocalizableContentType.SUBJECT, subject.getId(), subject.getName())).orElse(null);
            assigned.add(new ClassCurriculum(curriculum.getId(),
                    catalogNames.name(com.platform.content.entity.LocalizableContentType.CURRICULUM, curriculum.getId(), curriculum.getName()), subjectName));
        }
        return assigned;
    }

    /** The member's journey in each assigned curriculum: the most recent one if there were several versions. */
    private List<JourneyView> journeyViews(List<EnrollmentSummary> overviews, List<ClassCurriculum> assigned) {
        return assigned.stream().map(curriculum -> {
            Optional<EnrollmentSummary> overview = overviews.stream()
                    .filter(o -> o.curriculum().id().equals(curriculum.curriculumId()))
                    .max(Comparator.comparing(EnrollmentSummary::enrolledAt));
            return overview.map(o -> new JourneyView(curriculum.curriculumId(), curriculum.name(), true, o.completedSteps(),
                            o.totalSteps(), o.currentChapter() == null ? null : chapter(o.currentChapter()), o.chapters().size(),
                            o.chapters().stream().map(SchoolLearningService::chapter).toList()))
                    .orElse(new JourneyView(curriculum.curriculumId(), curriculum.name(), false, 0, 0, null, 0, List.of()));
        }).toList();
    }

    private static ChapterView chapter(LearningJourneyService.Chapter chapter) {
        return new ChapterView(chapter.position(), chapter.title(), chapter.completedSteps(), chapter.totalSteps(), chapter.state());
    }

    /** Only assigned curricula, and for each the most recent journey's progress. */
    private static List<CurriculumProgress> latestPerCurriculum(List<CurriculumProgress> all, Set<UUID> assignedIds) {
        Map<UUID, CurriculumProgress> latest = new LinkedHashMap<>();
        for (CurriculumProgress progress : all) {
            if (assignedIds.contains(progress.curriculum().id())) {
                latest.put(progress.curriculum().id(), progress);
            }
        }
        return List.copyOf(latest.values());
    }

    private static CurriculumTally tally(ClassCurriculum curriculum, Map<UUID, List<CurriculumProgress>> progressByMember) {
        // Objectives and skills in curriculum order, as the first member with the journey reports them.
        Map<String, Map<UUID, int[]>> counts = new LinkedHashMap<>();
        Map<UUID, String> names = new LinkedHashMap<>();
        int withJourney = 0;
        for (List<CurriculumProgress> memberProgress : progressByMember.values()) {
            Optional<CurriculumProgress> progress = memberProgress.stream()
                    .filter(p -> p.curriculum().id().equals(curriculum.curriculumId())).findFirst();
            if (progress.isEmpty()) {
                continue;
            }
            withJourney++;
            progress.get().objectives().forEach(objective -> objective.skills().forEach(skill -> {
                names.putIfAbsent(skill.skillId(), skill.name());
                counts.computeIfAbsent(objective.title(), title -> new LinkedHashMap<>())
                        .computeIfAbsent(skill.skillId(), id -> new int[SkillStanding.values().length])[skill.standing().ordinal()]++;
            }));
        }
        int withoutJourney = progressByMember.size() - withJourney;
        List<ObjectiveTally> objectives = counts.entrySet().stream().map(objective -> new ObjectiveTally(objective.getKey(),
                objective.getValue().entrySet().stream().map(skill -> {
                    int[] c = skill.getValue();
                    return new SkillTally(skill.getKey(), names.get(skill.getKey()),
                            c[SkillStanding.NOT_STARTED.ordinal()] + withoutJourney, c[SkillStanding.TOO_EARLY_TO_TELL.ordinal()],
                            c[SkillStanding.NEEDS_PRACTICE.ordinal()], c[SkillStanding.PROGRESSING.ordinal()],
                            c[SkillStanding.SECURE.ordinal()]);
                }).toList())).toList();
        return new CurriculumTally(curriculum.curriculumId(), curriculum.name(), objectives);
    }
}
