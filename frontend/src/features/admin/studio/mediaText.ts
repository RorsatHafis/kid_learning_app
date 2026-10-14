import { ApiError } from "@/lib/api/ApiError";
import { messagesNow } from "@/i18n/catalog";
import { studioMessages } from "./messages";

/** The server's reason for refusing a file (its type, size or dimensions), in the author's words. */
export function uploadError(err: unknown): string {
  if (err instanceof ApiError && (err.status === 422 || err.status === 413) && err.problem?.detail) return err.problem.detail;
  return messagesNow(studioMessages).uploadFailed;
}
