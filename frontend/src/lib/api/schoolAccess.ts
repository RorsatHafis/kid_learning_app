import { apiClient } from "./client";
import type { ChapterState, EvidenceLevel, SkillStanding } from "./types";

// PD-01: family-granted school access (PLATFORM-EXPANSION.md §4).
// school/web/{ClassAccessController,FamilySchoolAccessController,SchoolController}.java,
// school/service/{SchoolJoinService,SchoolLearningService}.java

// ---- the family's side

/** What joining with a code would mean, before anything changes. */
export interface JoinPreview {
  schoolId: string;
  schoolName: string;
  classId: string;
  className: string;
  /** The curricula the class assigns: the only learning the school will see. */
  assignedCurricula: string[];
  alreadyInClass: boolean;
  schoolHasAccess: boolean;
}

/** A school that can see the child's school-assigned learning, and the child's classes there. */
export interface SchoolAccess {
  schoolId: string;
  schoolName: string;
  grantedAt: string;
  classNames: string[];
}

export const familySchoolsApi = {
  list: (childId: string) => apiClient.get<SchoolAccess[]>(`/children/${childId}/schools`),
  preview: (childId: string, code: string) => apiClient.post<JoinPreview>(`/children/${childId}/schools/preview`, { code }),
  join: (childId: string, code: string) => apiClient.post<JoinPreview>(`/children/${childId}/schools/join`, { code }),
  revoke: (childId: string, schoolId: string) => apiClient.delete<void>(`/children/${childId}/schools/${schoolId}`),
};

// ---- the class's side (its teachers and its school's principal)

export interface JoinCode {
  code: string;
  expiresAt: string;
  expired: boolean;
}

export interface ClassCurriculum {
  curriculumId: string;
  name: string;
  subjectName: string | null;
}

export interface ClassMember {
  childId: string;
  displayName: string;
  joinedAt: string;
}

export interface SchoolChapter {
  position: number;
  title: string;
  completedSteps: number;
  totalSteps: number;
  state: ChapterState;
}

/** A member's journey in one assigned curriculum; `started` is false when they have none in it. */
export interface SchoolJourney {
  curriculumId: string;
  curriculumName: string;
  started: boolean;
  completedSteps: number;
  totalSteps: number;
  currentChapter: SchoolChapter | null;
  chapterCount: number;
  chapters: SchoolChapter[];
}

export interface ClassMemberRow extends ClassMember {
  journeys: SchoolJourney[];
}

/** How many members stand where on a skill; members without the journey count as not started. */
export interface SkillTally {
  skillId: string;
  name: string;
  notStarted: number;
  tooEarlyToTell: number;
  needsPractice: number;
  progressing: number;
  secure: number;
}

export interface ClassLearning {
  curricula: ClassCurriculum[];
  members: ClassMemberRow[];
  skills: { curriculumId: string; name: string; objectives: { title: string; skills: SkillTally[] }[] }[];
}

export interface MemberLearning extends ClassMember {
  journeys: SchoolJourney[];
  skills: {
    curriculumId: string;
    name: string;
    objectives: {
      title: string;
      standing: "NOT_STARTED" | "IN_PROGRESS" | "SECURE";
      skills: {
        skillId: string;
        name: string;
        standing: SkillStanding;
        evidenceLevel: EvidenceLevel;
        scoredAnswers: number;
        correctAnswers: number;
      }[];
    }[];
  }[];
}

// ---- school/service/ClassTrendService.java (X16): how the class has moved, week by week ----

/** Where the class stood at `at`: the children in it by then, each skill of each in one standing. */
export interface ClassTrendPoint {
  at: string;
  children: number;
  notStarted: number;
  tooEarlyToTell: number;
  needsPractice: number;
  progressing: number;
  secure: number;
}

export interface ClassTrend {
  weeks: number;
  /** Skills of the curricula the class assigns. */
  skills: number;
  /** weeks + 1 points, oldest first; the last is now. */
  points: ClassTrendPoint[];
  /** One per week between points: answers given while in the class. */
  answers: { from: string; to: string; answers: number; childrenAnswering: number }[];
  /** Children-on-skills that moved over the window (from its start, or from when the child joined). */
  movement: { becameSecure: number; movedUp: number; firstReading: number; movedDown: number };
  bySkill: { skillId: string; name: string; becameSecure: number; movedUp: number; movedDown: number }[];
}

const C = (classId: string) => `/schools/classes/${classId}`;

export const classAccessApi = {
  /** Null when the class has no code yet (204). */
  joinCode: async (classId: string) => (await apiClient.get<JoinCode | undefined>(`${C(classId)}/join-code`)) ?? null,
  issueCode: (classId: string) => apiClient.post<JoinCode>(`${C(classId)}/join-code`),
  revokeCode: (classId: string) => apiClient.delete<void>(`${C(classId)}/join-code`),
  members: (classId: string) => apiClient.get<ClassMember[]>(`${C(classId)}/children`),
  curricula: (classId: string) => apiClient.get<ClassCurriculum[]>(`${C(classId)}/curricula`),
  assign: (classId: string, curriculumId: string) => apiClient.post<unknown>(`${C(classId)}/curricula/${curriculumId}`),
  unassign: (classId: string, curriculumId: string) => apiClient.delete<void>(`${C(classId)}/curricula/${curriculumId}`),
  learning: (classId: string) => apiClient.get<ClassLearning>(`${C(classId)}/learning`),
  trend: (classId: string, weeks: number) => apiClient.get<ClassTrend>(`${C(classId)}/trend?weeks=${weeks}`),
  memberLearning: (classId: string, childId: string) =>
    apiClient.get<MemberLearning>(`${C(classId)}/children/${childId}/learning`),
  removeMember: (classId: string, childId: string) => apiClient.delete<void>(`${C(classId)}/children/${childId}`),
  /** Who stands where on one skill, on what evidence, and the class's practice for it (X10). */
  skill: (classId: string, skillId: string) => apiClient.get<SkillFollowUp>(`${C(classId)}/skills/${skillId}`),
  assignPractice: (classId: string, skillId: string, activityId: string, childIds: string[]) =>
    apiClient.post<AssignPracticeResult>(`${C(classId)}/skills/${skillId}/practice`, { activityId, childIds }),
};

// ---- school/service/ClassPracticeService.java (X10): following up a skill ----

export type PathItemStatus = "PENDING" | "IN_PROGRESS" | "COMPLETED" | "SKIPPED";

/** The class's most recent practice for this skill for one child, and what became of it. */
export interface PlacedPractice {
  activityId: string;
  activityTitle: string | null;
  /** SKIPPED: withdrawn when the class's access to the child ended. */
  status: PathItemStatus;
  assignedAt: string;
  completedAt: string | null;
  /** The child's standing on the skill when it was placed (X12): with today's, what happened afterwards. */
  standingWhenAssigned: SkillStanding | null;
}

export interface ChildOnSkill {
  childId: string;
  displayName: string;
  standing: SkillStanding;
  evidenceLevel: EvidenceLevel;
  scoredAnswers: number;
  correctAnswers: number;
  /** On the class's curriculum path (practice can be placed). */
  enrolled: boolean;
  latestPractice: PlacedPractice | null;
}

export interface PracticeOption {
  activityId: string;
  title: string;
  questionCount: number;
  difficultyLevel: number | null;
}

export interface SkillFollowUp {
  skillId: string;
  skillName: string;
  objectiveTitle: string;
  curriculumId: string;
  curriculumName: string;
  /** Needs practice first, then too early to tell, progressing, not started, secure; by name within each. */
  children: ChildOnSkill[];
  practice: PracticeOption[];
}

export interface AssignPracticeResult {
  assigned: string[];
  /** The same practice is still waiting for them: nothing was added. */
  alreadyWaiting: string[];
  /** Not on the class's curriculum path, or the content isn't one their path may use. */
  notOnThisPath: string[];
}

// ---- school/service/SchoolOverviewService.java (X7): the staff home ----

/** From the children's answers in the class's assigned curricula only. */
export interface ClassAttention {
  /** Children with at least one skill whose standing is "needs practice". */
  childrenNeedingPractice: number;
  skillsNeedingPractice: number;
  /** Children with no step done in any assigned curriculum (0 when nothing is assigned). */
  childrenNotStarted: number;
}

export interface ClassOverview {
  classId: string;
  className: string;
  schoolId: string;
  schoolName: string;
  children: number;
  curricula: ClassCurriculum[];
  teachers: number;
  /** `open`: families can use it now; `expiresAt` of the unrevoked code, open or expired. */
  joinCode: { open: boolean; expiresAt: string | null };
  attention: ClassAttention;
}

/** A principal's school: who it reaches and what is still to set up. */
export interface SchoolOverview {
  schoolId: string;
  name: string;
  childrenWithAccess: number;
  classes: number;
  classesWithoutTeacher: number;
  classesWithoutCurriculum: number;
  classesWithoutOpenCode: number;
}

export interface StaffOverview {
  /** Principals only; empty for a teacher. */
  schools: SchoolOverview[];
  classes: ClassOverview[];
}

export const staffOverviewApi = {
  get: () => apiClient.get<StaffOverview>("/schools/overview"),
};
