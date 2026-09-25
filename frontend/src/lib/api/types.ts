// Mirrors of backend/src/main/java/com/platform/**/entity + **/web DTOs.
// Every shape here has been read directly from the Java source (not
// guessed) — see the file path noted above each block. Re-verify against
// the backend before trusting a shape that isn't cited this way.

// ---- identity/entity/PlatformRole.java, identity/web/AuthResponse.java ----

/** No CHILD value — children are accessed through a parent's family
 * membership, never their own account. See PlatformRole's javadoc. */
export type PlatformRole = "PARENT" | "TEACHER" | "PRINCIPAL" | "ADMIN";

export interface AuthResponse {
  accountId: string;
  email: string;
  platformRole: PlatformRole;
  /** null on the PRINCIPAL "pending approval" registration branch */
  accessToken: string | null;
  expiresAt: string | null;
  refreshToken: string | null;
  refreshTokenExpiresAt: string | null;
}

// ---- child/entity/ChildStatus.java, child/web/ChildDtos.java ----

export type ChildStatus = "ACTIVE" | "ARCHIVED";

export interface Child {
  id: string;
  displayName: string;
  dateOfBirth: string; // ISO date (LocalDate)
  status: ChildStatus;
}

// ---- curriculum/web/CurriculumDtos.java ----

export interface CurriculumSummary {
  id: string;
  name: string;
  subjectCode: string;
  subjectName: string;
  ageHubCode: string;
  ageHubName: string;
}

// ---- learning/entity/EnrollmentStatus.java, learning/web/EnrollmentDtos.java ----

export type EnrollmentStatus = "ACTIVE" | "COMPLETED" | "PAUSED" | "WITHDRAWN";
export type LearningPathItemStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "SKIPPED";
export type LearningPathItemSource = "CURRICULUM" | "ADAPTIVE" | "REVIEW" | "TEACHER_ASSIGNED";

export interface Enrollment {
  id: string;
  childId: string;
  curriculumVersionId: string;
  status: EnrollmentStatus;
  enrolledAt: string;
}

export interface LearningPathItem {
  id: string;
  activityVersionId: string;
  sequenceOrder: number;
  status: LearningPathItemStatus;
  source: LearningPathItemSource;
}

// ---- content/entity/QuestionType.java, content/web/ActivityContentDtos.java ----

export type QuestionType = "MULTIPLE_CHOICE" | "TRUE_FALSE" | "SHORT_ANSWER" | "NUMERIC" | "MATCHING";

export interface QuestionOption {
  id: string;
  label: string;
  displayOrder: number;
}

export interface ActivityItem {
  activityItemId: string;
  sequenceOrder: number;
  questionVersionId: string;
  questionType: QuestionType;
  prompt: string;
  /** The choices for MULTIPLE_CHOICE (answered by option id). Not used for
   * TRUE_FALSE ("true"/"false") or free-response types. Never carries which
   * option is correct — that stays server-side. */
  options: QuestionOption[];
}

export interface ActivityVersionDetail {
  activityVersionId: string;
  activityId: string;
  title: string;
  instructions: string | null;
  difficultyLevel: number | null;
  estimatedDurationSeconds: number | null;
  items: ActivityItem[];
}

// ---- learning/entity/ActivityAttemptStatus.java, learning/web/ActivityAttemptDtos.java ----

export type ActivityAttemptStatus = "IN_PROGRESS" | "COMPLETED" | "ABANDONED";

export interface ActivityAttempt {
  id: string;
  childId: string;
  activityVersionId: string;
  learningPathItemId: string | null;
  status: ActivityAttemptStatus;
  startedAt: string;
  completedAt: string | null;
  score: number | null;
}

export interface AnswerRecord {
  id: string;
  questionVersionId: string;
  correct: boolean;
  timeSpentSeconds: number | null;
  occurredAt: string;
}

// ---- learning/web/LearnerProgressController.java (inline Response record) ----

export interface ProgressSummary {
  childId: string;
  completedActivities: number;
  learningMinutes: number;
  currentStreak: number;
  longestStreak: number;
  completedPathItems: number;
  totalPathItems: number;
}

// ---- streak/web/StreakController.java (inline Response record) ----

export interface StreakSummary {
  childId: string;
  currentStreak: number;
  longestStreak: number;
  lastActivityDate: string | null;
}

// ---- parent/entity/ParentInsightType.java, parent/web/ParentInsightResponse.java ----

export type ParentInsightType = "PROGRESS" | "WEAKNESS" | "STRENGTH" | "RECOMMENDATION";

/** GET /parent-insights is the CURRENT interpretation, derived live and not
 * persisted — so `id` and `occurredAt` are null there. Recorded snapshots
 * (POST /generate, GET /parent-insights/history) carry both. */
export interface ParentInsight {
  id: string | null;
  insightType: ParentInsightType;
  headline: string;
  detail: string | null;
  supportingData: Record<string, unknown>;
  occurredAt: string | null;
}

// ---- common/web/GlobalExceptionHandler.java ----

/** RFC-7807 ProblemDetail — the shape of every error response. `errors` is
 * only present on 400 validation failures; `lockedUntil` only on 423. */
export interface ApiProblem {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  errors?: Record<string, string>;
  lockedUntil?: string;
  correlationId?: string;
  timestamp?: string;
}

// ---- school/web/SchoolController.java ----
// TEACHER-scoped views only carry what that controller actually returns —
// notably ClassRosterChild has no dateOfBirth/status, unlike the parent-
// facing Child type, because ChildResponse.of() in SchoolController only
// serializes id + displayName.

export interface SchoolClassSummary {
  id: string;
  schoolId: string;
  name: string;
}

export interface ClassRosterChild {
  id: string;
  displayName: string;
}
