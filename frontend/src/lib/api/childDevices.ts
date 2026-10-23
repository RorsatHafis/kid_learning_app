import { apiClient } from "./client";
import type { ChildDeviceCode, ChildDeviceCodeState, ChildDeviceCollected } from "./types";

/**
 * M10a (child/device/ChildDeviceController.java): a parent opens one child's learning space on another device. The
 * parent makes a code and approves the waiting device; the device enters the code and collects a sign-in that opens
 * only that child's space.
 */
export const childDevicesApi = {
  create: (childId: string) => apiClient.post<ChildDeviceCode>(`/children/${childId}/device-codes`),
  state: (childId: string, codeId: string) => apiClient.get<ChildDeviceCodeState>(`/children/${childId}/device-codes/${codeId}`),
  approve: (childId: string, codeId: string) =>
    apiClient.post<ChildDeviceCodeState>(`/children/${childId}/device-codes/${codeId}/approve`),
  cancel: (childId: string, codeId: string) => apiClient.post<ChildDeviceCodeState>(`/children/${childId}/device-codes/${codeId}/cancel`),
  /** On the child's device, before it has any sign-in. */
  claim: (code: string) => apiClient.post<{ claimId: string; secret: string }>("/auth/child-device/claim", { code }),
  collect: (claimId: string, secret: string) => apiClient.post<ChildDeviceCollected>("/auth/child-device/collect", { claimId, secret }),
};
