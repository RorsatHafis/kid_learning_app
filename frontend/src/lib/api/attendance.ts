import { apiClient } from "./client";
import type { Page } from "./types";

// ---- school/attendance/AttendanceController.java (M12, owner decisions 2026-11-11) ----
// The school's own student records (separate from families' children) and their per-day attendance.

export type AttendanceStatus = "PRESENT" | "ABSENT" | "LATE" | "EXCUSED";
export const ATTENDANCE_STATUSES: AttendanceStatus[] = ["PRESENT", "ABSENT", "LATE", "EXCUSED"];

export interface SchoolStudent {
  id: string;
  name: string;
  number: string | null;
  since: string;
}

export interface RegisterRow {
  student: SchoolStudent;
  /** Null: not marked yet. */
  status: AttendanceStatus | null;
  note: string | null;
  recordedAt: string | null;
  /** Changed after it was first recorded (with a reason). */
  corrected: boolean;
}

export interface Register {
  classId: string;
  day: string;
  today: boolean;
  /** False for an archived class (read-only). */
  editable: boolean;
  rows: RegisterRow[];
  counts: Record<AttendanceStatus, number>;
  unmarked: number;
}

export interface MonthRow {
  student: SchoolStudent;
  present: number;
  absent: number;
  late: number;
  excused: number;
  marked: number;
}

export interface AttendanceMonth {
  classId: string;
  month: string;
  daysWithMarks: number;
  rows: MonthRow[];
}

/** What adding did: who was added, who was already here, and names held back because they're in another class. */
export interface AddedStudents {
  added: SchoolStudent[];
  alreadyInClass: string[];
  atSchool: { name: string; classId: string; className: string }[];
}

/** One of the school's student records (principal's view): classes now, a family connection, where they left. */
export interface SchoolStudentRow {
  id: string;
  name: string;
  number: string | null;
  classes: { id: string; name: string; archived: boolean }[];
  connected: boolean;
  /** For someone in no class in use: the last class they left, and when. */
  lastClass: { id: string; name: string; archived: boolean } | null;
  leftOn: string | null;
}

export type StudentsShown = "ALL" | "CURRENT" | "FORMER";

export const attendanceApi = {
  /** The school's records as a whole - its principal only. */
  schoolStudents: (schoolId: string, { q, which, page }: { q?: string; which?: StudentsShown; page?: number }) => {
    const params = new URLSearchParams();
    if (q) params.set("q", q);
    if (which && which !== "ALL") params.set("which", which);
    params.set("page", String(page ?? 0));
    return apiClient.get<Page<SchoolStudentRow>>(`/schools/${schoolId}/students?${params}`);
  },
  /** Puts former students (in no class in use) into this class as the same records - the principal only. */
  returnStudents: (classId: string, studentIds: string[]) =>
    apiClient.post<AddedStudents>(`/schools/classes/${classId}/students/return`, { studentIds }),
  students: (classId: string) => apiClient.get<SchoolStudent[]>(`/schools/classes/${classId}/students`),
  /** `sameNameIsNew`: these are new students even though the name is already in another of the school's classes. */
  addStudents: (classId: string, students: { name: string; number?: string | null }[], sameNameIsNew = false) =>
    apiClient.post<AddedStudents>(`/schools/classes/${classId}/students`, { students, sameNameIsNew }),
  /** Brings students (the same school records) from another class of the same school into this one. */
  moveStudents: (classId: string, fromClassId: string, studentIds: string[]) =>
    apiClient.post<AddedStudents>(`/schools/classes/${classId}/students/move`, { fromClassId, studentIds }),
  updateStudent: (classId: string, studentId: string, name: string, number: string | null) =>
    apiClient.put<SchoolStudent>(`/schools/classes/${classId}/students/${studentId}`, { name, number }),
  leave: (classId: string, studentId: string) => apiClient.post<void>(`/schools/classes/${classId}/students/${studentId}/leave`),
  register: (classId: string, day?: string) =>
    apiClient.get<Register>(`/schools/classes/${classId}/attendance${day ? `?day=${day}` : ""}`),
  record: (classId: string, day: string, marks: { studentId: string; status: AttendanceStatus; note?: string | null }[], reason?: string) =>
    apiClient.put<Register>(`/schools/classes/${classId}/attendance?day=${day}`, { marks, reason: reason || undefined }),
  month: (classId: string, month?: string) =>
    apiClient.get<AttendanceMonth>(`/schools/classes/${classId}/attendance/month${month ? `?month=${month}` : ""}`),
};

// ---- school/attendance/StudentLinkController.java (M12): a school record connected to a family's child by consent ----

export interface ClassStudentLink {
  studentId: string;
  status: "PENDING" | "ACTIVE";
  childId: string;
  /** The child's name as the family shares it with the school. */
  childName: string;
}

export interface ClassLinks {
  links: ClassStudentLink[];
  /** The class's learners whose families share them with the school, not yet connected or asked about. */
  candidates: { childId: string; childName: string }[];
}

export interface FamilySchoolLink {
  id: string;
  schoolName: string;
  studentName: string;
  status: "PENDING" | "ACTIVE";
  requestedAt: string;
  decidedAt: string | null;
}

export const schoolLinksApi = {
  forClass: (classId: string) => apiClient.get<ClassLinks>(`/schools/classes/${classId}/student-links`),
  ask: (classId: string, studentId: string, childId: string) =>
    apiClient.post<ClassLinks>(`/schools/classes/${classId}/students/${studentId}/link`, { childId }),
  end: (classId: string, studentId: string) => apiClient.delete<ClassLinks>(`/schools/classes/${classId}/students/${studentId}/link`),
  forChild: (childId: string) => apiClient.get<FamilySchoolLink[]>(`/children/${childId}/school-links`),
  connect: (childId: string, linkId: string) => apiClient.post<FamilySchoolLink[]>(`/children/${childId}/school-links/${linkId}/connect`),
  decline: (childId: string, linkId: string) => apiClient.post<FamilySchoolLink[]>(`/children/${childId}/school-links/${linkId}/decline`),
  disconnect: (childId: string, linkId: string) => apiClient.delete<FamilySchoolLink[]>(`/children/${childId}/school-links/${linkId}`),
};
