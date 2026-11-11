import { apiClient } from "./client";

// ---- content/media/storage/SchoolStorageController.java (M11, D-06 owner decision 2026-11-11: hybrid storage) ----
// The access key is sent once, on connecting, and never comes back.

export interface StorageConnectionView {
  id: string;
  endpoint: string;
  region: string;
  bucket: string;
  pathStyle: boolean;
  /** The access key ID's last characters only. */
  accessKeyIdEnding: string;
  verifiedAt: string;
  lastCheckedAt: string | null;
  lastCheckOk: boolean | null;
  lastCheckError: string | null;
  files: number;
  bytes: number;
}

export interface SchoolStorage {
  schoolId: string;
  /** This installation can keep a school's access key (own storage can be connected). */
  available: boolean;
  /** Where new files go: null = Bookeyond-hosted. */
  current: StorageConnectionView | null;
  /** Storage connected before - still read for the files kept there. */
  earlier: StorageConnectionView[];
  hostedFiles: number;
  hostedBytes: number;
}

export interface ConnectStorage {
  endpoint: string;
  region: string;
  bucket: string;
  pathStyle: boolean;
  accessKeyId: string;
  secretAccessKey: string;
}

export const schoolStorageApi = {
  get: (schoolId: string) => apiClient.get<SchoolStorage>(`/schools/${schoolId}/storage`),
  connect: (schoolId: string, input: ConnectStorage) => apiClient.post<SchoolStorage>(`/schools/${schoolId}/storage/own`, input),
  useHosted: (schoolId: string) => apiClient.post<SchoolStorage>(`/schools/${schoolId}/storage/hosted`),
  check: (schoolId: string) => apiClient.post<SchoolStorage>(`/schools/${schoolId}/storage/check`),
};
