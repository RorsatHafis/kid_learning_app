import { apiClient } from "./client";
import type { ActivityIntroduction, ActivityItem, MediaRef, Page, PlatformRole, QuestionType } from "./types";

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

/**
 * A catalog name's Khmer as written so far (LOCALIZATION.md "Content in two languages", V81): either may be null.
 * Each is given to a Khmer reader once written; the English otherwise.
 */
export interface KhmerName {
  name: string | null;
  description: string | null;
}

/** A catalog name's Khmer in a request: blank removes one (not translated); leaving `khmer` out keeps both as they are. */
export interface KhmerNameBody {
  name?: string | null;
  description?: string | null;
}

export interface Subject {
  id: string;
  code: string;
  name: string;
  description: string | null;
  /** X14: archived subjects are listed only in the archived view. */
  archived?: boolean;
  khmer?: KhmerName;
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
  khmer?: KhmerName;
}

export interface CatalogObjective {
  id: string;
  code: string;
  title: string;
  description: string | null;
  skillIds: string[];
  archived?: boolean;
  khmer?: KhmerName;
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
  archived?: boolean;
  khmer?: KhmerName;
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
  archived?: boolean;
  khmer?: KhmerName;
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
  /** The server's English sentence; the Studio words the issue from `code` and `values`. */
  message: string;
  skillId: string | null;
  /** What the sentence names: a skill, a prerequisite, a chapter, a count of drafts. */
  values?: Record<string, string | number | boolean>;
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
  archived?: boolean;
  /** L8: the help a child gets in the latest version; null without a version. */
  support?: ActivitySupport | null;
}

export interface ActivitySupport {
  questions: number;
  readAloud: number;
  explained: number;
  hinted: number;
  introduction: boolean;
}

export interface LessonSummary {
  id: string;
  title: string;
  subjectId: string;
  /** Only the activities a search found, when it found the lesson through them. */
  activities: ActivitySummary[];
  /** Every activity in the lesson. */
  activityCount: number;
  archived?: boolean;
  /** M5b: who besides its author can find it. */
  visibility?: LessonVisibility;
  /** M5b: copied from a colleague's shared lesson. */
  copied?: boolean;
}

/** M5b (content/entity/LessonVisibility.java): PUBLIC is deferred. */
export type LessonVisibility = "PRIVATE" | "SCHOOL";

/** M5b (LessonSharingService.SharedLesson): a colleague's shared lesson and its published activities. */
export interface SharedLesson {
  id: string;
  title: string;
  subjectId: string;
  schools: string[];
  activities: { id: string; title: string; questions: number }[];
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
  /** When this item's version was retired by a newer one: the question's published version now (else null). */
  currentVersionId?: string | null;
  currentVersionNumber?: number | null;
}

export interface ActivityVersionDetail extends VersionSummary {
  instructions: string | null;
  difficultyLevel: number | null;
  estimatedDurationSeconds: number | null;
  items: ItemDetail[];
  /** What this version practises / gives evidence for (PD-08); frozen once it is published. */
  skillIds: string[];
  /** What the version teaches before it asks; null when it has none. */
  introduction: ActivityIntroduction | null;
  /** Its Khmer (LOCALIZATION.md): any field may be null; `complete`: a child reading Khmer gets the activity's words in Khmer. */
  khmer?: {
    title: string | null;
    instructions: string | null;
    introduction: string | null;
    introductionImageDescription: string | null;
    complete: boolean;
  };
}

/** Sets a draft's introduction; every field optional. */
export interface IntroductionInput {
  text: string | null;
  imageAssetId: string | null;
  imageDescription: string | null;
  audioAssetId: string | null;
  /** Its Khmer, saved with it; absent: the Khmer is left as it is. */
  khmer?: { text: string | null; imageDescription: string | null };
}

export interface ActivityDetail {
  id: string;
  title: string;
  activityType: string;
  lessonId: string;
  subjectId: string;
  skillIds: string[];
  versions: ActivityVersionDetail[];
  archived?: boolean;
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

/** content/studio/StudioDtos.SortGroupDetail (X23) — a SORTING group; `group` is its number. */
export interface SortGroupDetail {
  id: string;
  label: string;
  group: number;
  image: MediaRef | null;
}

/** content/studio/StudioDtos.SortItemDetail (X23) — a SORTING item and the number of the group it belongs to. */
export interface SortItemDetail {
  id: string;
  label: string;
  group: number;
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
  /** SORTING (X23): its groups (by number) and its items. */
  groups: SortGroupDetail[];
  sortItems: SortItemDetail[];
  /** Y41: offered to the child before answering; never the answer. */
  hint: string | null;
  /** Y41: shown to the child after answering, with the right answer. */
  explanation: string | null;
  /** The version's Khmer beside its English (LOCALIZATION.md); absent from older servers. */
  khmer?: KhmerDetail;
}

/**
 * A version's Khmer as written so far: any field may be null; `options` maps each choice, pair side, item or group
 * (by id) to its label. `complete`: a child reading Khmer gets this version in Khmer (otherwise in English).
 * `translatable`: false for a short answer, which is checked against the author's own words.
 */
export interface KhmerDetail {
  prompt: string | null;
  hint: string | null;
  explanation: string | null;
  promptImageDescription: string | null;
  options: Record<string, string>;
  complete: boolean;
  translatable: boolean;
}

/** A draft's Khmer, saved with its English: one label per row, in the order of the request's own rows. */
export interface KhmerDraft {
  prompt: string | null;
  hint: string | null;
  explanation: string | null;
  promptImageDescription: string | null;
  options?: string[];
  pairLefts?: string[];
  pairRights?: string[];
  items?: string[];
  groups?: string[];
  sortItems?: string[];
}

export interface QuestionSummary {
  id: string;
  subjectId: string;
  questionType: QuestionType;
  skillIds: string[];
  latestPrompt: string | null;
  publishedVersionId: string | null;
  versions: VersionSummary[];
  archived?: boolean;
  /** The published version's own wording (what reusing the question gives a child); null without one. */
  publishedPrompt?: string | null;
  /** How far the latest version's Khmer has got (LOCALIZATION.md); a typed-word answer isn't translated. */
  khmer?: "NONE" | "PARTLY" | "COMPLETE" | "NOT_TRANSLATABLE" | null;
}

/** The question bank's filters; `live`: only questions with a published version. */
export interface QuestionQuery {
  /** X14: the archived questions instead. */
  archived?: boolean;
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
  archived?: boolean;
}

// ---- content/validation/ValidationReport.java ----
export interface ValidationIssue {
  code: string;
  /** The server's English sentence; the Studio words the issue from `code` and `values`. */
  message: string;
  /** What the sentence names: a skill, a question's position, a count. */
  values?: Record<string, string | number | boolean>;
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
  introduction: ActivityIntroduction | null;
  /** "km" when the activity's own words are given in Khmer (complete); null when as written. Each item says its own. */
  language?: "km" | null;
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

export interface SortGroupInput {
  label: string;
  imageAssetId?: string;
}

/** `group`: the item's group, as a place (0-based) in the draft's groups. */
export interface SortItemInput {
  label: string;
  group: number;
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
  /** SORTING (X23): 2–4 groups and 4–10 items, each in one of them (every group used, to publish). */
  groups?: SortGroupInput[];
  sortItems?: SortItemInput[];
  /** Y41: up to 300 characters; null or blank for none. */
  hint?: string | null;
  /** Y41: up to 600 characters; null or blank for none. */
  explanation?: string | null;
  /** Its Khmer, saved with the English in the same request (blank: not translated yet). */
  khmer?: KhmerDraft;
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

/** X14: what can be renamed, archived and restored in the Studio, by its API path. */
export type ManagedKind = "subjects" | "skills" | "objectives" | "curricula" | "lessons" | "activities" | "questions";

// ---- content/review/ContentReviewController.java (Y14) ----

export type ReviewKind = "QUESTION" | "ACTIVITY" | "CURRICULUM";
export interface ReviewView {
  id: string;
  status: "OPEN" | "APPROVED" | "CHANGES_REQUESTED" | "WITHDRAWN";
  requestedBy: string;
  requestedAt: string;
  decidedBy: string | null;
  decidedAt: string | null;
  note: string | null;
  /** Asked by the signed-in account. */
  mine: boolean;
  /** The draft changed after the review was asked: it can't be approved as is. */
  changedSince: boolean;
}
/** `required`: this draft is published through a review; `review`: its latest review. */
export interface ReviewState {
  required: boolean;
  review: ReviewView | null;
}
export interface ReviewQueueRow {
  id: string;
  kind: ReviewKind;
  versionId: string;
  contentId: string;
  subjectId: string;
  title: string;
  requestedBy: string;
  requestedAt: string;
}
const VERSION_PATH: Record<ReviewKind, string> = {
  QUESTION: "question-versions",
  ACTIVITY: "activity-versions",
  CURRICULUM: "curriculum-versions",
};
export const reviewApi = {
  settings: () => apiClient.get<{ required: boolean }>("/studio/review-settings"),
  state: (kind: ReviewKind, versionId: string) => apiClient.get<ReviewState>(`/studio/${VERSION_PATH[kind]}/${versionId}/review`),
  // 422 when the draft fails its checks, like a refused publish.
  request: (kind: ReviewKind, versionId: string) => apiClient.post<ReviewState>(`/studio/${VERSION_PATH[kind]}/${versionId}/review`),
  withdraw: (kind: ReviewKind, versionId: string) => apiClient.delete<ReviewState>(`/studio/${VERSION_PATH[kind]}/${versionId}/review`),
  queue: (page = 0, size = 50) =>
    apiClient.get<{ items: ReviewQueueRow[]; page: number; size: number; total: number }>(`/studio/reviews?page=${page}&size=${size}`),
  // 409 when asked by the reviewer, or the draft changed since.
  approve: (reviewId: string) => apiClient.post<ReviewState>(`/studio/reviews/${reviewId}/approve`),
  requestChanges: (reviewId: string, note: string) =>
    apiClient.post<ReviewState>(`/studio/reviews/${reviewId}/request-changes`, { note }),
};

// ---- content/withdrawal/ContentWithdrawalController.java (Y16) ----

export type WithdrawalKind = "QUESTION" | "ACTIVITY";
export interface WithdrawalState {
  withdrawal: { id: string; kind: WithdrawalKind; versionId: string; reason: string; withdrawnBy: string; withdrawnAt: string } | null;
  /** An activity version taken down because a question in it was. */
  viaQuestion: boolean;
}
const WITHDRAWAL_PATH: Record<WithdrawalKind, string> = { QUESTION: "question-versions", ACTIVITY: "activity-versions" };
export const withdrawalApi = {
  state: (kind: WithdrawalKind, versionId: string) =>
    apiClient.get<WithdrawalState>(`/studio/${WITHDRAWAL_PATH[kind]}/${versionId}/withdrawal`),
  // 409 for a draft, or a version already taken down.
  withdraw: (kind: WithdrawalKind, versionId: string, reason: string) =>
    apiClient.post<WithdrawalState>(`/studio/${WITHDRAWAL_PATH[kind]}/${versionId}/withdrawal`, { reason }),
  restore: (kind: WithdrawalKind, versionId: string) =>
    apiClient.delete<WithdrawalState>(`/studio/${WITHDRAWAL_PATH[kind]}/${versionId}/withdrawal`),
};

export const studioApi = {
  // ---- X14: edit details, archive, restore (archived = no new uses; nothing a child has changes)
  updateDetails: (kind: Exclude<ManagedKind, "questions">, id: string, body: { name: string; description?: string | null; khmer?: KhmerNameBody }) =>
    apiClient.put<void>(`${S}/${kind}/${id}`, body),
  archive: (kind: ManagedKind, id: string) => apiClient.post<void>(`${S}/${kind}/${id}/archive`),
  restore: (kind: ManagedKind, id: string) => apiClient.post<void>(`${S}/${kind}/${id}/restore`),
  subjects: (archived = false) => apiClient.get<Subject[]>(`${S}/subjects${archived ? "?archived=true" : ""}`),
  createSubject: (body: { code: string; name: string; description?: string; khmer?: KhmerNameBody }) =>
    apiClient.post<Subject>(`${S}/subjects`, body),
  ageHubs: () => apiClient.get<AgeHub[]>(`${S}/age-hubs`),
  catalog: (subjectId: string, archived = false) =>
    apiClient.get<Catalog>(`${S}/subjects/${subjectId}/catalog${archived ? "?archived=true" : ""}`),
  createSkill: (subjectId: string, body: { code: string; name: string; description?: string; khmer?: KhmerNameBody }) =>
    apiClient.post<CatalogSkill>(`${S}/subjects/${subjectId}/skills`, body),
  createObjective: (subjectId: string, body: { code: string; title: string; description?: string; khmer?: KhmerNameBody }) =>
    apiClient.post<CatalogObjective>(`${S}/subjects/${subjectId}/objectives`, body),
  linkObjectiveSkill: (objectiveId: string, skillId: string) =>
    apiClient.post<CatalogObjective>(`${S}/objectives/${objectiveId}/skills/${skillId}`),
  addPrerequisite: (skillId: string, prerequisiteSkillId: string) =>
    apiClient.post<CatalogSkill>(`${S}/skills/${skillId}/prerequisites/${prerequisiteSkillId}`),

  curricula: (archived = false) => apiClient.get<CurriculumSummary[]>(`${S}/curricula${archived ? "?archived=true" : ""}`),
  curriculum: (id: string) => apiClient.get<CurriculumDetail>(`${S}/curricula/${id}`),
  createCurriculum: (body: { subjectId: string; ageHubId: string; name: string; khmer?: KhmerNameBody }) =>
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
  lessons: (subjectId: string, query: { text?: string; archived?: boolean; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams({ subjectId });
    if (query.text?.trim()) params.set("q", query.text.trim());
    if (query.archived) params.set("archived", "true");
    params.set("page", String(query.page ?? 0));
    params.set("size", String(query.size ?? 10));
    return apiClient.get<Page<LessonSummary>>(`${S}/lessons?${params}`);
  },
  createLesson: (body: { subjectId: string; title: string }) => apiClient.post<LessonSummary>(`${S}/lessons`, body),
  /** M5b: the author shares their lesson with their school's teachers (403 for anyone else, 409 a platform lesson). */
  setLessonVisibility: (lessonId: string, visibility: LessonVisibility) =>
    apiClient.put<void>(`${S}/lessons/${lessonId}/visibility`, { visibility }),
  /** M5b: colleagues' shared lessons in a subject. */
  sharedLessons: (subjectId: string) => apiClient.get<SharedLesson[]>(`${S}/shared-lessons?subjectId=${encodeURIComponent(subjectId)}`),
  /** M5b: copies one into your own lessons, as drafts (404 when it isn't shared with you, 409 no storage room). */
  copySharedLesson: (lessonId: string) => apiClient.post<LessonSummary>(`${S}/shared-lessons/${lessonId}/copy`),
  createActivity: (lessonId: string, body: { title: string; skillIds: string[] }) =>
    apiClient.post<ActivityDetail>(`${S}/lessons/${lessonId}/activities`, body),
  activity: (id: string) => apiClient.get<ActivityDetail>(`${S}/activities/${id}`),
  openActivityDraft: (id: string) => apiClient.post<ActivityDetail>(`${S}/activities/${id}/draft`),
  /** A new activity in the same lesson starting as a copy of this one's latest version (questions reused). */
  duplicateActivity: (id: string, title: string) => apiClient.post<ActivityDetail>(`${S}/activities/${id}/duplicate`, { title }),
  tagActivitySkill: (id: string, skillId: string) => apiClient.post<ActivityDetail>(`${S}/activities/${id}/skills/${skillId}`),
  untagActivitySkill: (id: string, skillId: string) =>
    apiClient.delete<ActivityDetail>(`${S}/activities/${id}/skills/${skillId}`),
  /** What the activity teaches before it asks (its draft). */
  setActivityIntroduction: (versionId: string, body: IntroductionInput) =>
    apiClient.put<ActivityDetail>(`${S}/activity-versions/${versionId}/introduction`, body),
  reviseActivityDraft: (
    versionId: string,
    body: {
      instructions: string | null;
      difficultyLevel: number | null;
      estimatedDurationSeconds: number | null;
      /** The Khmer of the activity's name and instructions; absent: left as it is. */
      khmer?: { title: string | null; instructions: string | null };
    },
  ) => apiClient.put<ActivityDetail>(`${S}/activity-versions/${versionId}`, body),
  replaceActivityItems: (versionId: string, items: { questionVersionId: string }[]) =>
    apiClient.put<ActivityDetail>(`${S}/activity-versions/${versionId}/items`, { items }),
  validateActivityVersion: (versionId: string) =>
    apiClient.get<ValidationReport>(`${S}/activity-versions/${versionId}/validation`),
  /** As a child reading `language` gets it (Khmer only where complete); left out: as written. */
  previewActivityVersion: (versionId: string, language?: "en" | "km") =>
    apiClient.get<LearnerActivityPreview>(`${S}/activity-versions/${versionId}/preview${language ? `?language=${language}` : ""}`),
  publishActivityVersion: (versionId: string) =>
    apiClient.post<ActivityDetail>(`${S}/activity-versions/${versionId}/publish`),
  /** Y42: a new question written in the activity and added at its end - one step (its subject and skills: the activity's). */
  addNewQuestion: (versionId: string, body: QuestionDraftInput & { subjectId: string; questionType: QuestionType }) =>
    apiClient.post<ActivityDetail>(`${S}/activity-versions/${versionId}/questions`, { ...body, skillIds: [] }),
  /** Y42: the activity and its draft questions, published together or not at all. */
  publishActivityWithQuestions: (versionId: string) =>
    apiClient.post<ActivityDetail>(`${S}/activity-versions/${versionId}/publish-with-questions`),

  /** One page of the caller's question bank for a subject, searched and filtered by the server. */
  questions: (subjectId: string, query: QuestionQuery = {}) => {
    const params = new URLSearchParams({ subjectId });
    if (query.text?.trim()) params.set("q", query.text.trim());
    if (query.type) params.set("type", query.type);
    if (query.status) params.set("status", query.status);
    if (query.skillId) params.set("skillId", query.skillId);
    if (query.live) params.set("live", "true");
    if (query.archived) params.set("archived", "true");
    params.set("page", String(query.page ?? 0));
    params.set("size", String(query.size ?? 25));
    return apiClient.get<Page<QuestionSummary>>(`${S}/questions?${params}`);
  },
  question: (id: string) => apiClient.get<QuestionDetail>(`${S}/questions/${id}`),
  createQuestion: (body: QuestionDraftInput & { subjectId: string; questionType: QuestionType; skillIds: string[] }) =>
    apiClient.post<QuestionDetail>(`${S}/questions`, body),
  openQuestionDraft: (id: string) => apiClient.post<QuestionDetail>(`${S}/questions/${id}/draft`),
  /** A new question whose first draft copies this one's latest version (the original is untouched). */
  duplicateQuestion: (id: string) => apiClient.post<QuestionDetail>(`${S}/questions/${id}/duplicate`),
  tagQuestionSkill: (id: string, skillId: string) => apiClient.post<QuestionDetail>(`${S}/questions/${id}/skills/${skillId}`),
  untagQuestionSkill: (id: string, skillId: string) =>
    apiClient.delete<QuestionDetail>(`${S}/questions/${id}/skills/${skillId}`),
  reviseQuestionDraft: (versionId: string, body: QuestionDraftInput) =>
    apiClient.put<QuestionDetail>(`${S}/question-versions/${versionId}`, body),
  validateQuestionVersion: (versionId: string) =>
    apiClient.get<ValidationReport>(`${S}/question-versions/${versionId}/validation`),
  /** As a child reading `language` gets it (Khmer only where its Khmer is complete); without one, the author's own. */
  previewQuestionVersion: (versionId: string, language?: "en" | "km") =>
    apiClient.get<ActivityItem>(`${S}/question-versions/${versionId}/preview${language ? `?language=${language}` : ""}`),
  /**
   * A picture (PNG, JPEG, WebP, ≤ 2 MB) or recording (MP3, M4A, Ogg, WAV, ≤ 5 MB), inspected by the server.
   * The same file uploaded again is the same library item (X13).
   */
  /** M5a: `onProgress` reports the share sent (0..1); `signal` cancels the upload. */
  uploadMedia: (file: File, onProgress?: (fraction: number) => void, signal?: AbortSignal) => {
    const form = new FormData();
    form.append("file", file);
    return apiClient.post<MediaItem>(`${S}/media`, form, { onUploadProgress: onProgress, signal });
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
  /** When the family asked to delete the account, the day it will be erased (Y3). */
  eraseAfter: string | null;
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
  createStaff: (body: { email: string; role: StaffRole }) =>
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
