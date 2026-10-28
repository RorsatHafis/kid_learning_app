import { apiClient } from "./client";

// ---- school/web/AcademicYearController.java (M12 P1) ----

export interface AcademicTerm {
  id: string;
  name: string;
  /** ISO dates (yyyy-mm-dd), as the school set them. */
  startsOn: string;
  endsOn: string;
}

export interface AcademicYear {
  id: string;
  name: string;
  startsOn: string;
  endsOn: string;
  current: boolean;
  archived: boolean;
  terms: AcademicTerm[];
}

export interface DatedName {
  name: string;
  startsOn: string;
  endsOn: string;
}

const years = (schoolId: string) => `/schools/${schoolId}/academic-years`;

export const academicYearsApi = {
  list: (schoolId: string) => apiClient.get<AcademicYear[]>(years(schoolId)),
  create: (schoolId: string, body: DatedName) => apiClient.post<AcademicYear>(years(schoolId), body),
  change: (schoolId: string, yearId: string, body: DatedName) => apiClient.put<AcademicYear>(`${years(schoolId)}/${yearId}`, body),
  makeCurrent: (schoolId: string, yearId: string) => apiClient.post<AcademicYear>(`${years(schoolId)}/${yearId}/current`),
  archive: (schoolId: string, yearId: string) => apiClient.post<AcademicYear>(`${years(schoolId)}/${yearId}/archive`),
  restore: (schoolId: string, yearId: string) => apiClient.post<AcademicYear>(`${years(schoolId)}/${yearId}/restore`),
  addTerm: (schoolId: string, yearId: string, body: DatedName) =>
    apiClient.post<AcademicTerm>(`${years(schoolId)}/${yearId}/terms`, body),
  changeTerm: (schoolId: string, yearId: string, termId: string, body: DatedName) =>
    apiClient.put<AcademicTerm>(`${years(schoolId)}/${yearId}/terms/${termId}`, body),
  removeTerm: (schoolId: string, yearId: string, termId: string) =>
    apiClient.delete<void>(`${years(schoolId)}/${yearId}/terms/${termId}`),
  placeClass: (schoolId: string, classId: string, academicYearId: string | null) =>
    apiClient.put<{ classId: string; academicYearId: string | null }>(`/schools/${schoolId}/classes/${classId}/academic-year`, {
      academicYearId,
    }),
};
