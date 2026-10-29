import { apiClient } from "./client";
import type { Page } from "./types";

// ---- admin/service/AdminInsightService.java (X15): the administrator's operational reads ----
// Aggregates and operational facts only: no child's name or learning record.

export interface AdminSchoolRow {
  id: string;
  name: string;
  status: string;
  createdAt: string | null;
  principalEmail: string | null;
  principalStatus: string | null;
  classes: number;
  teachers: number;
  childrenWithAccess: number;
  classesWithoutTeacher: number;
  classesWithoutCurriculum: number;
  /** M12 B0: the organization this school is a branch of, and how many branches it has (1: an independent school). */
  organizationId: string;
  organizationName: string | null;
  organizationBranches: number;
}

export interface AdminClassRow {
  id: string;
  name: string;
  teachers: string[];
  children: number;
  curricula: string[];
  openCode: boolean;
}

export interface AdminSchoolDetail {
  school: AdminSchoolRow;
  classes: AdminClassRow[];
  staff: string[];
}

export type PlacementSource = "CURRICULUM" | "ADAPTIVE" | "REVIEW" | "TEACHER_ASSIGNED";

export interface LearningOperations {
  lastSevenDays: {
    attemptsStarted: number;
    attemptsCompleted: number;
    childrenLearning: number;
    answers: number;
    answersCorrect: number;
    /** Started more than a day ago and still open: children who left mid-activity. */
    attemptsLeftOpen: number;
    placementsBySource: Record<PlacementSource, number>;
    reviewItemsActive: number;
    reviewDueNow: number;
  };
  /** Skills an offered, published curriculum relies on that nothing published and in use practises (first 50). */
  coverageGaps: { skillId: string; skill: string; subject: string; curriculum: string }[];
  /** Every gap; `coverageGaps` lists the first of them. */
  coverageGapTotal: number;
  /** Age groups with no platform curriculum offered to families. */
  ageGroupsWithNothingOffered: string[];
}

/** One week (Monday, UTC). */
export interface WeekReport {
  weekOf: string;
  newFamilies: number;
  childrenLearning: number;
  activitiesCompleted: number;
  answers: number;
  newSchools: number;
}

export interface SystemStatus {
  version: string;
  profiles: string;
  schemaVersion: string;
  databaseBytes: number;
  mediaFiles: number;
  mediaBytes: number;
  mediaFreeBytes: number;
  mailHost: string;
  publicUrl: string;
  uptimeSeconds: number;
  javaVersion: string;
  heapUsedBytes: number;
  heapMaxBytes: number;
  checkedAt: string;
}

export const adminInsightsApi = {
  schools: (query: { text?: string; page?: number; size?: number } = {}) => {
    const params = new URLSearchParams();
    if (query.text?.trim()) params.set("q", query.text.trim());
    params.set("page", String(query.page ?? 0));
    params.set("size", String(query.size ?? 25));
    return apiClient.get<Page<AdminSchoolRow>>(`/admin/schools?${params}`);
  },
  school: (id: string) => apiClient.get<AdminSchoolDetail>(`/admin/schools/${id}`),
  learningOperations: () => apiClient.get<LearningOperations>("/admin/learning-operations"),
  weekly: () => apiClient.get<WeekReport[]>("/admin/reports/weekly"),
  system: () => apiClient.get<SystemStatus>("/admin/system"),
};
