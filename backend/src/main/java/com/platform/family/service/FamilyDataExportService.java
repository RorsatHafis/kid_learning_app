package com.platform.family.service;

import com.platform.audit.dto.RecordAuditEvent;
import com.platform.audit.entity.AuditActorType;
import com.platform.audit.service.AuditService;
import com.platform.child.entity.Child;
import com.platform.child.service.ChildService;
import com.platform.common.web.ResourceNotFoundException;
import com.platform.content.entity.Activity;
import com.platform.content.entity.ActivityVersion;
import com.platform.content.entity.QuestionOption;
import com.platform.content.entity.QuestionVersion;
import com.platform.content.repository.ActivityRepository;
import com.platform.content.repository.ActivityVersionRepository;
import com.platform.content.repository.QuestionOptionRepository;
import com.platform.content.repository.QuestionVersionRepository;
import com.platform.identity.entity.Account;
import com.platform.identity.entity.PlatformRole;
import com.platform.identity.repository.AccountRepository;
import com.platform.learning.entity.ActivityAttempt;
import com.platform.learning.entity.AnswerRecord;
import com.platform.learning.repository.ActivityAttemptRepository;
import com.platform.learning.repository.AnswerRecordRepository;
import com.platform.learning.service.LearningJourneyService;
import com.platform.learning.service.LearningJourneyService.CompletedActivity;
import com.platform.learning.service.LearningJourneyService.EnrollmentOverview;
import com.platform.learning.service.SkillProgressService;
import com.platform.learning.service.SkillProgressService.CurriculumProgress;
import com.platform.parent.service.ParentInsightService;
import com.platform.parent.web.ParentInsightResponse;
import com.platform.school.service.SchoolJoinService;
import com.platform.school.service.SchoolJoinService.SchoolAccessRecord;
import com.platform.subscription.service.SubscriptionService;
import com.platform.subscription.web.SubscriptionDtos.OwnFamilyPlanResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A copy of everything Bookeyond Kid holds about the caller and the children in their family,
 * for the family to keep or take elsewhere (CLAUDE.md §10: learning data belongs to the family).
 *
 * <p>It contains exactly what the family can already see in the app, gathered in one place:
 * the account, each child's profile, learning paths, skill standings with their evidence,
 * every completed activity, every recorded answer (as the child gave it, with the question
 * asked and whether it was right), the history of insights shown to the parent, every school the
 * family gave access to (and when it ended), and the family's plan. It never
 * contains another family's data, correct answers the child didn't give, passwords, or
 * session and link secrets. Every export is audited.
 */
@Service
public class FamilyDataExportService {

    public static final String FORMAT = "bookeyond-kid-family-export/1";
    private static final int ALL = Integer.MAX_VALUE;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final AccountRepository accounts;
    private final ChildService childService;
    private final FamilyAccessGuard familyAccessGuard;
    private final LearningJourneyService journeys;
    private final SkillProgressService skills;
    private final ParentInsightService insights;
    private final ActivityAttemptRepository attempts;
    private final AnswerRecordRepository answers;
    private final ActivityVersionRepository activityVersions;
    private final ActivityRepository activities;
    private final QuestionVersionRepository questionVersions;
    private final QuestionOptionRepository questionOptions;
    private final AuditService auditService;
    private final SchoolJoinService schoolAccess;
    private final SubscriptionService subscriptions;
    private final com.platform.child.service.ChildSettingsService childSettings;
    private final com.platform.child.service.DailyLearningTimeService dailyTime;
    private final com.platform.child.caregiver.CaregiverService caregivers;

    public FamilyDataExportService(AccountRepository accounts, ChildService childService, FamilyAccessGuard familyAccessGuard,
                                   LearningJourneyService journeys, SkillProgressService skills, ParentInsightService insights,
                                   ActivityAttemptRepository attempts, AnswerRecordRepository answers,
                                   ActivityVersionRepository activityVersions, ActivityRepository activities,
                                   QuestionVersionRepository questionVersions, QuestionOptionRepository questionOptions,
                                   AuditService auditService, SchoolJoinService schoolAccess,
                                   SubscriptionService subscriptions,
                                   com.platform.child.service.ChildSettingsService childSettings,
                                   com.platform.child.service.DailyLearningTimeService dailyTime,
                                   com.platform.child.caregiver.CaregiverService caregivers) {
        this.caregivers = caregivers;
        this.childSettings = childSettings;
        this.dailyTime = dailyTime;
        this.accounts = accounts;
        this.childService = childService;
        this.familyAccessGuard = familyAccessGuard;
        this.journeys = journeys;
        this.skills = skills;
        this.insights = insights;
        this.attempts = attempts;
        this.answers = answers;
        this.activityVersions = activityVersions;
        this.activities = activities;
        this.questionVersions = questionVersions;
        this.questionOptions = questionOptions;
        this.auditService = auditService;
        this.schoolAccess = schoolAccess;
        this.subscriptions = subscriptions;
    }

    /**
     * {@code plan}: the family's plan as the parent sees it (no administrator's note), when the account has a family.
     * {@code learningDayZone}: the time zone the family chose for its learning day (null: the platform's).
     */
    public record Export(String format, Instant exportedAt, AccountData account, OwnFamilyPlanResponse plan,
                         String learningDayZone, List<ChildData> children) {
    }

    public record AccountData(UUID id, String email, String role, Instant createdAt, Instant emailConfirmedAt,
                              String interfaceLanguage) {
    }

    public record ChildData(UUID id, String displayName, LocalDate dateOfBirth, Instant addedAt, String interfaceLanguage,
                            String learningBuddy, Integer breakReminderMinutes, Integer dailyLimitMinutes,
                            List<com.platform.child.service.DailyLearningTimeService.Extension> dailyTimeExtensions,
                            List<com.platform.child.caregiver.CaregiverService.Caregiver> caregivers,
                            List<EnrollmentOverview> learningPaths, List<CurriculumProgress> skills,
                            List<CompletedActivity> completedActivities, List<AnswerData> answers,
                            List<ParentInsightResponse> insightHistory, List<SchoolAccessRecord> schoolAccess) {
    }

    /** One answer as the child gave it. {@code answer} is readable text; {@code submitted} is the raw value stored. */
    public record AnswerData(Instant answeredAt, UUID attemptId, String activity, String question, String answer,
                             String submitted, boolean correct) {
    }

    @Transactional
    public Export export(UUID accountId) {
        Account account = accounts.findById(accountId).orElseThrow(() -> new ResourceNotFoundException("Account", accountId));
        List<ChildData> children = childService.listForAccount(accountId).stream()
                .sorted(Comparator.comparing(Child::getCreatedAt))
                .map(child -> childData(accountId, child))
                .toList();
        auditService.record(new RecordAuditEvent(null, accountId, AuditActorType.valueOf(account.getPlatformRole().name()),
                "ACCOUNT_DATA_EXPORTED", "ACCOUNT", accountId, Map.of("children", children.size())));
        // A parent no longer in any family (every membership ended) still gets their export, without a plan.
        OwnFamilyPlanResponse plan = account.getPlatformRole() == PlatformRole.PARENT
                ? subscriptions.familyPlanOf(accountId).map(OwnFamilyPlanResponse::of).orElse(null) : null;
        return new Export(FORMAT, Instant.now(),
                new AccountData(account.getId(), account.getEmail(), account.getPlatformRole().name(), account.getCreatedAt(),
                        account.getEmailVerifiedAt(), account.getPreferredLanguage()),
                plan, plan == null ? null : chosenZone(accountId), children);
    }

    private String chosenZone(UUID accountId) {
        com.platform.child.service.DailyLearningTimeService.FamilyDay day = dailyTime.familyDay(accountId);
        return day.familyChoice() ? day.zone() : null;
    }

    private ChildData childData(UUID accountId, Child child) {
        // listForAccount already scopes to the caller's families; checked again per child on purpose.
        familyAccessGuard.requireChildRecord(accountId, child.getId());
        return new ChildData(child.getId(), child.getDisplayName(), child.getDateOfBirth(), child.getCreatedAt(),
                child.getPreferredLanguage(), child.getLearningBuddy().name(),
                childSettings.breakReminderMinutes(child.getId()), dailyTime.limitMinutes(child.getId()),
                dailyTime.extensionsOf(child.getId()), caregivers.historyOf(child.getId()), journeys.enrollmentsFor(child.getId()),
                skills.progressFor(child.getId()),
                journeys.completedActivities(child.getId(), ALL), answersOf(child.getId()),
                insights.listRecent(child.getId()).stream().map(ParentInsightResponse::from).toList(),
                schoolAccess.accessHistoryOf(accountId, child.getId()));
    }

    private List<AnswerData> answersOf(UUID childId) {
        Map<UUID, String> activityTitles = new HashMap<>();
        Map<UUID, QuestionVersion> questions = new HashMap<>();
        Map<UUID, List<QuestionOption>> options = new HashMap<>();
        List<ActivityAttempt> childAttempts = attempts.findByChildIdOrderByStartedAtDesc(childId);
        // All answers of all attempts in one query, not one per attempt.
        Map<UUID, List<AnswerRecord>> byAttempt = childAttempts.isEmpty() ? Map.of()
                : answers.findByActivityAttemptIdIn(childAttempts.stream().map(ActivityAttempt::getId).toList()).stream()
                        .collect(Collectors.groupingBy(AnswerRecord::getActivityAttemptId));
        // The activities' titles, once for all attempts.
        List<UUID> versionIds = childAttempts.stream().map(ActivityAttempt::getActivityVersionId).distinct().toList();
        if (!versionIds.isEmpty()) {
            Map<UUID, UUID> activityOfVersion = new HashMap<>();
            activityVersions.findAllById(versionIds).forEach(v -> activityOfVersion.put(v.getId(), v.getActivityId()));
            Map<UUID, String> titles = new HashMap<>();
            activities.findAllById(activityOfVersion.values()).forEach(a -> titles.put(a.getId(), a.getTitle()));
            versionIds.forEach(v -> activityTitles.put(v, titles.get(activityOfVersion.get(v))));
        }
        // Likewise the questions and their choices, once for all answers.
        List<UUID> questionIds = byAttempt.values().stream().flatMap(List::stream).map(AnswerRecord::getQuestionVersionId)
                .distinct().toList();
        if (!questionIds.isEmpty()) {
            questionVersions.findAllById(questionIds).forEach(q -> questions.put(q.getId(), q));
            questionOptions.findByQuestionVersionIdInOrderByDisplayOrderAsc(questionIds)
                    .forEach(o -> options.computeIfAbsent(o.getQuestionVersionId(), key -> new java.util.ArrayList<>()).add(o));
            questionIds.forEach(id -> options.putIfAbsent(id, List.of()));
        }
        return childAttempts.stream()
                .sorted(Comparator.comparing(ActivityAttempt::getStartedAt))
                .flatMap(attempt -> {
                    String title = activityTitles.computeIfAbsent(attempt.getActivityVersionId(), this::activityTitle);
                    return byAttempt.getOrDefault(attempt.getId(), List.of()).stream()
                            .sorted(Comparator.comparing(AnswerRecord::getOccurredAt))
                            .map(record -> {
                                Optional<QuestionVersion> question = Optional.ofNullable(questions.computeIfAbsent(
                                        record.getQuestionVersionId(), id -> questionVersions.findById(id).orElse(null)));
                                List<QuestionOption> choices = options.computeIfAbsent(record.getQuestionVersionId(),
                                        questionOptions::findByQuestionVersionIdOrderByDisplayOrderAsc);
                                return new AnswerData(record.getOccurredAt(), attempt.getId(), title,
                                        question.map(QuestionVersion::getPrompt).orElse(null),
                                        readable(record.getSubmittedAnswer(), choices), record.getSubmittedAnswer(), record.isCorrect());
                            });
                })
                .toList();
    }

    private String activityTitle(UUID activityVersionId) {
        return activityVersions.findById(activityVersionId).map(ActivityVersion::getActivityId)
                .flatMap(activities::findById).map(Activity::getTitle).orElse(null);
    }

    /** Choice ids become the words the child saw; a matching answer becomes "left → right" pairs. */
    static String readable(String submitted, List<QuestionOption> choices) {
        if (submitted == null || choices.isEmpty()) {
            return submitted;
        }
        Map<String, String> labels = choices.stream()
                .collect(Collectors.toMap(o -> o.getId().toString(), QuestionOption::getLabel, (a, b) -> a));
        if (submitted.strip().startsWith("{")) {
            try {
                Map<String, String> pairs = JSON.readValue(submitted, new TypeReference<Map<String, String>>() { });
                return pairs.entrySet().stream()
                        .map(e -> labels.getOrDefault(e.getKey(), e.getKey()) + " → " + labels.getOrDefault(e.getValue(), e.getValue()))
                        .collect(Collectors.joining(", "));
            } catch (RuntimeException e) {
                return submitted;
            }
        }
        return labels.getOrDefault(submitted.strip(), submitted);
    }

}
