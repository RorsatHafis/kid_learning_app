package com.platform.content.service;

import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.Lesson;
import com.platform.content.entity.Question;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.LessonRepository;
import com.platform.content.repository.QuestionRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Central server-side authorization for teacher-managed content.
 *
 * Null owner means system/YulMe-owned content and is intentionally not writable
 * by ordinary teachers. ADMIN and PRINCIPAL may manage another teacher's content.
 */
@Service
public class ContentAccessGuard {
    private final AccountRepository accounts;
    private final LessonRepository lessons;
    private final QuestionRepository questions;
    private final ActivityRepository activities;

    public ContentAccessGuard(AccountRepository accounts, LessonRepository lessons,
                              QuestionRepository questions, ActivityRepository activities) {
        this.accounts = accounts;
        this.lessons = lessons;
        this.questions = questions;
        this.activities = activities;
    }

    public Account requireAuthor(UUID accountId) {
        Account account = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        if (account.getPlatformRole() == PlatformRole.PARENT) {
            throw new AccessDeniedException("Only staff may author content");
        }
        return account;
    }

    public Lesson requireLessonWrite(UUID accountId, UUID lessonId) {
        requireAuthor(accountId);
        Lesson lesson = lessons.findById(lessonId)
                .orElseThrow(() -> new ResourceNotFoundException("Lesson", lessonId));
        if (lesson.getOwnerAccountId() != null
                && !lesson.getOwnerAccountId().equals(accountId)
                && !isPrivileged(accountId)) {
            throw new AccessDeniedException("Content belongs to another teacher");
        }
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
        requireAuthor(accountId);
        Question question = questions.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("Question", questionId));
        if (question.getOwnerAccountId() != null
                && !question.getOwnerAccountId().equals(accountId)
                && !isPrivileged(accountId)) {
            throw new AccessDeniedException("Content belongs to another teacher");
        }
        return question;
    }

    private boolean isPrivileged(UUID accountId) {
        PlatformRole role = accounts.findById(accountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account", accountId))
                .getPlatformRole();
        return role == PlatformRole.ADMIN || role == PlatformRole.PRINCIPAL;
    }
}
