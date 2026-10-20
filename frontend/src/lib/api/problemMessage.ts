import { ApiError, waitInWords } from "./ApiError";
import { messagesNow } from "@/i18n/catalog";
import { apiMessages } from "./messages";

/**
 * Words for a failed staff action: the server's own explanation for a refusal (409/404 —
 * written to be shown), the first field error, or how long to wait after a 429; anything
 * else gets `fallback` rather than a developer-oriented message.
 */
export function problemMessage(err: unknown, fallback: string): string {
  if (!(err instanceof ApiError)) return fallback;
  if (err.isRateLimited) {
    const seconds = err.problem?.retryAfterSeconds;
    const m = messagesNow(apiMessages);
    return seconds ? m.justSent(waitInWords(seconds)) : m.waitAMoment;
  }
  if (err.validationErrors) return Object.values(err.validationErrors)[0] ?? fallback;
  // 402 (Y44): a school whose services wait for payment - the server says so in words meant for people.
  return err.status === 409 || err.status === 404 || err.status === 402 ? (err.problem?.detail ?? fallback) : fallback;
}
