package com.platform.content.studio;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.common.entity.CatalogStatus;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.Lesson;
import com.platform.content.entity.Question;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.LessonRepository;
import com.platform.content.repository.QuestionRepository;
import com.platform.content.service.ContentAccessGuard;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.entity.LearningObjective;
import com.platform.curriculum.entity.Subject;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.curriculum.repository.LearningObjectiveRepository;
import com.platform.curriculum.repository.SubjectRepository;
import com.platform.identity.entity.Account;
import com.platform.knowledge.entity.Skill;
import com.platform.knowledge.repository.SkillRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Editing details, archiving and restoring what the Studio manages (X14).
 *
 * <p>Archive means "no new uses": an archived subject, skill or objective can't be chosen for new content;
 * an archived curriculum is no longer offered to families; an archived activity is no longer placed on
 * paths, by the adaptive engine, by Smart Review or by a class's practice; an archived question can't be
 * added to activities. Nothing already on a child's path, published or answered changes, and nothing is
 * deleted - restoring brings it back. Pulling a version children are already served (unsafe content) is
 * a different decision (PD-04).
 *
 * <p>Who may: the catalog (subjects, skills, objectives) is an administrator's; content follows its owner
 * (ContentAccessGuard). No hidden cascades: a subject with curricula still offered, or a lesson with active
 * activities, is refused with what to do first.
 */
@Service
public class ContentLifecycleService {

    private final ContentAccessGuard guard;
    private final AuditService audit;
    private final SubjectRepository subjects;
    private final SkillRepository skills;
    private final LearningObjectiveRepository objectives;
    private final CurriculumRepository curricula;
    private final LessonRepository lessons;
    private final ActivityRepository activities;
    private final QuestionRepository questions;

    public ContentLifecycleService(ContentAccessGuard guard, AuditService audit, SubjectRepository subjects, SkillRepository skills,
                                   LearningObjectiveRepository objectives, CurriculumRepository curricula, LessonRepository lessons,
                                   ActivityRepository activities, QuestionRepository questions) {
        this.guard = guard;
        this.audit = audit;
        this.subjects = subjects;
        this.skills = skills;
        this.objectives = objectives;
        this.curricula = curricula;
        this.lessons = lessons;
        this.activities = activities;
        this.questions = questions;
    }

    // ------------------------------------------------------------------ the catalog (administrators)

    @Transactional
    public void updateSubject(UUID accountId, UUID subjectId, String name, String description) {
        Account admin = guard.requireCatalogAdmin(accountId);
        Subject subject = subject(subjectId);
        subject.rename(name.strip(), blankToNull(description));
        record(admin, "CONTENT_UPDATED", "SUBJECT", subjectId, Map.of("name", subject.getName()));
    }

    @Transactional
    public void archiveSubject(UUID accountId, UUID subjectId) {
        Account admin = guard.requireCatalogAdmin(accountId);
        Subject subject = subject(subjectId);
        if (curricula.existsBySubjectIdAndStatus(subjectId, CatalogStatus.ACTIVE)) {
            throw new IllegalStateException("Families are still offered this subject's curricula. Archive those curricula first.");
        }
        transition(subject.getStatus(), CatalogStatus.ACTIVE, "archived");
        subject.archive();
        record(admin, "CONTENT_ARCHIVED", "SUBJECT", subjectId, Map.of("name", subject.getName()));
    }

    @Transactional
    public void restoreSubject(UUID accountId, UUID subjectId) {
        Account admin = guard.requireCatalogAdmin(accountId);
        Subject subject = subject(subjectId);
        transition(subject.getStatus(), CatalogStatus.ARCHIVED, "restored");
        subject.restore();
        record(admin, "CONTENT_RESTORED", "SUBJECT", subjectId, Map.of("name", subject.getName()));
    }

    @Transactional
    public void updateSkill(UUID accountId, UUID skillId, String name, String description) {
        Account admin = guard.requireCatalogAdmin(accountId);
        Skill skill = skill(skillId);
        skill.rename(name, description);
        record(admin, "CONTENT_UPDATED", "SKILL", skillId, Map.of("name", skill.getName()));
    }

    @Transactional
    public void archiveSkill(UUID accountId, UUID skillId, boolean archive) {
        Account admin = guard.requireCatalogAdmin(accountId);
        Skill skill = skill(skillId);
        transition(skill.getStatus(), archive ? CatalogStatus.ACTIVE : CatalogStatus.ARCHIVED, archive ? "archived" : "restored");
        if (archive) {
            skill.archive();
        } else {
            skill.restore();
        }
        record(admin, archive ? "CONTENT_ARCHIVED" : "CONTENT_RESTORED", "SKILL", skillId, Map.of("name", skill.getName()));
    }

    @Transactional
    public void updateObjective(UUID accountId, UUID objectiveId, String title, String description) {
        Account admin = guard.requireCatalogAdmin(accountId);
        LearningObjective objective = objective(objectiveId);
        objective.rename(title, description);
        record(admin, "CONTENT_UPDATED", "LEARNING_OBJECTIVE", objectiveId, Map.of("title", objective.getTitle()));
    }

    @Transactional
    public void archiveObjective(UUID accountId, UUID objectiveId, boolean archive) {
        Account admin = guard.requireCatalogAdmin(accountId);
        LearningObjective objective = objective(objectiveId);
        transition(objective.getStatus(), archive ? CatalogStatus.ACTIVE : CatalogStatus.ARCHIVED, archive ? "archived" : "restored");
        if (archive) {
            objective.archive();
        } else {
            objective.restore();
        }
        record(admin, archive ? "CONTENT_ARCHIVED" : "CONTENT_RESTORED", "LEARNING_OBJECTIVE", objectiveId,
                Map.of("title", objective.getTitle()));
    }

    // ------------------------------------------------------------------ content (its owner)

    @Transactional
    public void renameCurriculum(UUID accountId, UUID curriculumId, String name) {
        Curriculum curriculum = guard.requireCurriculumWrite(accountId, curriculumId);
        curriculum.rename(name.strip());
        record(guard.requireAuthor(accountId), "CONTENT_UPDATED", "CURRICULUM", curriculumId, Map.of("name", curriculum.getName()));
    }

    @Transactional
    public void archiveCurriculum(UUID accountId, UUID curriculumId, boolean archive) {
        Curriculum curriculum = guard.requireCurriculumWrite(accountId, curriculumId);
        transition(curriculum.getStatus(), archive ? CatalogStatus.ACTIVE : CatalogStatus.ARCHIVED, archive ? "archived" : "restored");
        if (archive) {
            curriculum.archive();
        } else {
            curriculum.restore();
        }
        record(guard.requireAuthor(accountId), archive ? "CONTENT_ARCHIVED" : "CONTENT_RESTORED", "CURRICULUM", curriculumId,
                Map.of("name", curriculum.getName()));
    }

    @Transactional
    public void retitleLesson(UUID accountId, UUID lessonId, String title) {
        Lesson lesson = guard.requireLessonWrite(accountId, lessonId);
        lesson.retitle(title.strip());
        record(guard.requireAuthor(accountId), "CONTENT_UPDATED", "LESSON", lessonId, Map.of("title", lesson.getTitle()));
    }

    @Transactional
    public void archiveLesson(UUID accountId, UUID lessonId, boolean archive) {
        Lesson lesson = guard.requireLessonWrite(accountId, lessonId);
        if (archive && activities.existsByLessonIdAndStatus(lessonId, CatalogStatus.ACTIVE)) {
            throw new IllegalStateException("This lesson still has activities in use. Archive its activities first.");
        }
        transition(lesson.getStatus(), archive ? CatalogStatus.ACTIVE : CatalogStatus.ARCHIVED, archive ? "archived" : "restored");
        if (archive) {
            lesson.archive();
        } else {
            lesson.restore();
        }
        record(guard.requireAuthor(accountId), archive ? "CONTENT_ARCHIVED" : "CONTENT_RESTORED", "LESSON", lessonId,
                Map.of("title", lesson.getTitle()));
    }

    @Transactional
    public void retitleActivity(UUID accountId, UUID activityId, String title) {
        Activity activity = guard.requireActivityWrite(accountId, activityId);
        activity.retitle(title.strip());
        record(guard.requireAuthor(accountId), "CONTENT_UPDATED", "ACTIVITY", activityId, Map.of("title", activity.getTitle()));
    }

    @Transactional
    public void archiveActivity(UUID accountId, UUID activityId, boolean archive) {
        Activity activity = guard.requireActivityWrite(accountId, activityId);
        if (!archive && lessons.findById(activity.getLessonId()).map(l -> l.getStatus() == CatalogStatus.ARCHIVED).orElse(false)) {
            throw new IllegalStateException("Its lesson is archived. Restore the lesson first.");
        }
        transition(activity.getStatus(), archive ? CatalogStatus.ACTIVE : CatalogStatus.ARCHIVED, archive ? "archived" : "restored");
        if (archive) {
            activity.archive();
        } else {
            activity.restore();
        }
        record(guard.requireAuthor(accountId), archive ? "CONTENT_ARCHIVED" : "CONTENT_RESTORED", "ACTIVITY", activityId,
                Map.of("title", activity.getTitle()));
    }

    @Transactional
    public void archiveQuestion(UUID accountId, UUID questionId, boolean archive) {
        Question question = guard.requireQuestionWrite(accountId, questionId);
        transition(question.getStatus(), archive ? CatalogStatus.ACTIVE : CatalogStatus.ARCHIVED, archive ? "archived" : "restored");
        if (archive) {
            question.archive();
        } else {
            question.restore();
        }
        record(guard.requireAuthor(accountId), archive ? "CONTENT_ARCHIVED" : "CONTENT_RESTORED", "QUESTION", questionId, Map.of());
    }

    // ------------------------------------------------------------------ helpers

    /** Archiving what is archived, or restoring what isn't, is a conflict said plainly (409), not an entity assertion. */
    private static void transition(CatalogStatus current, CatalogStatus required, String attempted) {
        if (current != required) {
            throw new IllegalStateException(required == CatalogStatus.ACTIVE ? "It is already archived." : "It isn't archived.");
        }
    }

    private Subject subject(UUID id) {
        return subjects.findById(id).orElseThrow(() -> new ResourceNotFoundException("Subject", id));
    }

    private Skill skill(UUID id) {
        return skills.findById(id).orElseThrow(() -> new ResourceNotFoundException("Skill", id));
    }

    private LearningObjective objective(UUID id) {
        return objectives.findById(id).orElseThrow(() -> new ResourceNotFoundException("LearningObjective", id));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private void record(Account actor, String action, String resourceType, UUID resourceId, Map<String, Object> metadata) {
        audit.record(new RecordAuditEvent(null, actor.getId(), AuditActorType.valueOf(actor.getPlatformRole().name()), action, resourceType,
                resourceId, new HashMap<>(metadata)));
    }

}
