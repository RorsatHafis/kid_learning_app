import { apiClient } from "./client";
import type { ActivityVersionDetail, CorrectAnswer } from "./types";

// ---- site/SiteController.java, content/demo/PublicDemoController.java (M2) ----

export type StatementKind = "BRAND" | "TESTIMONIAL";

/** A statement as the landing page shows it (no source note). */
export interface PublicStatement {
  id: string;
  kind: StatementKind;
  textEn: string;
  textKm: string;
  attributionEn: string | null;
  attributionKm: string | null;
}

/** As administrators manage it: the source note of a testimonial's permission, order, visibility, version. */
export interface Statement extends PublicStatement {
  sourceNote: string | null;
  position: number;
  visible: boolean;
  version: number;
  updatedAt: string;
}

export interface StatementDraft {
  kind: StatementKind;
  textEn: string;
  textKm: string;
  attributionEn?: string | null;
  attributionKm?: string | null;
  sourceNote?: string | null;
  position: number;
  visible: boolean;
  /** A testimonial: the administrator confirms the words are genuine and permission is recorded. */
  confirmedGenuine?: boolean;
  version?: number;
}

/** The server's verdict on a demo answer. Nothing is stored. */
export interface DemoAnswer {
  correct: boolean;
  correctAnswer: CorrectAnswer | null;
  explanation: string | null;
}

export interface DemoCandidate {
  activityVersionId: string;
  title: string;
  versionNumber: number;
  chosen: boolean;
}

/** F1a: an administrator's words for one entry of the public pages' catalogs (a null language keeps the default). */
export interface SiteCopy {
  namespace: string;
  key: string;
  textEn: string | null;
  textKm: string | null;
  version: number;
  updatedAt: string;
}

const copyQuery = (namespace: string, key: string) =>
  `namespace=${encodeURIComponent(namespace)}&key=${encodeURIComponent(key)}`;

export const siteApi = {
  statements: () => apiClient.get<PublicStatement[]>("/site/statements", { skipAuth: true }),
  copy: () => apiClient.get<Omit<SiteCopy, "version" | "updatedAt">[]>("/site/copy", { skipAuth: true }),
  demoActivities: () => apiClient.get<ActivityVersionDetail[]>("/demo/activities", { skipAuth: true }),
  checkDemo: (activityVersionId: string, questionVersionId: string, submittedAnswer: string) =>
    apiClient.post<DemoAnswer>("/demo/check", { activityVersionId, questionVersionId, submittedAnswer }, { skipAuth: true }),
};

export const siteAdminApi = {
  statements: () => apiClient.get<Statement[]>("/admin/site-statements"),
  create: (draft: StatementDraft) => apiClient.post<Statement>("/admin/site-statements", draft),
  update: (id: string, draft: StatementDraft) => apiClient.put<Statement>(`/admin/site-statements/${id}`, draft),
  remove: (id: string) => apiClient.delete<void>(`/admin/site-statements/${id}`),
  /** The chosen lessons first, then up to 12 whose title contains `q`. */
  demoCandidates: (q = "") => apiClient.get<DemoCandidate[]>(`/admin/demo-activities${q.trim() ? `?q=${encodeURIComponent(q.trim())}` : ""}`),
  chooseDemo: (activityVersionIds: string[]) => apiClient.put<string[]>("/admin/demo-activities", { activityVersionIds }),
  copy: () => apiClient.get<SiteCopy[]>("/admin/site-copy"),
  putCopy: (namespace: string, key: string, body: { textEn: string | null; textKm: string | null; version: number | null }) =>
    apiClient.put<SiteCopy>(`/admin/site-copy?${copyQuery(namespace, key)}`, body),
  resetCopy: (namespace: string, key: string) => apiClient.delete<void>(`/admin/site-copy?${copyQuery(namespace, key)}`),
};
