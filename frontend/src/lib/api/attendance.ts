import { apiClient } from "./client";

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

export const attendanceApi = {
  students: (classId: string) => apiClient.get<SchoolStudent[]>(`/schools/classes/${classId}/students`),
  addStudents: (classId: string, students: { name: string; number?: string | null }[]) =>
    apiClient.post<{ added: SchoolStudent[]; alreadyInClass: string[] }>(`/schools/classes/${classId}/students`, { students }),
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
