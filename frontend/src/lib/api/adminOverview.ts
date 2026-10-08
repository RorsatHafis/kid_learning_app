import { apiClient } from "./client";

// ---- admin/service/AdminOverviewService.java (X7) ----

export interface EndingPlan {
  familyId: string;
  familyName: string;
  parentEmail: string | null;
  planName: string;
  endsAt: string;
}

export interface AdminOverview {
  waiting: {
    principalsAwaitingApproval: number;
    suspendedAccounts: number;
    lockoutsLast24Hours: number;
    /** Soonest first, at most 20; `plansEndingSoonTotal` counts them all. */
    plansEndingSoon: EndingPlan[];
    plansEndingSoonTotal: number;
    /** Families waiting for an answer to a plan request (Y11). */
    planRequestsOpen: number;
  };
  platform: { families: number; children: number; schools: number; classes: number; teachers: number; principals: number };
  /** Items with an open draft, and curricula with a published version. */
  content: { questionsInDraft: number; activitiesInDraft: number; curriculaInDraft: number; curriculaPublished: number };
}

export const adminOverviewApi = {
  get: () => apiClient.get<AdminOverview>("/admin/overview"),
};
