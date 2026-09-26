import { apiClient } from "./client";
import type { ActivityItem, PlatformRole, QuestionType } from "./types";

// ---- content/studio/StudioDtos.java ----
// Author-facing contract: carries answer keys, and every read is limited server-side
// to content the signed-in author may edit (ADMIN: platform content).

export type PublicationStatus = "DRAFT" | "PUBLISHED" | "RETIRED";

export interface VersionSummary {
  id: string;
  versionNumber: number;
  status: PublicationStatus;
  publishedAt: string | null;
}

export interface Subject {
  id: string;
  code: string;
  name: string;
  description: string | null;
}

export interface AgeHub {
  id: string;
  code: string;
  name: string;
  minAge: number;
  maxAge: number | null;
}

export interface CatalogSkill {
  id: string;
  code: string;
  name: string;
  description: string | null;
  status: "ACTIVE" | "ARCHIVED";
  prerequisiteSkillIds: string[];
}

export interface CatalogObjective {
  id: string;
  code: string;
  title: string;
  description: string | null;
  skillIds: string[];
}

export interface Catalog {
  subject: Subject;
  skills: CatalogSkill[];
  objectives: CatalogObjective[];
}

export interface CurriculumSummary {
  id: string;
  name: string;
  subjectId: string;
  ageHubId: string;
  versions: VersionSummary[];
}

export interface CurriculumVersionDetail extends VersionSummary {
  objectiveIds: string[];
}

export interface CurriculumDetail {
  id: string;
  name: string;
  subjectId: string;
  ageHubId: string;
  versions: CurriculumVersionDetail[];
}

export interface ActivitySummary {
  id: string;
  title: string;
  activityType: string;
  skillIds: string[];
  versions: VersionSummary[];
}

export interface LessonSummary {
  id: string;
  title: string;
  subjectId: string;
  activities: ActivitySummary[];
}

export interface ItemDetail {
  questionVersionId: string;
  questionId: string;
  questionType: QuestionType;
  prompt: string;
  questionVersionNumber: number;
  questionVersionStatus: PublicationStatus;
  points: number;
  questionSkillIds: string[];
}

export interface ActivityVersionDetail extends VersionSummary {
  instructions: string | null;
  difficultyLevel: number | null;
  estimatedDurationSeconds: number | null;
  items: ItemDetail[];
}

export interface ActivityDetail {
  id: string;
  title: string;
  activityType: string;
  lessonId: string;
  subjectId: string;
  skillIds: string[];
  versions: ActivityVersionDetail[];
}

export interface OptionDetail {
  id: string;
  label: string;
  correct: boolean;
}

export interface QuestionVersionDetail extends VersionSummary {
  prompt: string;
  correctAnswer: string | null;
  difficultyLevel: number | null;
  options: OptionDetail[];
}

export interface QuestionSummary {
  id: string;
  subjectId: string;
  questionType: QuestionType;
  skillIds: string[];
  latestPrompt: string | null;
  publishedVersionId: string | null;
  versions: VersionSummary[];
}

export interface QuestionDetail {
  id: string;
  subjectId: string;
  questionType: QuestionType;
  skillIds: string[];
  versions: QuestionVersionDetail[];
}

// ---- content/validation/ValidationReport.java ----
export interface ValidationIssue {
  code: string;
  message: string;
}

export interface ValidationReport {
  errors: ValidationIssue[];
  warnings: ValidationIssue[];
}

/** content/web/ActivityContentDtos.ActivityVersionDetailResponse — what a child is served. */
export interface LearnerActivityPreview {
  activityVersionId: string;
  activityId: string;
  title: string;
  instructions: string | null;
  difficultyLevel: number | null;
  estimatedDurationSeconds: number | null;
  items: ActivityItem[];
}

export interface OptionInput {
  label: string;
  correct: boolean;
}

export interface QuestionDraftInput {
  prompt: string;
  correctAnswer: string | null;
  difficultyLevel: number | null;
  options?: OptionInput[];
}

const S = "/studio";

export const studioApi = {
  subjects: () => apiClient.get<Subject[]>(`${S}/subjects`),
  createSubject: (body: { code: string; name: string; description?: string }) =>
    apiClient.post<Subject>(`${S}/subjects`, body),
  ageHubs: () => apiClient.get<AgeHub[]>(`${S}/age-hubs`),
  catalog: (subjectId: string) => apiClient.get<Catalog>(`${S}/subjects/${subjectId}/catalog`),
  createSkill: (subjectId: string, body: { code: string; name: string; description?: string }) =>
    apiClient.post<CatalogSkill>(`${S}/subjects/${subjectId}/skills`, body),
  createObjective: (subjectId: string, body: { code: string; title: string; description?: string }) =>
    apiClient.post<CatalogObjective>(`${S}/subjects/${subjectId}/objectives`, body),
  linkObjectiveSkill: (objectiveId: string, skillId: string) =>
    apiClient.post<CatalogObjective>(`${S}/objectives/${objectiveId}/skills/${skillId}`),
  addPrerequisite: (skillId: string, prerequisiteSkillId: string) =>
    apiClient.post<CatalogSkill>(`${S}/skills/${skillId}/prerequisites/${prerequisiteSkillId}`),

  curricula: () => apiClient.get<CurriculumSummary[]>(`${S}/curricula`),
  curriculum: (id: string) => apiClient.get<CurriculumDetail>(`${S}/curricula/${id}`),
  createCurriculum: (body: { subjectId: string; ageHubId: string; name: string }) =>
    apiClient.post<CurriculumDetail>(`${S}/curricula`, body),
  openCurriculumDraft: (id: string) => apiClient.post<CurriculumDetail>(`${S}/curricula/${id}/draft`),
  replaceCurriculumObjectives: (versionId: string, objectiveIds: string[]) =>
    apiClient.put<CurriculumDetail>(`${S}/curriculum-versions/${versionId}/objectives`, { objectiveIds }),
  validateCurriculumVersion: (versionId: string) =>
    apiClient.get<ValidationReport>(`${S}/curriculum-versions/${versionId}/validation`),
  publishCurriculumVersion: (versionId: string) =>
    apiClient.post<CurriculumDetail>(`${S}/curriculum-versions/${versionId}/publish`),

  lessons: () => apiClient.get<LessonSummary[]>(`${S}/lessons`),
  createLesson: (body: { subjectId: string; title: string }) => apiClient.post<LessonSummary>(`${S}/lessons`, body),
  createActivity: (lessonId: string, body: { title: string; skillIds: string[] }) =>
    apiClient.post<ActivityDetail>(`${S}/lessons/${lessonId}/activities`, body),
  activity: (id: string) => apiClient.get<ActivityDetail>(`${S}/activities/${id}`),
  openActivityDraft: (id: string) => apiClient.post<ActivityDetail>(`${S}/activities/${id}/draft`),
  tagActivitySkill: (id: string, skillId: string) => apiClient.post<ActivityDetail>(`${S}/activities/${id}/skills/${skillId}`),
  untagActivitySkill: (id: string, skillId: string) =>
    apiClient.delete<ActivityDetail>(`${S}/activities/${id}/skills/${skillId}`),
  reviseActivityDraft: (
    versionId: string,
    body: { instructions: string | null; difficultyLevel: number | null; estimatedDurationSeconds: number | null },
  ) => apiClient.put<ActivityDetail>(`${S}/activity-versions/${versionId}`, body),
  replaceActivityItems: (versionId: string, items: { questionVersionId: string }[]) =>
    apiClient.put<ActivityDetail>(`${S}/activity-versions/${versionId}/items`, { items }),
  validateActivityVersion: (versionId: string) =>
    apiClient.get<ValidationReport>(`${S}/activity-versions/${versionId}/validation`),
  previewActivityVersion: (versionId: string) =>
    apiClient.get<LearnerActivityPreview>(`${S}/activity-versions/${versionId}/preview`),
  publishActivityVersion: (versionId: string) =>
    apiClient.post<ActivityDetail>(`${S}/activity-versions/${versionId}/publish`),

  questions: (subjectId: string) =>
    apiClient.get<QuestionSummary[]>(`${S}/questions?subjectId=${encodeURIComponent(subjectId)}`),
  question: (id: string) => apiClient.get<QuestionDetail>(`${S}/questions/${id}`),
  createQuestion: (body: QuestionDraftInput & { subjectId: string; questionType: QuestionType; skillIds: string[] }) =>
    apiClient.post<QuestionDetail>(`${S}/questions`, body),
  openQuestionDraft: (id: string) => apiClient.post<QuestionDetail>(`${S}/questions/${id}/draft`),
  tagQuestionSkill: (id: string, skillId: string) => apiClient.post<QuestionDetail>(`${S}/questions/${id}/skills/${skillId}`),
  untagQuestionSkill: (id: string, skillId: string) =>
    apiClient.delete<QuestionDetail>(`${S}/questions/${id}/skills/${skillId}`),
  reviseQuestionDraft: (versionId: string, body: QuestionDraftInput) =>
    apiClient.put<QuestionDetail>(`${S}/question-versions/${versionId}`, body),
  validateQuestionVersion: (versionId: string) =>
    apiClient.get<ValidationReport>(`${S}/question-versions/${versionId}/validation`),
  previewQuestionVersion: (versionId: string) => apiClient.get<ActivityItem>(`${S}/question-versions/${versionId}/preview`),
  publishQuestionVersion: (versionId: string) =>
    apiClient.post<QuestionDetail>(`${S}/question-versions/${versionId}/publish`),
};

// ---- admin/web/AdminController.java ----

export interface StaffAccount {
  id: string;
  email: string;
  platformRole: PlatformRole;
  status: string;
  emailVerifiedAt: string | null;
}

export const adminApi = {
  staff: () => apiClient.get<StaffAccount[]>("/admin/staff"),
  pendingPrincipals: () => apiClient.get<StaffAccount[]>("/admin/principals/pending"),
  // No password: the person chooses their own from an emailed setup link (admin/service/AdminService.java).
  createStaff: (body: { email: string; role: "TEACHER" | "PRINCIPAL" }) =>
    apiClient.post<StaffAccount>("/admin/staff", body),
  approvePrincipal: (id: string) => apiClient.post<StaffAccount>(`/admin/principals/${id}/approve`),
};
