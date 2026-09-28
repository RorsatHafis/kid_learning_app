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
 * asked and whether it was right) and the history of insights shown to the parent. It never
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

    public FamilyDataExportService(AccountRepository accounts, ChildService childService, FamilyAccessGuard familyAccessGuard,
                                   LearningJourneyService journeys, SkillProgressService skills, ParentInsightService insights,
                                   ActivityAttemptRepository attempts, AnswerRecordRepository answers,
                                   ActivityVersionRepository activityVersions, ActivityRepository activities,
                                   QuestionVersionRepository questionVersions, QuestionOptionRepository questionOptions,
                                   AuditService auditService) {
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
    }

    public record Export(String format, Instant exportedAt, AccountData account, List<ChildData> children) {
    }

    public record AccountData(UUID id, String email, String role, Instant createdAt, Instant emailConfirmedAt) {
    }

    public record ChildData(UUID id, String displayName, LocalDate dateOfBirth, Instant addedAt,
                            List<EnrollmentOverview> learningPaths, List<CurriculumProgress> skills,
                            List<CompletedActivity> completedActivities, List<AnswerData> answers,
                            List<ParentInsightResponse> insightHistory) {
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
        return new Export(FORMAT, Instant.now(),
                new AccountData(account.getId(), account.getEmail(), account.getPlatformRole().name(), account.getCreatedAt(),
                        account.getEmailVerifiedAt()),
                children);
    }

    private ChildData childData(UUID accountId, Child child) {
        // listForAccount already scopes to the caller's families; checked again per child on purpose.
        familyAccessGuard.requireChildAccess(accountId, child.getId());
        return new ChildData(child.getId(), child.getDisplayName(), child.getDateOfBirth(), child.getCreatedAt(),
                journeys.enrollmentsFor(child.getId()), skills.progressFor(child.getId()),
                journeys.completedActivities(child.getId(), ALL), answersOf(child.getId()),
                insights.listRecent(child.getId()).stream().map(ParentInsightResponse::from).toList());
    }

    private List<AnswerData> answersOf(UUID childId) {
        Map<UUID, String> activityTitles = new HashMap<>();
        Map<UUID, QuestionVersion> questions = new HashMap<>();
        Map<UUID, List<QuestionOption>> options = new HashMap<>();
        return attempts.findByChildIdOrderByStartedAtDesc(childId).stream()
                .sorted(Comparator.comparing(ActivityAttempt::getStartedAt))
                .flatMap(attempt -> {
                    String title = activityTitles.computeIfAbsent(attempt.getActivityVersionId(), this::activityTitle);
                    return answers.findByActivityAttemptId(attempt.getId()).stream()
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
