// Real backend transport layer for the YulMe application.
// Every API call goes to the actual Spring Boot backend; there is no demo/mock mode.

const BASE_URL: string = (import.meta as any).env?.VITE_API_BASE_URL || 'http://localhost:8080/api/v1';

const TOKEN_KEY = 'yulme.accessToken';
const ACCOUNT_KEY = 'yulme.accountId';
const CHILD_KEY = 'yulme.childId';

export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY);
}

export function isAuthenticated(): boolean {
  return !!getToken();
}

export function getStoredChildId(): string | null {
  return localStorage.getItem(CHILD_KEY);
}

export function setStoredChildId(childId: string) {
  localStorage.setItem(CHILD_KEY, childId);
}

export function getStoredRole(): PlatformRole | null { return localStorage.getItem('yulme.role') as PlatformRole | null; }

export function clearSession() {
  localStorage.removeItem(TOKEN_KEY);
  localStorage.removeItem(ACCOUNT_KEY);
  localStorage.removeItem('yulme.role');
  localStorage.removeItem(CHILD_KEY);
}

function newIdempotencyKey(): string {
  if ('randomUUID' in crypto) return crypto.randomUUID();
  // Fallback for older browsers without crypto.randomUUID.
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

export class ApiError extends Error {
  status: number;
  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const token = getToken();
  const res = await fetch(`${BASE_URL}${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...(init.headers || {}),
    },
  });

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = await res.json();
      message = body.detail || body.message || body.title || message;
    } catch {
      // Response wasn't JSON - keep the default message.
    }
    throw new ApiError(res.status, message);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

// ---- Types (mirroring the backend DTOs exactly - see YulMe-Backend web/*Dtos.java) ----

export type PlatformRole = 'PARENT' | 'TEACHER' | 'PRINCIPAL' | 'ADMIN';

export type AuthResponse = {
  accountId: string;
  email: string;
  platformRole: PlatformRole;
  accessToken: string | null;
  expiresAt: string | null;
};

export type ChildStatus = 'ACTIVE' | 'ARCHIVED';

export type ChildResponse = {
  id: string;
  displayName: string;
  dateOfBirth: string;
  status: ChildStatus;
};

export type CurriculumSummary = {
  id: string;
  name: string;
  subjectCode: string;
  subjectName: string;
  ageHubCode: string;
  ageHubName: string;
};

export type EnrollmentStatus = 'ACTIVE' | 'COMPLETED' | 'PAUSED' | 'WITHDRAWN';

export type EnrollmentResponse = {
  id: string;
  childId: string;
  curriculumVersionId: string;
  status: EnrollmentStatus;
  enrolledAt: string;
};

export type LearningPathItemSource = 'CURRICULUM' | 'ADAPTIVE' | 'REVIEW' | 'TEACHER_ASSIGNED';
export type LearningPathItemStatus = 'PENDING' | 'IN_PROGRESS' | 'COMPLETED' | 'SKIPPED';

export type LearningPathItemResponse = {
  id: string;
  activityVersionId: string;
  sequenceOrder: number;
  status: LearningPathItemStatus;
  source: LearningPathItemSource;
};

export type QuestionType = 'MULTIPLE_CHOICE' | 'TRUE_FALSE' | 'SHORT_ANSWER' | 'NUMERIC' | 'MATCHING';

export type QuestionOptionResponse = {
  id: string;
  label: string;
  displayOrder: number;
};

export type ActivityItemResponse = {
  activityItemId: string;
  sequenceOrder: number;
  questionVersionId: string;
  questionType: QuestionType;
  prompt: string;
  options: QuestionOptionResponse[];
};

export type ActivityVersionDetail = {
  activityVersionId: string;
  activityId: string;
  title: string;
  instructions: string | null;
  difficultyLevel: number | null;
  estimatedDurationSeconds: number | null;
  items: ActivityItemResponse[];
};

export type ActivityAttemptStatus = 'IN_PROGRESS' | 'COMPLETED' | 'ABANDONED';

export type AttemptResponse = {
  id: string;
  childId: string;
  activityVersionId: string;
  learningPathItemId: string | null;
  status: ActivityAttemptStatus;
  startedAt: string;
  completedAt: string | null;
  score: number | null;
};

export type AnswerRecordResponse = {
  id: string;
  questionVersionId: string;
  correct: boolean;
  timeSpentSeconds: number | null;
  occurredAt: string;
};

export type AdaptiveRecommendationResponse = {
  direction: string;
  targetSkillId: string;
  insertedLearningPathItemId: string;
  activityVersionId: string;
};

export type SmartReviewCheckResponse = {
  reviewOutcomeRecorded: boolean;
  flaggedForReview: boolean;
  skillId: string;
  insertedLearningPathItemId: string | null;
  activityVersionId: string | null;
};

export type ParentInsightType = 'STRENGTH' | 'WEAKNESS' | 'PROGRESS' | 'RECOMMENDATION';

export type ParentInsightResponse = {
  id: string;
  insightType: ParentInsightType;
  headline: string;
  detail: string | null;
  supportingData: Record<string, unknown>;
  occurredAt: string;
};

export type Lookup = {id:string; code:string; name:string};
export type ObjectiveLookup = {id:string; code:string; title:string};
export type SkillLookup = {id:string; code:string; name:string};
export type TeacherClass = {id:string; schoolId:string; name:string};
export type AdminStaff = {id:string; email:string; platformRole:PlatformRole; status:string; emailVerifiedAt:string|null};
export type SchoolResponse = {id:string; name:string; principalAccountId:string|null};
export type ClassResponse = {id:string; schoolId:string; name:string};

// ---- API surface ----

export const api = {
  // --- auth ---
  async register(email: string, password: string, familyName?: string, role?: PlatformRole): Promise<AuthResponse> {
    const auth = await request<AuthResponse>('/auth/register', {
      method: 'POST',
      body: JSON.stringify({ email, password, familyName, role, idempotencyKey: newIdempotencyKey() }),
    });
    saveSession(auth);
    return auth;
  },

  async login(email: string, password: string): Promise<AuthResponse> {
    const auth = await request<AuthResponse>('/auth/login', {
      method: 'POST',
      body: JSON.stringify({ email, password }),
    });
    saveSession(auth);
    return auth;
  },

  // --- children ---
  listChildren(): Promise<ChildResponse[]> {
    return request('/children');
  },

  createChild(displayName: string, dateOfBirth: string): Promise<ChildResponse> {
    return request('/children', {
      method: 'POST',
      body: JSON.stringify({ displayName, dateOfBirth }),
    });
  },

  // --- curricula ---
  listCurricula(): Promise<CurriculumSummary[]> {
    return request('/curricula');
  },

  // --- enrollment ---
  enroll(childId: string, curriculumId: string): Promise<EnrollmentResponse> {
    return request(`/children/${childId}/enrollments`, {
      method: 'POST',
      body: JSON.stringify({ curriculumId }),
    });
  },

  getEnrollment(enrollmentId: string): Promise<EnrollmentResponse> {
    return request(`/enrollments/${enrollmentId}`);
  },

  getNextActivity(enrollmentId: string): Promise<LearningPathItemResponse> {
    return request(`/enrollments/${enrollmentId}/next-activity`);
  },

  // --- content ---
  getActivityVersion(activityVersionId: string): Promise<ActivityVersionDetail> {
    return request(`/activity-versions/${activityVersionId}`);
  },

  // --- attempts ---
  startAttempt(
    childId: string,
    activityVersionId: string,
    learningPathItemId?: string | null
  ): Promise<AttemptResponse> {
    return request(`/children/${childId}/activity-attempts`, {
      method: 'POST',
      body: JSON.stringify({ activityVersionId, learningPathItemId: learningPathItemId ?? null }),
    });
  },

  getAttempt(attemptId: string): Promise<AttemptResponse> {
    return request(`/activity-attempts/${attemptId}`);
  },

  listAnswers(attemptId: string): Promise<AnswerRecordResponse[]> {
    return request(`/activity-attempts/${attemptId}/answers`);
  },

  submitAnswer(
    attemptId: string,
    questionVersionId: string,
    submittedAnswer: string,
    timeSpentSeconds?: number
  ): Promise<AnswerRecordResponse> {
    return request(`/activity-attempts/${attemptId}/answers`, {
      method: 'POST',
      body: JSON.stringify({
        questionVersionId,
        submittedAnswer,
        timeSpentSeconds: timeSpentSeconds ?? null,
        idempotencyKey: newIdempotencyKey(),
      }),
    });
  },

  completeAttempt(attemptId: string): Promise<AttemptResponse> {
    return request(`/activity-attempts/${attemptId}/complete`, { method: 'POST' });
  },

  abandonAttempt(attemptId: string): Promise<AttemptResponse> {
    return request(`/activity-attempts/${attemptId}/abandon`, { method: 'POST' });
  },

  /** May resolve to undefined - the backend returns 204 when there's nothing to recommend yet (see ActivityAttemptController). */
  getAdaptiveNext(attemptId: string): Promise<AdaptiveRecommendationResponse | undefined> {
    return request(`/activity-attempts/${attemptId}/adaptive-next`, { method: 'POST' });
  },

  /** May resolve to undefined for the same reason as getAdaptiveNext. */
  getSmartReviewCheck(attemptId: string): Promise<SmartReviewCheckResponse | undefined> {
    return request(`/activity-attempts/${attemptId}/smart-review-check`, { method: 'POST' });
  },

  // --- admin / school operations ---
  listAdminStaff(): Promise<AdminStaff[]> { return request('/admin/staff'); },
  listPendingPrincipals(): Promise<AdminStaff[]> { return request('/admin/principals/pending'); },
  approvePrincipal(id:string): Promise<AdminStaff> { return request(`/admin/principals/${id}/approve`, {method:'POST'}); },
  createAdminLesson(subjectId:string,title:string){return request<{id:string;title:string;ownerAccountId:string}>('/teacher/lessons',{method:'POST',body:JSON.stringify({subjectId,title})});},
  createAdminChallenge(activityVersionId:string,title:string,description:string,targetCompletions:number){return request('/challenges',{method:'POST',body:JSON.stringify({activityVersionId,title,description,targetCompletions})});},
  createAdminStaff(email:string,password:string,role:'TEACHER'|'PRINCIPAL'): Promise<AdminStaff> {
    return request('/admin/staff',{method:'POST',body:JSON.stringify({email,password,role})});
  },
  listSchools(): Promise<SchoolResponse[]> { return request('/schools/mine'); },
  createSchool(name: string, principalAccountId?: string): Promise<SchoolResponse> {
    return request('/schools', {
      method: 'POST',
      body: JSON.stringify({ name, principalAccountId: principalAccountId || null })
    });
  },
  listStaffClasses(): Promise<ClassResponse[]> { return request('/schools/classes/mine'); },
  createSchoolClass(schoolId:string,name:string): Promise<ClassResponse> {
    return request(`/schools/${schoolId}/classes`,{method:'POST',body:JSON.stringify({name})});
  },
  assignTeacher(classId:string,teacherAccountId:string) {
    return request(`/schools/classes/${classId}/teachers`,{method:'POST',body:JSON.stringify({teacherAccountId})});
  },
  enrollChildInClass(classId:string,childId:string) {
    return request(`/schools/classes/${classId}/children`,{method:'POST',body:JSON.stringify({childId})});
  },

  // --- teacher/admin studio ---
  listTeacherSubjects(): Promise<Lookup[]> { return request('/teacher/subjects'); },
  listAgeHubs(): Promise<Lookup[]> { return request('/teacher/age-hubs'); },
  listObjectives(subjectId:string): Promise<ObjectiveLookup[]> { return request(`/teacher/subjects/${subjectId}/objectives`); },
  listSkills(subjectId:string): Promise<SkillLookup[]> { return request(`/teacher/subjects/${subjectId}/skills`); },
  tagActivity(activityId:string,skillId:string){return request(`/teacher/activities/${activityId}/skills/${skillId}`,{method:'POST'});},
  createLesson(subjectId:string,title:string): Promise<{id:string;title:string;ownerAccountId:string}> { return request('/teacher/lessons',{method:'POST',body:JSON.stringify({subjectId,title})}); },
  createLessonVersion(lessonId:string,body:string){return request<{id:string;versionNumber:number;status:string}>(`/teacher/lessons/${lessonId}/versions`,{method:'POST',body:JSON.stringify({body})});},
  publishLesson(versionId:string){return request(`/teacher/lessons/versions/${versionId}/publish`,{method:'POST'});},
  createActivity(lessonId:string,activityType:string,title:string){return request<{id:string;title:string;activityType:string}>(`/teacher/lessons/${lessonId}/activities`,{method:'POST',body:JSON.stringify({activityType,title})});},
  createActivityVersion(activityId:string,instructions:string,difficultyLevel:number){return request<{id:string;versionNumber:number;status:string}>(`/teacher/activities/${activityId}/versions`,{method:'POST',body:JSON.stringify({instructions,difficultyLevel})});},
  addActivityItem(versionId:string,questionVersionId:string,sequenceOrder=0,points=1){return request(`/teacher/activities/versions/${versionId}/items`,{method:'POST',body:JSON.stringify({questionVersionId,sequenceOrder,points})});},
  publishActivity(versionId:string){return request(`/teacher/activities/versions/${versionId}/publish`,{method:'POST'});},
  createQuestion(subjectId:string,questionType:QuestionType){return request<{id:string;questionType:QuestionType;ownerAccountId:string}>('/teacher/questions',{method:'POST',body:JSON.stringify({subjectId,questionType})});},
  createQuestionVersion(questionId:string,prompt:string,correctAnswer?:string,difficultyLevel=1){return request<{id:string;versionNumber:number;status:string}>(`/teacher/questions/${questionId}/versions`,{method:'POST',body:JSON.stringify({prompt,correctAnswer,difficultyLevel})});},
  addQuestionOption(versionId:string,label:string,correct:boolean,displayOrder:number){return request(`/teacher/questions/versions/${versionId}/options`,{method:'POST',body:JSON.stringify({label,correct,displayOrder})});},
  publishQuestion(versionId:string){return request(`/teacher/questions/versions/${versionId}/publish`,{method:'POST'});},
  createCurriculum(subjectId:string,ageHubId:string,name:string){return request<{id:string;name:string;ownerAccountId:string}>('/teacher/curricula',{method:'POST',body:JSON.stringify({subjectId,ageHubId,name})});},
  createCurriculumVersion(curriculumId:string){return request<{id:string;versionNumber:number;status:string}>(`/teacher/curricula/${curriculumId}/versions`,{method:'POST'});},
  addCurriculumObjective(versionId:string,learningObjectiveId:string,displayOrder=0){return request(`/teacher/curricula/versions/${versionId}/objectives`,{method:'POST',body:JSON.stringify({learningObjectiveId,displayOrder})});},
  publishCurriculum(versionId:string){return request(`/teacher/curricula/versions/${versionId}/publish`,{method:'POST'});},
  listMyClasses():Promise<TeacherClass[]>{return request('/schools/classes/mine');},
  listClassChildren(classId:string){return request<{id:string;displayName:string}[]>(`/schools/classes/${classId}/children`);},
  assignCurriculumToClass(classId:string,curriculumId:string){return request(`/schools/classes/${classId}/curricula/${curriculumId}`,{method:'POST'});},
  createChallenge(activityVersionId:string,title:string,description:string,targetCompletions:number){return request('/challenges',{method:'POST',body:JSON.stringify({activityVersionId,title,description,targetCompletions})});},
  assignChallenge(challengeId:string,classId:string){return request(`/challenges/${challengeId}/classes/${classId}`,{method:'POST'});},
  listChallenges(classId:string){return request(`/challenges/classes/${classId}`);},
  getStreak(childId:string){return request<{childId:string;currentStreak:number;longestStreak:number;lastActivityDate:string|null}>(`/children/${childId}/streak`);},
  getProgress(childId:string){return request<{childId:string;completedActivities:number;learningMinutes:number;currentStreak:number;longestStreak:number;completedPathItems:number;totalPathItems:number}>(`/children/${childId}/progress`);},

  // --- parent insights ---
  generateParentInsights(childId: string): Promise<ParentInsightResponse[]> {
    return request(`/children/${childId}/parent-insights/generate`, { method: 'POST' });
  },

  listParentInsights(childId: string): Promise<ParentInsightResponse[]> {
    return request(`/children/${childId}/parent-insights`);
  },
};

function saveSession(auth: AuthResponse) {
  if (auth.accessToken) localStorage.setItem(TOKEN_KEY, auth.accessToken); else clearSession();
  if (auth.accessToken) { localStorage.setItem(ACCOUNT_KEY, auth.accountId); localStorage.setItem('yulme.role', auth.platformRole); }
}
