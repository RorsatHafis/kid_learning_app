package com.platform.school.service;

import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.learner.service.EvidenceStandard.SkillStanding;
import com.platform.learning.service.SkillProgressService.CurriculumProgress;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolChildAccessStatus;
import com.platform.school.entity.SchoolClass;
import com.platform.school.entity.TeacherAssignmentStatus;
import com.platform.school.repository.ClassJoinCodeRepository;
import com.platform.school.repository.SchoolChildAccessRepository;
import com.platform.school.repository.SchoolClassRepository;
import com.platform.school.repository.SchoolRepository;
import com.platform.school.repository.TeacherAssignmentRepository;
import com.platform.school.service.SchoolLearningService.ClassCurriculum;
import com.platform.school.service.SchoolLearningService.ClassSnapshot;
import com.platform.school.service.SchoolLearningService.JourneyView;
import com.platform.school.service.SchoolLearningService.MemberRow;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * The staff home (X7): the classes a teacher teaches, or every class of a principal's schools,
 * each summarised from the same read as its class workspace - never more than that workspace
 * shows. A principal also gets each school's setup gaps. An administrator has no class view
 * (CLAUDE.md §10/§22: no privileged shortcut to children's learning).
 */
@Service
public class SchoolOverviewService {

    private final SchoolAccessGuard guard;
    private final SchoolLearningService learning;
    private final SchoolRepository schools;
    private final SchoolClassRepository classes;
    private final TeacherAssignmentRepository assignments;
    private final ClassJoinCodeRepository joinCodes;
    private final SchoolChildAccessRepository grants;

    public SchoolOverviewService(SchoolAccessGuard guard, SchoolLearningService learning, SchoolRepository schools,
                                 SchoolClassRepository classes, TeacherAssignmentRepository assignments,
                                 ClassJoinCodeRepository joinCodes, SchoolChildAccessRepository grants) {
        this.guard = guard;
        this.learning = learning;
        this.schools = schools;
        this.classes = classes;
        this.assignments = assignments;
        this.joinCodes = joinCodes;
        this.grants = grants;
    }

    /**
     * What asks for the staff's attention in a class, from the children's answers in its assigned
     * curricula only.
     *
     * @param childrenNeedingPractice children with at least one skill whose standing is "needs practice"
     * @param skillsNeedingPractice   distinct skills at least one child needs to practise
     * @param childrenNotStarted      children with no step done in any assigned curriculum (0 when none is assigned)
     */
    public record Attention(int childrenNeedingPractice, int skillsNeedingPractice, int childrenNotStarted) {
    }

    /** {@code open}: a code families can use now; {@code expiresAt} of the unrevoked code, open or expired. */
    public record JoinCodeState(boolean open, Instant expiresAt) {
    }

    public record ClassOverview(UUID classId, String className, UUID schoolId, String schoolName, int children,
                                List<ClassCurriculum> curricula, int teachers, JoinCodeState joinCode, Attention attention) {
    }

    /** A principal's school at a glance: who it reaches and what is still to set up. */
    public record SchoolOverview(UUID schoolId, String name, long childrenWithAccess, int classes, int classesWithoutTeacher,
                                 int classesWithoutCurriculum, int classesWithoutOpenCode) {
    }

    public record StaffOverview(List<SchoolOverview> schools, List<ClassOverview> classes) {
    }

    @Transactional(readOnly = true)
    public StaffOverview overview(UUID accountId) {
        Account account = guard.requireStaff(accountId);
        Instant now = Instant.now();
        List<School> run = switch (account.getPlatformRole()) {
            case PRINCIPAL -> schools.findByPrincipalAccountId(accountId).stream()
                    .sorted(Comparator.comparing(School::getName, String.CASE_INSENSITIVE_ORDER)).toList();
            case TEACHER -> List.of();
            default -> throw new AccessDeniedException("Only teachers and principals have a class overview");
        };
        List<SchoolClass> visible = account.getPlatformRole() == PlatformRole.PRINCIPAL
                ? run.stream().flatMap(school -> classes.findBySchoolId(school.getId()).stream()).toList()
                : assignments.findByTeacherAccountIdAndStatus(accountId, TeacherAssignmentStatus.ACTIVE).stream()
                        .map(assignment -> classes.findById(assignment.getSchoolClassId()).orElse(null))
                        .filter(Objects::nonNull)
                        .toList();

        List<ClassOverview> overviews = new ArrayList<>();
        for (SchoolClass schoolClass : visible) {
            guard.requireClassStaff(accountId, schoolClass.getId());
            overviews.add(overview(schoolClass, now));
        }
        overviews.sort(Comparator.comparing(ClassOverview::schoolName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(ClassOverview::className, String.CASE_INSENSITIVE_ORDER));

        List<SchoolOverview> schoolOverviews = run.stream().map(school -> {
            List<ClassOverview> ofSchool = overviews.stream().filter(c -> c.schoolId().equals(school.getId())).toList();
            return new SchoolOverview(school.getId(), school.getName(),
                    grants.countBySchoolIdAndStatus(school.getId(), SchoolChildAccessStatus.ACTIVE), ofSchool.size(),
                    (int) ofSchool.stream().filter(c -> c.teachers() == 0).count(),
                    (int) ofSchool.stream().filter(c -> c.curricula().isEmpty()).count(),
                    (int) ofSchool.stream().filter(c -> !c.joinCode().open()).count());
        }).toList();
        return new StaffOverview(schoolOverviews, overviews);
    }

    private ClassOverview overview(SchoolClass schoolClass, Instant now) {
        School school = schools.findById(schoolClass.getSchoolId()).orElseThrow();
        ClassSnapshot snapshot = learning.snapshot(schoolClass.getId());
        JoinCodeState code = joinCodes.findBySchoolClassIdAndRevokedAtIsNull(schoolClass.getId())
                .map(joinCode -> new JoinCodeState(joinCode.isOpenAt(now), joinCode.getExpiresAt()))
                .orElse(new JoinCodeState(false, null));
        int teachers = assignments.findBySchoolClassIdAndStatus(schoolClass.getId(), TeacherAssignmentStatus.ACTIVE).size();
        return new ClassOverview(schoolClass.getId(), schoolClass.getName(), school.getId(), school.getName(),
                snapshot.members().size(), snapshot.assigned(), teachers, code, attention(snapshot));
    }

    static Attention attention(ClassSnapshot snapshot) {
        int childrenNeeding = 0;
        Set<UUID> skills = new HashSet<>();
        for (Map.Entry<UUID, List<CurriculumProgress>> member : snapshot.progressByMember().entrySet()) {
            boolean needs = false;
            for (CurriculumProgress progress : member.getValue()) {
                for (var objective : progress.objectives()) {
                    for (var skill : objective.skills()) {
                        if (skill.standing() == SkillStanding.NEEDS_PRACTICE) {
                            needs = true;
                            skills.add(skill.skillId());
                        }
                    }
                }
            }
            if (needs) {
                childrenNeeding++;
            }
        }
        int notStarted = snapshot.assigned().isEmpty() ? 0 : (int) snapshot.members().stream()
                .filter(SchoolOverviewService::notStarted)
                .count();
        return new Attention(childrenNeeding, skills.size(), notStarted);
    }

    private static boolean notStarted(MemberRow member) {
        return member.journeys().stream().mapToInt(JourneyView::completedSteps).sum() == 0;
    }
}
