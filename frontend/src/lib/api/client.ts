import { ApiError } from "./ApiError";
import { tokenStore } from "./tokenStore";
import type { ApiProblem, AuthResponse } from "./types";

const API_BASE = "/api/v1";

/** Fired when a refresh attempt fails outright (per AuthController: an
 * invalid/expired/reused/revoked refresh token always means "sign in
 * again", never a soft/retryable error). AuthProvider listens for this to
 * clear its state and redirect — kept as an event rather than a direct
 * import so this module never depends on React. */
export const SESSION_EXPIRED_EVENT = "bk:session-expired";

type RequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
  /** Skip attaching Authorization / skip the refresh-retry loop — only
   * auth endpoints themselves need this. */
  skipAuth?: boolean;
};

/** refreshed: new tokens stored. rejected: the server refused the refresh
 * token (the session is over — sign in again). unavailable: the refresh
 * could not be completed (network failure, server error); the stored refresh
 * token may still be valid, so it is kept. */
export type RefreshOutcome =
  | { kind: "refreshed"; auth: AuthResponse }
  | { kind: "rejected" }
  | { kind: "unavailable" };

const REFRESH_LOCK = "bk-auth-refresh";

let refreshInFlight: Promise<RefreshOutcome> | null = null;

/**
 * The ONE way this app refreshes its session — boot-time and on-401 alike.
 *
 * The backend's refresh tokens are single-use: presenting one that was
 * already rotated is treated as theft and revokes the whole session family
 * (AccountSessionService). So the client must never send the same refresh
 * token twice. Concurrent callers in this tab share one in-flight refresh;
 * across tabs (which share the token via localStorage) a Web Lock makes
 * refreshes take turns, and the token is read INSIDE the lock so a tab that
 * waited uses the token the previous tab rotated to, not the stale one.
 */
export function refreshSession(): Promise<RefreshOutcome> {
  if (!refreshInFlight) {
    refreshInFlight = withCrossTabLock(performRefresh).finally(() => {
      refreshInFlight = null;
    });
  }
  return refreshInFlight;
}

async function withCrossTabLock<T>(fn: () => Promise<T>): Promise<T> {
  // Web Locks exist in every current browser on secure origins (https,
  // localhost). Without them only in-tab coalescing applies.
  const locks = typeof navigator !== "undefined" ? navigator.locks : undefined;
  return locks ? await locks.request(REFRESH_LOCK, fn) : fn();
}

async function performRefresh(): Promise<RefreshOutcome> {
  const refreshToken = tokenStore.getRefreshToken();
  if (!refreshToken) return { kind: "rejected" };

  let res: Response;
  try {
    res = await fetch(`${API_BASE}/auth/refresh`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    return { kind: "unavailable" };
  }
  if (res.status >= 500) return { kind: "unavailable" };
  if (!res.ok) return { kind: "rejected" };

  const data: AuthResponse = await res.json();
  tokenStore.setSession({
    accessToken: data.accessToken,
    accessTokenExpiresAt: data.expiresAt,
    refreshToken: data.refreshToken,
    refreshTokenExpiresAt: data.refreshTokenExpiresAt,
  });
  return { kind: "refreshed", auth: data };
}

async function parseProblem(res: Response): Promise<ApiProblem | null> {
  try {
    return (await res.json()) as ApiProblem;
  } catch {
    return null;
  }
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { skipAuth, body, headers, ...rest } = options;

  const doFetch = () =>
    fetch(`${API_BASE}${path}`, {
      ...rest,
      headers: {
        "Content-Type": "application/json",
        ...(skipAuth ? {} : { Authorization: `Bearer ${tokenStore.getAccessToken() ?? ""}` }),
        ...headers,
      },
      body: body !== undefined ? JSON.stringify(body) : undefined,
    });

  let res = await doFetch();

  // 401 = not authenticated (missing/expired/invalid access token): refresh
  // once and retry once. 403 = authenticated but not permitted: never a
  // reason to refresh. A retried request that is 401 again is returned as-is,
  // so this can never loop.
  if (res.status === 401 && !skipAuth) {
    const outcome = await refreshSession();
    if (outcome.kind === "refreshed") {
      res = await doFetch();
    } else if (outcome.kind === "rejected") {
      tokenStore.clear();
      window.dispatchEvent(new Event(SESSION_EXPIRED_EVENT));
    }
    // "unavailable": keep the refresh token; this request fails, the next one retries.
  }

  if (!res.ok) {
    throw new ApiError(res.status, await parseProblem(res));
  }

  if (res.status === 204) return undefined as T;
  return (await res.json()) as T;
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "POST", body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "PUT", body }),
};
