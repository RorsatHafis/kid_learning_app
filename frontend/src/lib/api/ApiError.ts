import type { ApiProblem } from "./types";

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

  /** 429: the server's per-network budget for sign-in, registration or refresh ran out. */
  get isRateLimited() {
    return this.status === 429;
  }
}

/** "Too many attempts from this network" copy with the server's wait, rounded up for people. */
export function rateLimitedMessage(err: ApiError): string {
  const seconds = err.problem?.retryAfterSeconds;
  if (!seconds) return "Too many attempts from this network. Please wait a moment and try again.";
  const wait = seconds < 60 ? `${seconds} second${seconds === 1 ? "" : "s"}` : `${Math.ceil(seconds / 60)} minute${seconds <= 60 ? "" : "s"}`;
  return `Too many attempts from this network. Please try again in ${wait}.`;
}
