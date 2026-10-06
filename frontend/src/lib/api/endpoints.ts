import { apiClient } from "./client";
import type {
  AccountProfile,
  ActivityAttempt,
  ActivityVersionDetail,
  AnswerRecord,
  AuthResponse,
  Child,
  CompletedActivity,
  CurriculumChoice,
  CurriculumProgress,
  CurriculumSummary,
  Enrollment,
  EnrollmentOverview,
  Journey,
  LearningPathItem,
  ParentInsight,
  ProgressSince,
  ProgressSummary,
  SchoolClassSummary,
  SchoolClassWithTeachers,
  SchoolInvitation,
  SchoolStaffMember,
  SchoolSummary,
  SkillStory,
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
  // identity/web/AccountRecoveryController.java — always 202, whether or not the address has an account.
  requestPasswordReset: (email: string) => apiClient.post<void>("/auth/password-reset", { email }, { skipAuth: true }),
  resetPassword: (input: { token: string; newPassword: string }) =>
    apiClient.post<void>("/auth/password-reset/confirm", input, { skipAuth: true }),
  confirmEmail: (token: string) => apiClient.post<void>("/auth/email-verification/confirm", { token }, { skipAuth: true }),
  completeAccountSetup: (input: { token: string; newPassword: string }) =>
    apiClient.post<void>("/auth/account-setup/confirm", input, { skipAuth: true }),
  // 400 for an unusable link, 409 when the new address now belongs to another account.
  confirmEmailChange: (token: string) => apiClient.post<void>("/auth/email-change/confirm", { token }, { skipAuth: true }),
};

// identity/web/AccountController.java — always the signed-in account; no id in any path.
export const accountApi = {
  me: () => apiClient.get<AccountProfile>("/account"),
  changePassword: (input: { currentPassword: string; newPassword: string }) =>
    apiClient.post<AuthResponse>("/account/password", input),
  signOutEverywhere: () => apiClient.post<{ sessionsEnded: number }>("/account/sign-out-everywhere"),
  // 202 whether or not the new address has an account; 422 wrong current password, 409 already this address, 429 too soon.
  requestEmailChange: (input: { currentPassword: string; newEmail: string }) => apiClient.post<void>("/account/email", input),
  // identity/web/AccountRecoveryController.java — 409 when already confirmed, 429 when sent too recently.
  resendVerification: () => apiClient.post<void>("/account/email-verification"),
  // family/web/FamilyDataExportController.java — everything held about this account and its family's children.
  dataExport: () => apiClient.get<unknown>("/account/data-export"),
};

// child/web/ChildController.java
export const childrenApi = {
  list: () => apiClient.get<Child[]>("/children"),
  get: (childId: string) => apiClient.get<Child>(`/children/${childId}`),
  create: (input: { displayName: string; dateOfBirth: string }) => apiClient.post<Child>("/children", input),
  rename: (childId: string, displayName: string) => apiClient.patch<Child>(`/children/${childId}`, { displayName }),
  /** The family catalog for this child: the child's age group first (curriculum/web/CurriculumController.java). */
  curricula: (childId: string) => apiClient.get<CurriculumChoice[]>(`/children/${childId}/curricula`),
};

// learning/web/LearningJourneyController.java — server-side views of a child's
// learning. These replace the old client-side enrollment pointer: the server is
// the only source of which enrollments a child has.
export const learningApi = {
  enrollments: (childId: string) => apiClient.get<EnrollmentOverview[]>(`/children/${childId}/enrollments`),
  journey: (enrollmentId: string) => apiClient.get<Journey>(`/enrollments/${enrollmentId}/journey`),
  skills: (childId: string) => apiClient.get<CurriculumProgress[]>(`/children/${childId}/skills`),
  /** X25: places due reviews on paths with nothing waiting (Smart Review otherwise places them after a completed step). */
  placeDueReviews: (childId: string) => apiClient.post<{ placed: number }>(`/children/${childId}/reviews/place-due`),
  /** One skill, explained (X19): where it stands, the activities behind it, what is waiting for it. */
  skill: (childId: string, skillId: string) => apiClient.get<SkillStory>(`/children/${childId}/skills/${skillId}`),
  /** What changed in the last `days` (7–90): each skill answered on, where it stood then and stands now (X12). */
  changes: (childId: string, days = 28) => apiClient.get<ProgressSince>(`/children/${childId}/changes?days=${days}`),
  /** Newest first; with `after` (the last one shown), the ones finished before it. */
  history: (childId: string, limit = 20, after?: CompletedActivity) => {
    const params = new URLSearchParams({ limit: String(limit) });
    if (after) {
      params.set("before", after.completedAt);
      params.set("beforeId", after.attemptId);
    }
    return apiClient.get<CompletedActivity[]>(`/children/${childId}/activity-history?${params}`);
  },
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
  // school/web/SchoolController.java
  mySchools: () => apiClient.get<SchoolSummary[]>("/schools/mine"),
  createSchool: (name: string) => apiClient.post<SchoolSummary>("/schools", { name }),
  createClass: (schoolId: string, name: string) => apiClient.post<SchoolClassSummary>(`/schools/${schoolId}/classes`, { name }),
  // school/web/SchoolLifecycleController.java (X17): the principal's
  renameSchool: (schoolId: string, name: string) => apiClient.put<SchoolSummary>(`/schools/${schoolId}`, { name }),
  renameClass: (classId: string, name: string) => apiClient.put<SchoolClassSummary>(`/schools/classes/${classId}`, { name }),
  archiveClass: (classId: string) => apiClient.post<SchoolClassSummary>(`/schools/classes/${classId}/archive`),
  restoreClass: (classId: string) => apiClient.post<SchoolClassSummary>(`/schools/classes/${classId}/restore`),
  assignTeacher: (classId: string, teacherAccountId: string) =>
    apiClient.post<unknown>(`/schools/classes/${classId}/teachers`, { teacherAccountId }),
  // school/web/SchoolStaffController.java
  schoolClasses: (schoolId: string) => apiClient.get<SchoolClassWithTeachers[]>(`/schools/${schoolId}/classes`),
  schoolClass: (classId: string) => apiClient.get<SchoolClassWithTeachers>(`/schools/classes/${classId}`),
  unassignTeacher: (classId: string, teacherAccountId: string) =>
    apiClient.delete<void>(`/schools/classes/${classId}/teachers/${teacherAccountId}`),
  staff: (schoolId: string) => apiClient.get<SchoolStaffMember[]>(`/schools/${schoolId}/staff`),
  inviteTeacher: (schoolId: string, email: string) => apiClient.post<SchoolStaffMember>(`/schools/${schoolId}/staff`, { email }),
  removeStaff: (schoolId: string, membershipId: string) => apiClient.delete<void>(`/schools/${schoolId}/staff/${membershipId}`),
  myInvitations: () => apiClient.get<SchoolInvitation[]>("/schools/invitations/mine"),
  acceptInvitation: (id: string) => apiClient.post<void>(`/schools/invitations/${id}/accept`),
  declineInvitation: (id: string) => apiClient.post<void>(`/schools/invitations/${id}/decline`),
};
