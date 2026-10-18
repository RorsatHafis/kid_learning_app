import { apiClient } from "./client";

// ---- content/media/StorageController.java ----

/** Where an allowance comes from: the platform's own media, an administrator's exception, or a plan. */
export type StorageSource = "PLATFORM" | "OVERRIDE" | "SCHOOL_PLAN" | "DEFAULT_PLAN";
/** NEARLY_FULL from 80%; OVER = more kept than allowed after a change (nothing is removed). */
export type StorageState = "OK" | "NEARLY_FULL" | "FULL" | "OVER";

export interface StorageAllowance {
  /** Null = unlimited. */
  limitMb: number | null;
  limitBytes: number | null;
  source: StorageSource;
}

export interface StorageSlice {
  type: "IMAGE" | "AUDIO";
  archived: boolean;
  /** No question uses it. */
  unused: boolean;
  files: number;
  bytes: number;
}

export interface StorageView {
  usedBytes: number;
  allowance: StorageAllowance;
  /** Null when unlimited. */
  remainingBytes: number | null;
  state: StorageState;
  slices: StorageSlice[];
  largest: { id: string; title: string | null; type: "IMAGE" | "AUDIO"; sizeBytes: number; archived: boolean }[];
}

export interface StorageOwnerRow {
  accountId: string;
  email: string | null;
  role: "TEACHER" | "PRINCIPAL" | "ADMIN" | "PARENT" | null;
  files: number;
  usedBytes: number;
  allowance: StorageAllowance;
  state: StorageState;
}

export interface StorageOverview {
  /** What the files take on disk: each file once, however many authors keep it. */
  storedBytes: number;
  /** The platform's own media, counted against no one. */
  platformBytes: number;
  platformFiles: number;
  authorBytes: number;
  owners: StorageOwnerRow[];
}

export const storageApi = {
  /** The signed-in author's own storage (an administrator's is the platform's). */
  own: () => apiClient.get<StorageView>("/studio/storage"),
};

export const adminStorageApi = {
  overview: () => apiClient.get<StorageOverview>("/admin/storage"),
  of: (accountId: string) => apiClient.get<StorageView>(`/admin/accounts/${accountId}/storage`),
  /** `limitMb` null = unlimited; the note is kept in the audit history. */
  setAllowance: (accountId: string, limitMb: number | null, note: string) =>
    apiClient.put<StorageView>(`/admin/accounts/${accountId}/storage-allowance`, { limitMb, note }),
  clearAllowance: (accountId: string, note: string) =>
    apiClient.post<StorageView>(`/admin/accounts/${accountId}/storage-allowance/clear`, { note }),
};
