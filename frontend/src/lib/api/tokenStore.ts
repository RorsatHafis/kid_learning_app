// Storage split follows AuthController's own documented session lifecycle
// (see its class javadoc): accessToken is a short-lived stateless JWT,
// refreshToken is a long-lived, server-tracked, single-use-then-rotated
// opaque value. Keeping the access token in memory only (not localStorage)
// limits what a script-injection could steal to "until next reload"; the
// refresh token has to survive reloads, so it goes in localStorage — an
// accepted trade-off for a first pass, worth revisiting (httpOnly cookie)
// before this handles anything higher-stakes than a learning app.

const REFRESH_TOKEN_KEY = "bk_refresh_token";
const REFRESH_EXPIRES_KEY = "bk_refresh_token_expires_at";

let accessToken: string | null = null;
let accessTokenExpiresAt: string | null = null;

export const tokenStore = {
  setSession(next: {
    accessToken: string | null;
    accessTokenExpiresAt: string | null;
    refreshToken: string | null;
    refreshTokenExpiresAt: string | null;
  }) {
    accessToken = next.accessToken;
    accessTokenExpiresAt = next.accessTokenExpiresAt;

    if (next.refreshToken) {
      localStorage.setItem(REFRESH_TOKEN_KEY, next.refreshToken);
      localStorage.setItem(REFRESH_EXPIRES_KEY, next.refreshTokenExpiresAt ?? "");
    } else {
      localStorage.removeItem(REFRESH_TOKEN_KEY);
      localStorage.removeItem(REFRESH_EXPIRES_KEY);
    }
  },

  clear() {
    accessToken = null;
    accessTokenExpiresAt = null;
    localStorage.removeItem(REFRESH_TOKEN_KEY);
    localStorage.removeItem(REFRESH_EXPIRES_KEY);
  },

  getAccessToken() {
    return accessToken;
  },

  getRefreshToken() {
    return localStorage.getItem(REFRESH_TOKEN_KEY);
  },

  /** True if we have a refresh token that isn't (as far as the client can
   * tell) already expired — used on app boot to decide whether it's worth
   * trying a silent refresh before showing the logged-out state. */
  hasUsableRefreshToken() {
    const token = localStorage.getItem(REFRESH_TOKEN_KEY);
    if (!token) return false;
    const expiresAt = localStorage.getItem(REFRESH_EXPIRES_KEY);
    if (!expiresAt) return true;
    return new Date(expiresAt).getTime() > Date.now();
  },
};

void accessTokenExpiresAt; // reserved for a future proactive-refresh timer
