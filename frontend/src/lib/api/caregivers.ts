import { apiClient } from "./client";

/** M10b (child/caregiver/CaregiverController.java): see the child's progress, or also open the child's space. */
export type CaregiverScope = "VIEW_PROGRESS" | "OPEN_SPACE";

/** One caregiver of a child, as the family sees it. */
export interface Caregiver {
  id: string;
  email: string;
  scope: CaregiverScope;
  status: "INVITED" | "ACTIVE";
  invitedAt: string;
  acceptedAt: string | null;
}

/** One child the signed-in adult helps with, or is invited to. */
export interface CaredFor {
  id: string;
  childId: string;
  childName: string;
  scope: CaregiverScope;
  status: "INVITED" | "ACTIVE";
  invitedAt: string;
}

export const caregiversApi = {
  of: (childId: string) => apiClient.get<Caregiver[]>(`/children/${childId}/caregivers`),
  invite: (childId: string, email: string, scope: CaregiverScope) =>
    apiClient.post<Caregiver>(`/children/${childId}/caregivers`, { email, scope }),
  changeScope: (childId: string, caregiverId: string, scope: CaregiverScope) =>
    apiClient.put<Caregiver>(`/children/${childId}/caregivers/${caregiverId}`, { scope }),
  remove: (childId: string, caregiverId: string) => apiClient.delete<void>(`/children/${childId}/caregivers/${caregiverId}`),
  /** The caregiver's own side. */
  caring: () => apiClient.get<CaredFor[]>("/caregiving"),
  accept: (caregiverId: string) => apiClient.post<CaredFor>(`/caregiving/${caregiverId}/accept`),
  decline: (caregiverId: string) => apiClient.post<void>(`/caregiving/${caregiverId}/decline`),
  leave: (caregiverId: string) => apiClient.delete<void>(`/caregiving/${caregiverId}`),
};
