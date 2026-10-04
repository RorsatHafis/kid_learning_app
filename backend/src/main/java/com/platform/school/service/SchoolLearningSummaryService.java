package com.platform.school.service;

import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.school.entity.School;
import com.platform.school.entity.SchoolClass;
import com.platform.school.repository.SchoolClassRepository;
import com.platform.school.service.ClassTrendService.ClassTrend;
import com.platform.school.service.ClassTrendService.Movement;
import com.platform.school.service.ClassTrendService.TrendPoint;
import com.platform.school.service.ClassTrendService.WeekOfAnswers;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.UUID;

/**
 * Learning across a school (X21) - the principal's "which classes are moving, which are stuck, and
 * where are children not answering?". Each active class's trend over the last weeks, read with the
 * class's own rules (ClassTrendService: a child counts from when they joined; the evidence standard
 * of every other standing): who is in it, how many answered this week, where its children stand on
 * its skills now and what moved. Counts only - no child is named - and for the school's principal
 * only: an administrator runs the platform, not its children's learning (PD-01).
 */
@Service
public class SchoolLearningSummaryService {

    public static final int WEEKS = 4;

    public record ClassLearning(UUID classId, String className, int children, int skills, int answeringThisWeek,
                                int answersThisWeek, TrendPoint now, Movement movement) {
    }

    public record SchoolLearning(UUID schoolId, String schoolName, int weeks, List<ClassLearning> classes) {
    }

    private final SchoolAccessGuard guard;
    private final SchoolClassRepository classes;
    private final ClassTrendService trends;

    public SchoolLearningSummaryService(SchoolAccessGuard guard, SchoolClassRepository classes, ClassTrendService trends) {
        this.guard = guard;
        this.classes = classes;
        this.trends = trends;
    }

    @Transactional(readOnly = true)
    public SchoolLearning learning(UUID accountId, UUID schoolId) {
        Account account = guard.requireStaff(accountId);
        if (account.getPlatformRole() != PlatformRole.PRINCIPAL) {
            throw new AccessDeniedException("Only the school's principal sees learning across the school");
        }
        School school = guard.requireSchoolManage(accountId, schoolId);
        List<ClassLearning> rows = classes.findBySchoolId(schoolId).stream()
                .filter(schoolClass -> !schoolClass.isArchived())
                .sorted(Comparator.comparing(SchoolClass::getName, String.CASE_INSENSITIVE_ORDER))
                .map(schoolClass -> {
                    // The class's own read, with its own check (principal of the class's school).
                    ClassTrend trend = trends.trend(accountId, schoolClass.getId(), WEEKS);
                    TrendPoint now = trend.points().getLast();
                    WeekOfAnswers lastWeek = trend.answers().getLast();
                    return new ClassLearning(schoolClass.getId(), schoolClass.getName(), now.children(), trend.skills(),
                            lastWeek.childrenAnswering(), lastWeek.answers(), now, trend.movement());
                })
                .toList();
        return new SchoolLearning(school.getId(), school.getName(), WEEKS, rows);
    }

}
