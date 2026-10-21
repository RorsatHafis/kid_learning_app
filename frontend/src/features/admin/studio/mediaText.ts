import { ApiError } from "@/lib/api/ApiError";
import { messagesNow } from "@/i18n/catalog";
import { studioMessages } from "./messages";

/**
 * The server's reason for refusing a file (its type, size or dimensions, or a full storage
 * allowance - nothing was stored), in the author's words.
 */
export function uploadError(err: unknown): string {
  if (err instanceof ApiError && (err.status === 422 || err.status === 413) && err.problem?.detail) return err.problem.detail;
  // 409 "Storage Limit Reached" (StorageLimitException) carries the allowance it would exceed.
  if (err instanceof ApiError && err.status === 409 && typeof err.problem?.limitBytes === "number" && err.problem.detail) {
    return err.problem.detail;
  }
  // A 413 without the server's own explanation came from a proxy in front of it (its request limit).
  if (err instanceof ApiError && err.status === 413) return messagesNow(studioMessages).uploadTooLarge;
  return messagesNow(studioMessages).uploadFailed;
}
