import type { ApiProblem } from "./types";
import { messagesNow } from "@/i18n/catalog";
import { apiMessages } from "./messages";

/**
 * Thrown by `client.ts` for every non-2xx response. Carries the backend's
 * RFC-7807 ProblemDetail body so callers can branch on `status` /
 * `problem.errors` instead of parsing prose. Never surface `problem.detail`
 * on a 500 directly to a child-facing screen — GlobalExceptionHandler
 * already keeps those generic ("An unexpected error occurred"), but a
 * parent/teacher screen showing it verbatim is fine.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly problem: ApiProblem | null;

  constructor(status: number, problem: ApiProblem | null) {
    super(problem?.detail ?? `Request failed with status ${status}`);
    this.name = "ApiError";
    this.status = status;
    this.problem = problem;
  }

  get isUnauthorized() {
    return this.status === 401;
  }

  get isForbidden() {
    return this.status === 403;
  }

  get validationErrors(): Record<string, string> | null {
    return this.problem?.errors ?? null;
  }

  /** 409 from a family's plan: adding this is beyond what the plan includes. */
  get isPlanLimit() {
    return this.status === 409 && typeof this.problem?.entitlement === "string";
  }

  /** 429: the server's per-network budget for sign-in, registration or refresh ran out. */
  get isRateLimited() {
    return this.status === 429;
  }
}

/** A server wait (Retry-After seconds) in words, rounded up for people: "45 seconds", "3 minutes". */
export function waitInWords(seconds: number): string {
  const m = messagesNow(apiMessages);
  return seconds < 60 ? m.seconds(seconds) : m.minutes(Math.ceil(seconds / 60));
}

/** "Too many attempts from this network" copy with the server's wait, rounded up for people. */
export function rateLimitedMessage(err: ApiError): string {
  const m = messagesNow(apiMessages);
  const seconds = err.problem?.retryAfterSeconds;
  if (!seconds) return m.tooManyWait;
  return m.tooManyIn(waitInWords(seconds));
}
