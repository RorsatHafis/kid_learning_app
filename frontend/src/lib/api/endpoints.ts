import { apiClient } from "./client";
import type {
  AccountProfile,
  ActivityAttempt,
  ActivityVersionDetail,
  AnswerRecord,
  AuthResponse,
  Child,
  ClassRosterChild,
  CompletedActivity,
  CurriculumProgress,
  CurriculumSummary,
  Enrollment,
  EnrollmentOverview,
  Journey,
  LearningPathItem,
  ParentInsight,
  ProgressSummary,
  SchoolClassSummary,
  StreakSummary,
} from "./types";

// One function per backend endpoint the frontend calls, each citing the
// controller it was verified against. Extend this file rather than
// calling apiClient directly from components.

export const authApi = {
  register: (input: { email: string; password: string; familyName?: string; role?: "PARENT" | "PRINCIPAL" }) =>
    apiClient.post<AuthResponse>(
      "/auth/register",
      { ...input, idempotencyKey: crypto.randomUUID() },
      { skipAuth: true },
    ),
  login: (input: { email: string; password: string }) =>
    apiClient.post<AuthResponse>("/auth/login", input, { skipAuth: true }),
  logout: (refreshToken: string) => apiClient.post<void>("/auth/logout", { refreshToken }, { skipAuth: true }),
};

// identity/web/AccountController.java — always the signed-in account; no id in any path.
export const accountApi = {
  me: () => apiClient.get<AccountProfile>("/account"),
  changePassword: (input: { currentPassword: string; newPassword: string }) =>
    apiClient.post<AuthResponse>("/account/password", input),
  signOutEverywhere: () => apiClient.post<{ sessionsEnded: number }>("/account/sign-out-everywhere"),
};

// child/web/ChildController.java
export const childrenApi = {
  list: () => apiClient.get<Child[]>("/children"),
  get: (childId: string) => apiClient.get<Child>(`/children/${childId}`),
  create: (input: { displayName: string; dateOfBirth: string }) => apiClient.post<Child>("/children", input),
  rename: (childId: string, displayName: string) => apiClient.patch<Child>(`/children/${childId}`, { displayName }),
};

// learning/web/LearningJourneyController.java — server-side views of a child's
// learning. These replace the old client-side enrollment pointer: the server is
// the only source of which enrollments a child has.
export const learningApi = {
  enrollments: (childId: string) => apiClient.get<EnrollmentOverview[]>(`/children/${childId}/enrollments`),
  journey: (enrollmentId: string) => apiClient.get<Journey>(`/enrollments/${enrollmentId}/journey`),
  skills: (childId: string) => apiClient.get<CurriculumProgress[]>(`/children/${childId}/skills`),
  history: (childId: string, limit = 20) =>
    apiClient.get<CompletedActivity[]>(`/children/${childId}/activity-history?limit=${limit}`),
};

// curriculum/web/CurriculumController.java
export const curriculaApi = {
  list: () => apiClient.get<CurriculumSummary[]>("/curricula"),
};

// learning/web/EnrollmentController.java
export const enrollmentApi = {
  enroll: (childId: string, curriculumId: string) =>
    apiClient.post<Enrollment>(`/children/${childId}/enrollments`, { curriculumId }),
  get: (enrollmentId: string) => apiClient.get<Enrollment>(`/enrollments/${enrollmentId}`),
  /** 200 with the next pending item, or whatever the backend currently
   * has queued — see LearningPathItemResponse. */
  nextActivity: (enrollmentId: string) =>
    apiClient.get<LearningPathItem>(`/enrollments/${enrollmentId}/next-activity`),
};

// content/web/ActivityContentController.java — published, un-guarded catalog content
export const activityContentApi = {
  getVersion: (activityVersionId: string) =>
    apiClient.get<ActivityVersionDetail>(`/activity-versions/${activityVersionId}`),
};

// learning/web/ActivityAttemptController.java
export const activityAttemptApi = {
  start: (childId: string, input: { activityVersionId: string; learningPathItemId?: string }) =>
    apiClient.post<ActivityAttempt>(`/children/${childId}/activity-attempts`, input),
  get: (attemptId: string) => apiClient.get<ActivityAttempt>(`/activity-attempts/${attemptId}`),
  listAnswers: (attemptId: string) => apiClient.get<AnswerRecord[]>(`/activity-attempts/${attemptId}/answers`),
  /** submittedAnswer per question type (backend AnswerScoringService):
   * MULTIPLE_CHOICE → the chosen option's id; TRUE_FALSE → "true" | "false";
   * SHORT_ANSWER → text; NUMERIC → a number. An invalid value is rejected with
   * 422 and never scored. The caller owns idempotencyKey so a retry of the SAME
   * answer can reuse it and get the original result back instead of a conflict. */
  submitAnswer: (
    attemptId: string,
    input: { questionVersionId: string; submittedAnswer: string; timeSpentSeconds?: number; idempotencyKey: string },
  ) => apiClient.post<AnswerRecord>(`/activity-attempts/${attemptId}/answers`, input),
  complete: (attemptId: string) => apiClient.post<ActivityAttempt>(`/activity-attempts/${attemptId}/complete`),
  abandon: (attemptId: string) => apiClient.post<ActivityAttempt>(`/activity-attempts/${attemptId}/abandon`),
};

// learning/web/LearnerProgressController.java, streak/web/StreakController.java
export const progressApi = {
  get: (childId: string) => apiClient.get<ProgressSummary>(`/children/${childId}/progress`),
  streak: (childId: string) => apiClient.get<StreakSummary>(`/children/${childId}/streak`),
};

// parent/web/ParentInsightController.java — `list` is the child's CURRENT
// interpretation (derived live); `generate` also records it as a history snapshot.
export const parentInsightApi = {
  list: (childId: string) => apiClient.get<ParentInsight[]>(`/children/${childId}/parent-insights`),
  generate: (childId: string) => apiClient.post<ParentInsight[]>(`/children/${childId}/parent-insights/generate`),
};

// school/web/SchoolController.java — teacher-usable subset only.
// requireClassAccess (verified in SchoolAccessGuard.java) grants TEACHER
// access only when they hold an ACTIVE TeacherAssignment for that exact
// class — real, per-class server-side scoping, not just a role check.
export const schoolApi = {
  myClasses: () => apiClient.get<SchoolClassSummary[]>("/schools/classes/mine"),
  classRoster: (classId: string) => apiClient.get<ClassRosterChild[]>(`/schools/classes/${classId}/children`),
};
