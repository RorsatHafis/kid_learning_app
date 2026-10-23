import { currentLocale } from "../../i18n/store";
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
  /** M5a: a file upload reports how much has been sent (0..1); sent over XHR, which fetch can't report. */
  onUploadProgress?: (fraction: number) => void;
};

/** refreshed: new tokens stored. rejected: the server refused the refresh
 * token (the session is over — sign in again). unavailable: the refresh
 * could not be completed (network failure, server error, rate limited); the
 * stored refresh token may still be valid, so it is kept. */
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
      headers: { "Content-Type": "application/json", "Accept-Language": currentLocale() },
      body: JSON.stringify({ refreshToken }),
    });
  } catch {
    return { kind: "unavailable" };
  }
  // 429: the per-network refresh budget ran out. The server refused before looking
  // at the token, so it was not rotated and stays valid — keep it and retry later.
  if (res.status >= 500 || res.status === 429) return { kind: "unavailable" };
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

/**
 * M8: the proof that the password was just re-entered on this sign-in, for actions that move money or access
 * (the server's RequiresStepUp). Memory only, a few minutes long; asked for by `onStepUpNeeded`'s handler.
 */
let stepUp: { token: string; until: number } | null = null;
let stepUpHandler: (() => Promise<boolean>) | null = null;

export function setStepUp(token: string, expiresAt: string) {
  stepUp = { token, until: Date.parse(expiresAt) };
}

export function clearStepUp() {
  stepUp = null;
}

/** Registers what asks the person for their password again; it resolves true once a new proof is set. */
export function onStepUpNeeded(handler: (() => Promise<boolean>) | null) {
  stepUpHandler = handler;
}

/**
 * M5a: the same request as fetch, over XMLHttpRequest so the upload's progress can be reported; answered as a
 * Response so refresh, step-up and errors are handled exactly as for every other request.
 */
function sendWithProgress(url: string, init: RequestInit, onProgress: (fraction: number) => void): Promise<Response> {
  return new Promise((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open(init.method ?? "GET", url);
    Object.entries((init.headers ?? {}) as Record<string, string>).forEach(([name, value]) => xhr.setRequestHeader(name, value));
    xhr.upload.onprogress = (e) => {
      if (e.lengthComputable && e.total > 0) onProgress(e.loaded / e.total);
    };
    xhr.onload = () => {
      const headers = new Headers();
      xhr
        .getAllResponseHeaders()
        .trim()
        .split(/[\r\n]+/)
        .forEach((line) => {
          const at = line.indexOf(":");
          if (at > 0) headers.append(line.slice(0, at).trim(), line.slice(at + 1).trim());
        });
      const empty = xhr.status === 204 || xhr.status === 205 || xhr.status === 304;
      resolve(new Response(empty ? null : xhr.responseText, { status: xhr.status, headers }));
    };
    xhr.onerror = () => reject(new TypeError("Network request failed"));
    xhr.onabort = () => reject(new DOMException("", "AbortError"));
    if (init.signal) {
      if (init.signal.aborted) return reject(new DOMException("", "AbortError"));
      init.signal.addEventListener("abort", () => xhr.abort(), { once: true });
    }
    xhr.send(init.body as XMLHttpRequestBodyInit | null);
  });
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { skipAuth, body, headers, onUploadProgress, ...rest } = options;

  const send = (url: string, init: RequestInit) =>
    onUploadProgress && body instanceof FormData ? sendWithProgress(url, init, onUploadProgress) : fetch(url, init);

  const doFetch = () =>
    send(`${API_BASE}${path}`, {
      ...rest,
      headers: {
        // A file upload sets its own multipart content type (with the boundary).
        ...(body instanceof FormData ? {} : { "Content-Type": "application/json" }),
        ...(skipAuth ? {} : { Authorization: `Bearer ${tokenStore.getAccessToken() ?? ""}` }),
        ...(!skipAuth && stepUp && stepUp.until > Date.now() ? { "X-Step-Up": stepUp.token } : {}),
        // The server answers (errors, field messages) in the language on screen - read per request,
        // so a request sent after switching is answered in the new language.
        "Accept-Language": currentLocale(),
        ...headers,
      },
      body: body instanceof FormData ? body : body !== undefined ? JSON.stringify(body) : undefined,
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

  // M8: the action needs the password again - ask once, then try again once with the new proof.
  if (res.status === 403 && !skipAuth && stepUpHandler) {
    const problem = await parseProblem(res.clone());
    if (problem?.stepUpRequired && (await stepUpHandler())) {
      res = await doFetch();
    }
  }

  if (!res.ok) {
    throw new ApiError(res.status, await parseProblem(res));
  }

  // 204, and 202 from endpoints that only acknowledge (password reset, resend confirmation), have no body.
  const text = await res.text();
  return (text ? JSON.parse(text) : undefined) as T;
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "POST", body }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "PUT", body }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "PATCH", body }),
  delete: <T>(path: string, options?: RequestOptions) => request<T>(path, { ...options, method: "DELETE" }),
};
