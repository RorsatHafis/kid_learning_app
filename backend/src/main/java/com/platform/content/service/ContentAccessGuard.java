package com.platform.content.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.Lesson;
import com.platform.content.entity.Question;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.LessonRepository;
import com.platform.content.repository.QuestionRepository;
import com.platform.curriculum.entity.Curriculum;
import com.platform.curriculum.repository.CurriculumRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.school.repository.SchoolStaffRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Central server-side authorization for teacher-managed content.
 *
 * Null owner means system/YulMe-owned content and is intentionally not writable
 * by ordinary teachers. ADMIN may manage any teacher's content; a PRINCIPAL only the
 * content of teachers who are ACTIVE staff at a school they run (TD-17) - never another
 * school's teachers.
 *
 * Battle 4: system-owned (null-owner) content is writable by ADMIN only - the same
 * rule {@code TeacherAuthoringController#curriculumOwner} already applies to
 * system-owned curricula. Previously a null owner short-circuited the ownership
 * check and let any non-PARENT account through.
 */
@Service
public class ContentAccessGuard {
    private final AccountRepository accounts;
    private final LessonRepository lessons;
    private final QuestionRepository questions;
    private final ActivityRepository activities;
    private final CurriculumRepository curricula;
    private final SchoolStaffRepository schoolStaff;

    public ContentAccessGuard(AccountRepository accounts, LessonRepository lessons,
                              QuestionRepository questions, ActivityRepository activities,
                              CurriculumRepository curricula, SchoolStaffRepository schoolStaff) {
        this.accounts = accounts;
        this.lessons = lessons;
        this.questions = questions;
        this.activities = activities;
        this.curricula = curricula;
        this.schoolStaff = schoolStaff;
    }

    public Account requireAuthor(UUID accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() == PlatformRole.PARENT) {
            throw new AccessDeniedException("Only staff may author content");
        }
        return account;
    }

    /**
     * N7: the owner recorded on content this account authors. ADMIN authors platform
     * content, which is ownerless (writable by ADMIN only, placed on family paths);
     * everyone else owns what they author.
     */
    public UUID authoringOwner(Account account) {
        return account.getPlatformRole() == PlatformRole.ADMIN ? null : account.getId();
    }

    /** Only ADMIN manages the platform catalog: teachers do not redefine official skill semantics (directive §14.1). */
    public Account requireCatalogAdmin(UUID accountId) {
        Account account = requireAuthor(accountId);
        if (account.getPlatformRole() != PlatformRole.ADMIN) {
            throw new AccessDeniedException("Only an administrator may change the skill and objective catalog");
        }
        return account;
    }

    /** A curriculum's owner, or ADMIN; platform (ownerless) curricula are ADMIN-only. */
    public Curriculum requireCurriculumWrite(UUID accountId, UUID curriculumId) {
        Account account = requireAuthor(accountId);
        Curriculum curriculum = curricula.findById(curriculumId)
                .orElseThrow(() -> new ResourceNotFoundException("Curriculum", curriculumId));
        if (account.getPlatformRole() != PlatformRole.ADMIN && !account.getId().equals(curriculum.getOwnerAccountId())) {
            throw new AccessDeniedException("You do not own this curriculum");
        }
        return curriculum;
    }

    public Lesson requireLessonWrite(UUID accountId, UUID lessonId) {
        Account account = requireAuthor(accountId);
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        requireOwnerWrite(account, lesson.getOwnerAccountId());
        return lesson;
    }

    public Activity requireActivityWrite(UUID accountId, UUID activityId) {
        Activity activity = activities.findById(activityId)
                .orElseThrow(() -> new ResourceNotFoundException("Activity", activityId));
        if (activity.getLessonId() == null) {
            throw new AccessDeniedException("Standalone activity is not teacher-managed");
        }
        requireLessonWrite(accountId, activity.getLessonId());
        return activity;
    }

    public Question requireQuestionWrite(UUID accountId, UUID questionId) {
        Account account = requireAuthor(accountId);
        Question question = questions.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question", questionId));
        requireOwnerWrite(account, question.getOwnerAccountId());
        return question;
    }

    private void requireOwnerWrite(Account account, UUID ownerAccountId) {
        PlatformRole role = account.getPlatformRole();
        if (ownerAccountId == null) {
            if (role != PlatformRole.ADMIN) {
                throw new AccessDeniedException("System content is not teacher-managed");
            }
            return;
        }
        if (ownerAccountId.equals(account.getId()) || role == PlatformRole.ADMIN) {
            return;
        }
        if (role == PlatformRole.PRINCIPAL && schoolStaff.isActiveStaffOfPrincipal(ownerAccountId, account.getId())) {
            return;
        }
        throw new AccessDeniedException("Content belongs to another teacher");
    }
}
