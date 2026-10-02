import { apiClient } from "./client";
import type { ActivityItem, MediaRef, Page, PlatformRole, QuestionType } from "./types";

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

// ---- content/studio/JourneyBuilderService.java: a version as the journey a child meets ----

export type JourneyIssueCode =
  | "OBJECTIVE_OTHER_SUBJECT"
  | "CHAPTER_NO_SKILLS"
  | "CHAPTER_NO_STEPS"
  | "SKILL_ARCHIVED"
  | "SKILL_NO_ACTIVITY"
  | "SKILL_NO_EVIDENCE"
  | "SKILL_ONE_ACTIVITY"
  | "SKILL_NO_HARDER_STEP"
  | "SKILL_REPEATED"
  | "PREREQUISITE_LATER"
  | "PREREQUISITE_NO_ACTIVITY";

/** WARNING: changes what a child experiences. TIP: limits how the path can adapt. Neither blocks publishing. */
export interface JourneyIssue {
  code: JourneyIssueCode;
  severity: "WARNING" | "TIP";
  message: string;
  skillId: string | null;
}

export interface JourneyStep {
  activityId: string;
  activityVersionId: string;
  title: string;
  versionNumber: number;
  difficulty: number | null;
  estimatedSeconds: number | null;
  questions: number;
  placedBySkill: string;
}

export interface JourneySkill {
  skillId: string;
  name: string;
  archived: boolean;
  publishedActivities: number;
  draftOnlyActivities: number;
  evidenceQuestions: number;
  minDifficulty: number | null;
  maxDifficulty: number | null;
  firstTaughtInChapter: number | null;
  /** `chapter` is where this journey teaches the prerequisite, or null when it isn't in it. */
  prerequisites: { skillId: string; name: string; chapter: number | null; hasActivity: boolean }[];
}

export interface JourneyChapter {
  position: number;
  objectiveId: string;
  code: string;
  title: string;
  description: string | null;
  otherSubject: boolean;
  estimatedSeconds: number;
  stepsWithoutDuration: number;
  steps: JourneyStep[];
  skills: JourneySkill[];
  issues: JourneyIssue[];
}

export interface JourneyMap {
  curriculumId: string;
  curriculumVersionId: string;
  versionNumber: number;
  status: PublicationStatus;
  /** True for an unsaved order: nothing was written. */
  preview: boolean;
  totals: {
    chapters: number;
    steps: number;
    skills: number;
    estimatedSeconds: number;
    stepsWithoutDuration: number;
    warnings: number;
    tips: number;
  };
  chapters: JourneyChapter[];
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
  /** Only the activities a search found, when it found the lesson through them. */
  activities: ActivitySummary[];
  /** Every activity in the lesson. */
  activityCount: number;
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
  /** What this version practises / gives evidence for (PD-08); frozen once it is published. */
  skillIds: string[];
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
  image: MediaRef | null;
  audio: MediaRef | null;
}

/** content/studio/StudioDtos.MatchPairDetail — one pair of a MATCHING question, author view. */
export interface MatchPairDetail {
  leftId: string;
  left: string;
  rightId: string | null;
  right: string | null;
  leftImage: MediaRef | null;
  rightImage: MediaRef | null;
}

/** content/studio/StudioDtos.OrderItemDetail — one ORDERING item; a version lists them in the correct order. */
export interface OrderItemDetail {
  id: string;
  label: string;
  image: MediaRef | null;
  audio: MediaRef | null;
}

export interface QuestionVersionDetail extends VersionSummary {
  prompt: string;
  correctAnswer: string | null;
  difficultyLevel: number | null;
  promptImage: MediaRef | null;
  promptImageDescription: string | null;
  promptAudio: MediaRef | null;
  options: OptionDetail[];
  pairs: MatchPairDetail[];
  items: OrderItemDetail[];
  /** What this version practises / gives evidence for (PD-08); frozen once it is published. */
  skillIds: string[];
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

/** The question bank's filters; `live`: only questions with a published version. */
export interface QuestionQuery {
  text?: string;
  type?: QuestionType;
  status?: PublicationStatus;
  skillId?: string;
  live?: boolean;
  page?: number;
  size?: number;
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

/** Media fields are asset ids from `uploadMedia`; left out when there is none. */
export interface OptionInput {
  label: string;
  correct: boolean;
  imageAssetId?: string;
  audioAssetId?: string;
}

export interface MatchPairInput {
  left: string;
  right: string;
  leftImageAssetId?: string;
  rightImageAssetId?: string;
}

export interface OrderItemInput {
  label: string;
  imageAssetId?: string;
  audioAssetId?: string;
}

export interface QuestionDraftInput {
  prompt: string;
  correctAnswer: string | null;
  difficultyLevel: number | null;
  promptImageAssetId?: string;
  /** Required to publish when there is a picture: screen readers read it. */
  promptImageDescription?: string;
  promptAudioAssetId?: string;
  options?: OptionInput[];
  /** MATCHING: each item with its match (2–6 pairs to publish). */
  pairs?: MatchPairInput[];
  /** ORDERING: the items in their correct order (3–8 to publish). */
  items?: OrderItemInput[];
}

// ---- content/media/MediaLibraryService.java (X13): the media library ----

/** A library item: a MediaRef plus what authors manage. `usedBy`: question versions using it. */
export interface MediaItem extends MediaRef {
  sizeBytes: number;
  width: number | null;
  height: number | null;
  title: string | null;
  /** What it shows: offered as the screen-reader text wherever it is used. */
  description: string | null;
  archived: boolean;
  createdAt: string | null;
  usedBy: number;
  /** Whether this author may describe, archive or delete it (platform media: administrators). */
  editable: boolean;
}

export type MediaUseField = "PROMPT_IMAGE" | "PROMPT_AUDIO" | "ANSWER_IMAGE" | "ANSWER_AUDIO";

export interface MediaUse {
  questionId: string;
  subjectId: string;
  versionId: string;
  versionNumber: number;
  status: PublicationStatus;
  prompt: string;
  field: MediaUseField;
}

export interface MediaDetail {
  item: MediaItem;
  uses: MediaUse[];
}

export interface MediaQuery {
  type?: "IMAGE" | "AUDIO";
  text?: string;
  archived?: boolean;
  unused?: boolean;
  page?: number;
  size?: number;
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
  /** The version as the journey a child will meet (content/studio/JourneyBuilderService.java). */
  journey: (versionId: string) => apiClient.get<JourneyMap>(`${S}/curriculum-versions/${versionId}/journey`),
  /** The same for chapters in an unsaved order; nothing is written. */
  previewJourney: (versionId: string, objectiveIds: string[]) =>
    apiClient.post<JourneyMap>(`${S}/curriculum-versions/${versionId}/journey/preview`, { objectiveIds }),

  /** One page of the caller's lessons in a subject; `text` matches lesson and activity titles. */
  lessons: (subjectId: string, query: { text?: string; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams({ subjectId });
    if (query.text?.trim()) params.set("q", query.text.trim());
    params.set("page", String(query.page ?? 0));
    params.set("size", String(query.size ?? 10));
    return apiClient.get<Page<LessonSummary>>(`${S}/lessons?${params}`);
  },
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

  /** One page of the caller's question bank for a subject, searched and filtered by the server. */
  questions: (subjectId: string, query: QuestionQuery = {}) => {
    const params = new URLSearchParams({ subjectId });
    if (query.text?.trim()) params.set("q", query.text.trim());
    if (query.type) params.set("type", query.type);
    if (query.status) params.set("status", query.status);
    if (query.skillId) params.set("skillId", query.skillId);
    if (query.live) params.set("live", "true");
    params.set("page", String(query.page ?? 0));
    params.set("size", String(query.size ?? 25));
    return apiClient.get<Page<QuestionSummary>>(`${S}/questions?${params}`);
  },
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
  /**
   * A picture (PNG, JPEG, WebP, ≤ 2 MB) or recording (MP3, M4A, Ogg, WAV, ≤ 5 MB), inspected by the server.
   * The same file uploaded again is the same library item (X13).
   */
  uploadMedia: (file: File) => {
    const form = new FormData();
    form.append("file", file);
    return apiClient.post<MediaItem>(`${S}/media`, form);
  },
  /** The media library: platform media and the author's own, newest first (X13). */
  mediaLibrary: (query: MediaQuery = {}) => {
    const params = new URLSearchParams();
    if (query.type) params.set("type", query.type);
    if (query.text) params.set("q", query.text);
    if (query.archived) params.set("archived", "true");
    if (query.unused) params.set("unused", "true");
    params.set("page", String(query.page ?? 0));
    params.set("size", String(query.size ?? 24));
    return apiClient.get<Page<MediaItem>>(`${S}/media?${params}`);
  },
  mediaDetail: (id: string) => apiClient.get<MediaDetail>(`${S}/media/${id}`),
  describeMedia: (id: string, body: { title: string | null; description: string | null }) =>
    apiClient.put<MediaItem>(`${S}/media/${id}`, body),
  archiveMedia: (id: string) => apiClient.post<MediaItem>(`${S}/media/${id}/archive`),
  restoreMedia: (id: string) => apiClient.post<MediaItem>(`${S}/media/${id}/restore`),
  deleteMedia: (id: string) => apiClient.delete<void>(`${S}/media/${id}`),
  publishQuestionVersion: (versionId: string) =>
    apiClient.post<QuestionDetail>(`${S}/question-versions/${versionId}/publish`),
};

// ---- admin/web/AdminController.java ----

/** The platform roles that are staff; families are never listed with them. */
export type StaffRole = "TEACHER" | "PRINCIPAL" | "ADMIN";

export interface StaffAccount {
  id: string;
  email: string;
  platformRole: PlatformRole;
  status: string;
  emailVerifiedAt: string | null;
}

export const adminApi = {
  /** One page of staff accounts (never families), by email; `text` matches part of an email. */
  staff: (query: { text?: string; role?: StaffRole; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams();
    if (query.text?.trim()) params.set("q", query.text.trim());
    if (query.role) params.set("role", query.role);
    params.set("page", String(query.page ?? 0));
    params.set("size", String(query.size ?? 25));
    return apiClient.get<Page<StaffAccount>>(`/admin/staff?${params}`);
  },
  pendingPrincipals: () => apiClient.get<StaffAccount[]>("/admin/principals/pending"),
  // No password: the person chooses their own from an emailed setup link (admin/service/AdminService.java).
  createStaff: (body: { email: string; role: "TEACHER" | "PRINCIPAL" }) =>
    apiClient.post<StaffAccount>("/admin/staff", body),
  approvePrincipal: (id: string) => apiClient.post<StaffAccount>(`/admin/principals/${id}/approve`),
};

// ---- admin/web/AdminOperationsController.java ----

export interface AuditEntry {
  id: string;
  occurredAt: string;
  actorId: string | null;
  actorEmail: string | null;
  actorType: string;
  action: string;
  resourceType: string;
  resourceId: string | null;
  metadata: Record<string, unknown>;
  correlationId: string | null;
}

/** One page, newest first; pass nextBefore/nextBeforeId back to continue. Both are null on the last page. */
export interface AuditPage {
  entries: AuditEntry[];
  nextBefore: string | null;
  nextBeforeId: string | null;
}

export interface AuditQuery {
  action?: string;
  actorId?: string;
  resourceId?: string;
  before?: string;
  beforeId?: string;
  limit?: number;
}

export const operationsApi = {
  auditEvents: (query: AuditQuery) => {
    const params = new URLSearchParams();
    for (const [key, value] of Object.entries(query)) {
      if (value !== undefined && value !== "") params.set(key, String(value));
    }
    const qs = params.toString();
    return apiClient.get<AuditPage>(`/admin/audit-events${qs ? `?${qs}` : ""}`);
  },
  auditActions: () => apiClient.get<string[]>("/admin/audit-events/actions"),
  findAccount: (email: string) => apiClient.get<StaffAccount>(`/admin/accounts?email=${encodeURIComponent(email)}`),
  suspend: (id: string, reason: string) => apiClient.post<StaffAccount>(`/admin/accounts/${id}/suspend`, { reason }),
  reinstate: (id: string, reason: string) => apiClient.post<StaffAccount>(`/admin/accounts/${id}/reinstate`, { reason }),
};
