import { apiClient } from "./client";
import type {
  ActivityAttempt,
  ActivityVersionDetail,
  AdaptiveRecommendation,
  AnswerRecord,
  AuthResponse,
  Child,
  ClassRosterChild,
  CurriculumSummary,
  Enrollment,
  LearningPathItem,
  ParentInsight,
  ProgressSummary,
  SchoolClassSummary,
  SmartReviewCheck,
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

// child/web/ChildController.java
export const childrenApi = {
  list: () => apiClient.get<Child[]>("/children"),
  create: (input: { displayName: string; dateOfBirth: string }) => apiClient.post<Child>("/children", input),
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
  /** Returns null on a 204 ("nothing to recommend yet" — a legitimate
   * outcome per the controller's javadoc, not an error). */
  adaptiveNext: async (attemptId: string): Promise<AdaptiveRecommendation | null> => {
    const res = await apiClient.post<AdaptiveRecommendation | null>(`/activity-attempts/${attemptId}/adaptive-next`);
    return res ?? null;
  },
  smartReviewCheck: async (attemptId: string): Promise<SmartReviewCheck | null> => {
    const res = await apiClient.post<SmartReviewCheck | null>(`/activity-attempts/${attemptId}/smart-review-check`);
    return res ?? null;
  },
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
