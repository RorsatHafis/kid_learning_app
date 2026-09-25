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
}
