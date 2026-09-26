import { ApiError, waitInWords } from "./ApiError";

/**
 * Words for a failed staff action: the server's own explanation for a refusal (409/404 —
 * written to be shown), the first field error, or how long to wait after a 429; anything
 * else gets `fallback` rather than a developer-oriented message.
 */
export function problemMessage(err: unknown, fallback: string): string {
  if (!(err instanceof ApiError)) return fallback;
  if (err.isRateLimited) {
    const seconds = err.problem?.retryAfterSeconds;
    return seconds ? `That was just sent. You can send it again in ${waitInWords(seconds)}.` : "Please wait a moment and try again.";
  }
  if (err.validationErrors) return Object.values(err.validationErrors)[0] ?? fallback;
  return err.status === 409 || err.status === 404 ? (err.problem?.detail ?? fallback) : fallback;
}
