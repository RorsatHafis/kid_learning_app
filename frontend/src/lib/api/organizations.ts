import { apiClient } from "./client";

// ---- school/web/SchoolOrganizationController.java (M12 B0) ----

export interface SchoolBranch {
  id: string;
  name: string;
  principalAccountId: string;
  archived: boolean;
}

export interface SchoolOrganization {
  id: string;
  name: string;
  /** The account owns the organization (sees every branch, can add one); false: it runs a branch of it. */
  owner: boolean;
  branches: SchoolBranch[];
}

/** M12 B2: one branch's size and payment state (school/service/OrganizationOverviewService.java). Counts only. */
export interface BranchFigures {
  schoolId: string;
  name: string;
  archived: boolean;
  runByOwner: boolean;
  classes: number;
  teachers: number;
  children: number;
  paid: boolean;
  paidUntil: string | null;
  paymentPending: boolean;
  currentYear: string | null;
}

/** Totals over branches in use; a child or teacher in two branches is counted once. */
export interface OrganizationOverview {
  organizationId: string;
  name: string;
  branchesInUse: number;
  branchesPaid: number;
  children: number;
  teachers: number;
  classes: number;
  branches: BranchFigures[];
}

export const organizationsApi = {
  mine: () => apiClient.get<SchoolOrganization[]>("/organizations/mine"),
  /** M12 B2: the owner's overview of every branch (404 for anyone else). */
  overview: (organizationId: string) => apiClient.get<OrganizationOverview>(`/organizations/${organizationId}/overview`),
  /** M12 B1: the owner appoints a branch's principal (an existing principal account). */
  assignPrincipal: (organizationId: string, schoolId: string, email: string) =>
    apiClient.put<SchoolBranch>(`/organizations/${organizationId}/branches/${schoolId}/principal`, { email }),
  addBranch: (organizationId: string, name: string) => apiClient.post<SchoolBranch>(`/organizations/${organizationId}/branches`, { name }),
  /** A sibling branch's calendar (years and terms) into this branch; years already here by name are skipped. */
  copyCalendar: (schoolId: string, sourceSchoolId: string) =>
    apiClient.post<{ yearsAdded: string[]; yearsAlreadyThere: string[] }>(`/schools/${schoolId}/academic-years/copy-from/${sourceSchoolId}`),
};
