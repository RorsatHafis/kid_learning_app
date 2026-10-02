import { ApiError } from "@/lib/api/ApiError";

/** The server's reason for refusing a file (its type, size or dimensions), in the author's words. */
export function uploadError(err: unknown): string {
  if (err instanceof ApiError && (err.status === 422 || err.status === 413) && err.problem?.detail) return err.problem.detail;
  return "The upload didn't work. Check your connection and try again.";
}
