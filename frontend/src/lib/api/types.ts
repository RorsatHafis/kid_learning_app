// Mirrors of backend/src/main/java/com/platform/**/entity + **/web DTOs.
// Every shape here has been read directly from the Java source (not
// guessed) — see the file path noted above each block. Re-verify against
// the backend before trusting a shape that isn't cited this way.

import type { Locale } from "../../i18n/store";

// ---- identity/entity/PlatformRole.java, identity/web/AuthResponse.java ----

/** No CHILD value — children are accessed through a parent's family
 * membership, never their own account. See PlatformRole's javadoc. */
/** One page of a server-paged list (common/web/PageResponse.java). `page` is zero-based. */
export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  total: number;
}

export type PlatformRole = "PARENT" | "TEACHER" | "PRINCIPAL" | "ADMIN";

export interface AuthResponse {
  accountId: string;
  email: string;
  platformRole: PlatformRole;
  /** null on the PRINCIPAL registration branch, until the address is confirmed */
  accessToken: string | null;
  expiresAt: string | null;
  refreshToken: string | null;
  refreshTokenExpiresAt: string | null;
  /** The account's interface language, applied as soon as it signs in. */
  preferredLanguage: Locale;
  /** M10a: set when this sign-in is a child's device - it opens only this child's learning space (email is then null). */
  childId?: string | null;
}

// ---- identity/web/AccountController.java ----

export type AccountStatus = "ACTIVE" | "DISABLED" | "PENDING_VERIFICATION";

/** identity/phone/PhoneController.PhoneStatus: the account's phone (M9b). */
export interface PhoneStatus {
  /** Whether text messages can be sent here at all (a provider is set up). */
  available: boolean;
  /** E.164, e.g. +85512345678; null when the account has none. */
  phone: string | null;
  /** The same number as people write it: +855 12 345 678. */
  display: string | null;
  verifiedAt: string | null;
}

/** security/stepup/SecondFactorService.Status — an administrator's authenticator app (M9a). */
export interface SecondFactorStatus {
  enabled: boolean;
  /** Set up but not yet confirmed with a first code (not in force). */
  pending: boolean;
  recoveryCodesLeft: number;
}

/** identity/web/AccountController.DeviceResponse — a sign-in on some device. */
export interface SignedInDevice {
  id: string;
  userAgent: string | null;
  signedInAt: string;
  lastActiveAt: string;
  current: boolean;
  /** M10a: a child's device - a sign-in that opens only this child's space. */
  childId?: string | null;
}

export interface AccountProfile {
  id: string;
  email: string;
  platformRole: PlatformRole;
  status: AccountStatus;
  /** null until the sign-in address has been proven by an emailed link. */
  emailVerifiedAt: string | null;
  createdAt: string;
  /** When the account will be erased, if its owner asked to delete it. */
  eraseAfter: string | null;
  /** The version of the terms and privacy notice the account agreed to (Y15). */
  legalVersionAccepted: string | null;
  /** The interface language the account chose (en or km). */
  preferredLanguage: Locale;
}

// ---- child/entity/ChildStatus.java, child/web/ChildDtos.java ----

/** DELETION_PENDING: the family asked to delete the child; erased at `eraseAfter` unless kept. */
export type ChildStatus = "ACTIVE" | "ARCHIVED" | "DELETION_PENDING";

export interface Child {
  id: string;
  displayName: string;
  /** ISO date (LocalDate). Null for a caregiver (M10b), whose pages never read it. */
  dateOfBirth: string;
  status: ChildStatus;
  eraseAfter: string | null;
  /** The language the child's own space is shown in - the child's, not the parent's. */
  preferredLanguage: Locale;
  /** The Learning Buddy the child chose (child/web/ChildDtos.java); Momo the otter until they choose. */
  learningBuddy: LearningBuddyKind;
  /** M10b: how the reader relates to the child - the family, or a caregiver's scope. */
  access?: "FAMILY" | "VIEW_PROGRESS" | "OPEN_SPACE";
}

/** child/entity/LearningBuddy.java */
export type LearningBuddyKind = "OTTER" | "ELEPHANT" | "BUFFALO" | "GIBBON" | "FROG";

// ---- curriculum/web/CurriculumDtos.java ----

export interface CurriculumSummary {
  id: string;
  name: string;
  subjectCode: string;
  subjectName: string;
  ageHubCode: string;
  ageHubName: string;
}

/** curriculum/web/CurriculumDtos.CurriculumChoiceResponse: a curriculum as offered for one child. */
export interface CurriculumChoice extends CurriculumSummary {
  minAge: number;
  /** null when the age group has no upper bound. */
  maxAge: number | null;
  /** For the child's age group, by the rule automatic enrolment uses. */
  forChildAge: boolean;
  ageOrder: number;
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

export type QuestionType = "MULTIPLE_CHOICE" | "TRUE_FALSE" | "SHORT_ANSWER" | "NUMERIC" | "MATCHING" | "ORDERING" | "SORTING";

/** content/media/MediaService.MediaRef — an uploaded picture or recording, fetched by its signed URL (expires within hours). */
export interface MediaRef {
  id: string;
  type: "IMAGE" | "AUDIO";
  mimeType: string;
  url: string;
}

export interface QuestionOption {
  id: string;
  label: string;
  /** ORDERING: the order the child is given the items in — never the answer. */
  displayOrder: number;
  /** MATCHING only: which column the item is in. Which items pair up is never sent. */
  matchSide?: "LEFT" | "RIGHT" | null;
  /** SORTING only (X23): a group to sort into, or an item to sort. Which group an item belongs to is never sent. */
  sortRole?: "GROUP" | "ITEM" | null;
  /** A picture standing for the item; `label` then describes it (screen readers). */
  image?: MediaRef | null;
  /** The item read aloud. */
  audio?: MediaRef | null;
}

export interface ActivityItem {
  activityItemId: string;
  sequenceOrder: number;
  questionVersionId: string;
  questionType: QuestionType;
  prompt: string;
  /** A picture shown with the prompt, and the prompt read aloud (for children who don't read yet). */
  promptImage?: MediaRef | null;
  /** What the picture shows, for screen readers. */
  promptImageDescription?: string | null;
  promptAudio?: MediaRef | null;
  /** The choices for MULTIPLE_CHOICE (answered by option id). Not used for
   * TRUE_FALSE ("true"/"false") or free-response types. Never carries which
   * option is correct — that stays server-side. */
  options: QuestionOption[];
  /** Y41: the author's hint, offered before answering (never the answer); null when there is none. */
  hint?: string | null;
  /** "km" when the question is given in Khmer (its Khmer is complete and the child reads Khmer); null: as written. */
  language?: "km" | null;
  /**
   * V83: given as written, the language it is written in when that is known ("en": saved through the bilingual
   * Studio); null when unknown (older content, possibly typed in Khmer) - then nothing is marked, never guessed.
   */
  writtenIn?: "en" | null;
}

export interface ActivityVersionDetail {
  activityVersionId: string;
  activityId: string;
  title: string;
  instructions: string | null;
  difficultyLevel: number | null;
  estimatedDurationSeconds: number | null;
  items: ActivityItem[];
  /** What the activity teaches before it asks; null when it has none. */
  introduction: ActivityIntroduction | null;
}

/** content/web/ActivityContentDtos.LearnerIntroduction: an explanation or worked example, a picture, a recording. */
export interface ActivityIntroduction {
  text: string | null;
  image: MediaRef | null;
  imageDescription: string | null;
  audio: MediaRef | null;
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

/** Feedback on an answer already given (learning/service/AnswerFeedbackService):
 * the correct answer, so a mistake can be explained. Only ever present on a
 * recorded answer — the content endpoint never carries an answer key. */
export interface CorrectAnswer {
  /** MULTIPLE_CHOICE: the correct option id(s); ORDERING: every item in the correct order; empty for other types. */
  optionIds: string[];
  /** The correct answer as the child would read it. */
  text: string | null;
  /** MATCHING: each left item's id with its match's id; SORTING: each item's id with its group's id; empty otherwise. */
  matches?: { leftOptionId: string; rightOptionId: string }[];
}

export interface AnswerRecord {
  id: string;
  questionVersionId: string;
  correct: boolean;
  timeSpentSeconds: number | null;
  occurredAt: string;
  correctAnswer: CorrectAnswer | null;
  /** Y41: the author's explanation, only ever with a recorded answer; null when there is none. */
  explanation?: string | null;
  /** D-16: the child opened the hint before answering - practice, not evidence they showed it on their own. */
  hintUsed?: boolean;
  /** The answer as recorded, exactly as it was sent. */
  submittedAnswer?: string;
}

// ---- learning/service/LearningJourneyService.java (records served as-is by LearningJourneyController) ----

export interface SkillRef {
  id: string;
  name: string;
}

export interface CurriculumRef {
  id: string;
  name: string;
  subjectCode: string | null;
  subjectName: string | null;
  ageHubName: string | null;
}

/** Why a step is on the path — each value maps to a recorded cause. */
export type PlacementReason =
  | "CURRICULUM"
  | "REINFORCE_PREREQUISITE"
  | "REINFORCE_SKILL"
  | "CHALLENGE"
  | "REVIEW"
  | "TEACHER_ASSIGNED"
  | "UNEXPLAINED";

export interface Explanation {
  reason: PlacementReason;
  /** The governing skill (adaptive) or reviewed skill (review). */
  skill: SkillRef | null;
  /** REINFORCE_PREREQUISITE only: the prerequisite practised. */
  targetSkill: SkillRef | null;
  afterActivityTitle: string | null;
  afterScore: number | null;
  decidedAt: string | null;
  /** TEACHER_ASSIGNED only: the class and school that placed it ("Grade 1 A · Wat Phnom Primary"), when recorded. */
  assignedBy: string | null;
}

export interface JourneyStep {
  learningPathItemId: string;
  activityVersionId: string;
  title: string;
  activityType: string;
  difficultyLevel: number | null;
  estimatedDurationSeconds: number | null;
  questionCount: number;
  sequenceOrder: number;
  status: LearningPathItemStatus;
  source: LearningPathItemSource;
  skills: SkillRef[];
  score: number | null;
  completedAt: string | null;
  explanation: Explanation;
  /** The chapter (learning objective) this step belongs to; null for practice outside every chapter. */
  chapterId: string | null;
}

/** Where a child is in a chapter, from its steps. Not mastery (see Skills & evidence). */
export type ChapterState = "COMPLETE" | "CURRENT" | "STARTED" | "UPCOMING" | "NOT_READY";

/** One learning objective of the enrolled curriculum version, in the order its educators set. */
export interface Chapter {
  id: string;
  position: number;
  title: string;
  description: string | null;
  totalSteps: number;
  completedSteps: number;
  state: ChapterState;
}

export interface Journey {
  enrollmentId: string;
  childId: string;
  curriculum: CurriculumRef;
  status: EnrollmentStatus;
  enrolledAt: string;
  completedSteps: number;
  totalSteps: number;
  /** Same rule as next-activity: an activity underway, then placed practice, then the curriculum. */
  nextStepId: string | null;
  chapters: Chapter[];
  /** In play order: done (as completed), then waiting in the order they will be served. */
  steps: JourneyStep[];
}

export interface EnrollmentOverview {
  enrollmentId: string;
  curriculum: CurriculumRef;
  status: EnrollmentStatus;
  enrolledAt: string;
  completedSteps: number;
  totalSteps: number;
  nextStep: JourneyStep | null;
  /** The chapter holding the next step; null when nothing is waiting. */
  currentChapter: Chapter | null;
  chapterCount: number;
  /** Every chapter with its state, in curriculum order. */
  chapters: Chapter[];
  /** A family path's fit with the child's age now; null for a class's path. */
  ageFit: AgeFit | null;
}

/** learning/service/LearningJourneyService.AgeFit: whether a family path is for the child's age now. */
export interface AgeFit {
  forChildAge: boolean;
  childAge: number;
  minAge: number;
  maxAge: number | null;
  /** A published platform path in the same subject for the child's age, which they aren't on. */
  suggestedCurriculumId: string | null;
  suggestedCurriculumName: string | null;
}

export type AdaptationDecisionType = "CONTINUE" | "INCREASE_DIFFICULTY" | "REMEDIATE" | "SCHEDULE_REVIEW";

export interface DecisionSummary {
  type: AdaptationDecisionType;
  governingSkill: SkillRef | null;
  targetSkill: SkillRef | null;
  placedActivityTitle: string | null;
}

export interface CompletedActivity {
  attemptId: string;
  activityVersionId: string;
  title: string;
  subjectName: string | null;
  /** null for free practice (no path item). */
  source: LearningPathItemSource | null;
  score: number | null;
  correctAnswers: number;
  totalAnswers: number;
  startedAt: string;
  completedAt: string;
  skills: SkillRef[];
  decision: DecisionSummary | null;
  /** D-16: how many of the answers were given after opening the hint. */
  hintedAnswers?: number;
}

// ---- learner/service/EvidenceStandard.java, learning/service/SkillProgressService.java ----

/** Evidence strength, separate from performance: NONE is "not assessed", never "weak". */
export type EvidenceLevel = "NONE" | "PARTIAL" | "SUFFICIENT" | "STRONG";
export type SkillStanding = "NOT_STARTED" | "TOO_EARLY_TO_TELL" | "NEEDS_PRACTICE" | "PROGRESSING" | "SECURE";
export type ObjectiveStanding = "NOT_STARTED" | "IN_PROGRESS" | "SECURE";

/** Deliberately no mastery number — the server reports what the evidence supports. */
export interface SkillProgress {
  skillId: string;
  name: string;
  description: string | null;
  evidenceLevel: EvidenceLevel;
  standing: SkillStanding;
  scoredAnswers: number;
  correctAnswers: number;
  lastPracticedAt: string | null;
  inReview: boolean;
  reviewDueAt: string | null;
  /** D-17: answers are at the "Secure" level but all from one day - it reads progressing until another day confirms it. */
  secureAfterAnotherDay?: boolean;
}

export interface ObjectiveProgress {
  objectiveId: string;
  title: string;
  description: string | null;
  standing: ObjectiveStanding;
  skillsMapped: number;
  skillsPractised: number;
  skillsWithSufficientEvidence: number;
  skills: SkillProgress[];
}

// ---- learning/service/SkillStoryService.java (X19): one skill, explained to its family ----

export interface ActivityOnSkill {
  attemptId: string;
  activityTitle: string;
  at: string;
  /** Answers in this activity on this skill, and how many were right. */
  answers: number;
  correct: number;
  /** Where the skill stood after this activity (same rule as today's standing); null if not recorded. */
  standingAfter: SkillStanding | null;
  evidenceAfter: number;
  /** D-16: answers here given after opening the hint (counted in answers/correct, but practice, not evidence). */
  withHint?: number;
}

export interface WaitingStep {
  learningPathItemId: string;
  activityTitle: string;
  status: LearningPathItemStatus;
  source: LearningPathItemSource;
  addedAt: string | null;
  curriculumName: string;
}

export interface SkillStory {
  skillId: string;
  name: string;
  description: string | null;
  objectiveTitle: string;
  curriculumName: string;
  now: SkillProgress;
  /** Newest first, the most recent 20. */
  activities: ActivityOnSkill[];
  activitiesInAll: number;
  waiting: WaitingStep[];
  /** D-16: answers on the skill given with the hint, which `now`'s counts leave out. */
  hintedAnswers?: number;
}

export interface CurriculumProgress {
  enrollmentId: string;
  curriculum: CurriculumRef;
  objectives: ObjectiveProgress[];
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
/** M6: a plan that lifts a family's limit, as the server's catalogue has it (a price only when one is set). */
export interface PlanOffer {
  planId: string;
  name: string;
  limit: number | null;
  priceAmountMinor: number | null;
  priceCurrency: string | null;
  billingInterval: "MONTH" | "YEAR" | null;
}

/** M10a: a child's device (lib/api/childDevices.ts). */
export interface ChildDeviceCode {
  id: string;
  code: string;
  expiresAt: string;
}

export interface ChildDeviceCodeState {
  id: string;
  status: "OPEN" | "CLAIMED" | "APPROVED" | "DELIVERED" | "CANCELLED" | "EXPIRED";
  deviceUserAgent: string | null;
  expiresAt: string;
}

export interface ChildDeviceCollected {
  status: "WAITING" | "SIGNED_IN";
  accountId: string | null;
  childId: string | null;
  childName: string | null;
  accessToken: string | null;
  expiresAt: string | null;
  refreshToken: string | null;
  refreshTokenExpiresAt: string | null;
}

export interface ApiProblem {
  type?: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  errors?: Record<string, string>;
  lockedUntil?: string;
  /** 429 only (security/ratelimit/AuthRateLimitFilter.java), mirrors Retry-After. */
  retryAfterSeconds?: number;
  /** 402 only (Y44, SchoolPaymentRequiredException): the school's services wait for payment. */
  schoolPaymentRequired?: boolean;
  /** M8: 403 when the action needs the password re-entered (a step-up). */
  stepUpRequired?: boolean;
  /** M9a: 422 when a step-up needs a code from the authenticator app (none, wrong or used). */
  secondFactorRequired?: boolean;
  /** M9a: 403 when an administrator has to turn an authenticator app on before stepping up. */
  secondFactorSetupRequired?: boolean;
  /** M3: 409 when today's learning time is used and a new activity would start. */
  dailyLimitReached?: boolean;
  usedMinutes?: number;
  limitMinutes?: number;
  schoolId?: string;
  /** 409 "Plan Limit Reached" only: which entitlement and its limit (PlanLimitException). */
  entitlement?: string;
  limit?: number;
  /** M6: why a plan refused, whose plan it is, and that nothing already there is lost. */
  reason?: "PLAN_LIMIT" | "SCHOOL_UNPAID" | "SUBSCRIPTION_EXPIRED";
  audience?: "FAMILY" | "SCHOOL";
  dataKept?: boolean;
  /** M6, a family's limit only: the real plans a family may choose that include more (limit null: no limit). */
  unlockedBy?: PlanOffer[];
  /** 409 "Storage Limit Reached" only (StorageLimitException): nothing was stored. */
  usedBytes?: number;
  limitBytes?: number;
  neededBytes?: number;
  correlationId?: string;
  timestamp?: string;
}

// ---- school/web/SchoolController.java ----
// A class's children and their learning are in lib/api/schoolAccess.ts (PD-01): school views
// carry only what the school may see, never the parent-facing Child.

export interface SchoolClassSummary {
  id: string;
  schoolId: string;
  name: string;
  /** X17: an archived class takes no new children, curricula, teachers or practice. */
  archived: boolean;
}

// school/web/SchoolController.java SchoolResponse
export interface SchoolSummary {
  id: string;
  name: string;
  principalAccountId: string;
  /** PD-26 (Y17): the school takes nothing new; families and children keep everything. */
  archived: boolean;
}

// school/web/SchoolStaffController.java
export interface SchoolClassWithTeachers {
  id: string;
  schoolId: string;
  name: string;
  teachers: { accountId: string; email: string }[];
  archived: boolean;
  /** M12 P1: the academic year the class belongs to; null for a class placed in none. */
  academicYearId?: string | null;
}

export type SchoolStaffStatus = "INVITED" | "ACTIVE" | "DECLINED" | "REMOVED";

export interface SchoolStaffMember {
  id: string;
  accountId: string;
  email: string;
  status: SchoolStaffStatus;
  invitedAt: string;
  respondedAt: string | null;
}

export interface SchoolInvitation {
  id: string;
  schoolId: string;
  schoolName: string;
  invitedAt: string;
}

/** A school whose staff the signed-in teacher is on (Y21). */
export interface SchoolMembership {
  id: string;
  schoolId: string;
  schoolName: string;
  joinedAt: string | null;
}

// ---- learning/service/ProgressChangeService.java (X12): what changed over a window ----

/** How a skill moved; listed in this order. */
export type ChangeKind = "BECAME_SECURE" | "MOVED_UP" | "FIRST_READING" | "STARTED" | "PRACTISED" | "MOVED_DOWN";

export interface SkillChange {
  skillId: string;
  name: string;
  kind: ChangeKind;
  /** Where it stood at the start of the window, by the same rule as today's standing. */
  then: SkillStanding;
  now: SkillStanding;
  answersSince: number;
  lastPracticedAt: string | null;
  /** In one of the child's learning paths, so it has a skill page (false: practised only outside them). */
  onPlan?: boolean;
}

export interface ProgressSince {
  since: string;
  days: number;
  answersSince: number;
  /** Only skills answered on in the window: nothing changes without answers. */
  changes: SkillChange[];
}

// ---- child/service/DailyLearningTimeService.java (M3) ----

/** A child's learning day: the parent's limit (null = none), the minutes used today (server time), extra given. */
export interface LearningToday {
  day: string;
  limitMinutes: number | null;
  usedMinutes: number;
  extraMinutes: number;
  remainingMinutes: number | null;
  reached: boolean;
  /** At most 5 minutes left: the next activity is the last for today. */
  nearEnd: boolean;
  /** The IANA time zone the learning day follows (the family's choice, else the platform's). */
  zone: string;
}

/** child/service/DailyLearningTimeService.FamilyDay: the zone a family's learning day follows. */
export interface FamilyLearningDay {
  zone: string;
  /** Chosen by a parent (else the platform's zone). */
  familyChoice: boolean;
  platformZone: string;
}

