import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { accountApi, authApi } from "../api/endpoints";
import { SESSION_EXPIRED_EVENT, refreshSession } from "../api/client";
import { tokenStore } from "../api/tokenStore";
import type { AuthResponse, PlatformRole } from "../api/types";
import { currentLocale, setAccountLocale } from "../../i18n/store";

interface Session {
  accountId: string;
  email: string;
  platformRole: PlatformRole;
}

interface AuthContextValue {
  session: Session | null;
  /** True while the boot-time silent refresh is in flight — render nothing
   * route-guard-sensitive until this settles, or a logged-in user briefly
   * flashes a logged-out screen on reload. */
  isLoading: boolean;
  /** The saved sign-in could not be checked (offline, server error, or the network's
   * refresh budget is spent) — it is kept, not discarded; `retrySession` tries again. */
  sessionUnavailable: boolean;
  retrySession: () => void;
  login: (email: string, password: string) => Promise<Session>;
  register: (input: { email: string; password: string; familyName?: string; role?: "PARENT" | "PRINCIPAL"; acceptedLegalVersion?: string }) => Promise<Session | "PENDING_CONFIRMATION">;
  logout: () => Promise<void>;
  /** Changes the password (current one required). The server ends every session and
   * returns a fresh one, which this adopts so this device stays signed in. */
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>;
  /** Ends every session on every device, this one included, then signs out here. */
  signOutEverywhere: () => Promise<void>;
  /** Re-reads the signed-in account (e.g. after its email changed) into the session. */
  refreshAccount: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function toSession(auth: { accountId: string; email: string; platformRole: PlatformRole }): Session {
  return { accountId: auth.accountId, email: auth.email, platformRole: auth.platformRole };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const onSessionExpired = () => {
      setSession(null);
      setAccountLocale(null);
    };
    window.addEventListener(SESSION_EXPIRED_EVENT, onSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onSessionExpired);
  }, []);

  const [sessionUnavailable, setSessionUnavailable] = useState(false);
  const [bootAttempt, setBootAttempt] = useState(0);

  useEffect(() => {
    // On boot we only have a persisted refresh token, never an access token
    // (that's memory-only, see tokenStore.ts), so re-establish the session
    // with a refresh. It MUST go through client.ts's refreshSession — the
    // same single-flight, cross-tab-locked path every 401 uses. A separate
    // direct call here used to race it (React StrictMode runs this effect
    // twice in development; several tabs boot at once), presenting one
    // single-use refresh token twice — which the backend correctly treats as
    // theft and answers by revoking the whole session.
    let cancelled = false;
    async function bootstrap() {
      if (!tokenStore.hasUsableRefreshToken()) {
        setIsLoading(false);
        return;
      }
      const outcome = await refreshSession();
      if (cancelled) return;
      if (outcome.kind === "refreshed") {
        setSession(toSession(outcome.auth));
        setAccountLocale(outcome.auth.preferredLanguage);
      } else if (outcome.kind === "rejected") {
        tokenStore.clear();
      }
      // "unavailable" (offline / server error / rate limited): keep the refresh
      // token and say so, rather than presenting a valid sign-in as signed out.
      setSessionUnavailable(outcome.kind === "unavailable");
      setIsLoading(false);
    }
    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, [bootAttempt]);

  const retrySession = useCallback(() => {
    setIsLoading(true);
    setBootAttempt((n) => n + 1);
  }, []);

  const adopt = useCallback((data: AuthResponse) => {
    tokenStore.setSession({
      accessToken: data.accessToken,
      accessTokenExpiresAt: data.expiresAt,
      refreshToken: data.refreshToken,
      refreshTokenExpiresAt: data.refreshTokenExpiresAt,
    });
    const nextSession = toSession(data);
    setSession(nextSession);
    setSessionUnavailable(false);
    // The account's own language - never the previous user's or this browser's.
    setAccountLocale(data.preferredLanguage);
    return nextSession;
  }, []);

  const login = useCallback(
    async (email: string, password: string) => adopt(await authApi.login({ email, password })),
    [adopt],
  );

  const changePassword = useCallback(
    async (currentPassword: string, newPassword: string) => {
      adopt(await accountApi.changePassword({ currentPassword, newPassword }));
    },
    [adopt],
  );

  const signOutEverywhere = useCallback(async () => {
    await accountApi.signOutEverywhere();
    tokenStore.clear();
    setSession(null);
    setAccountLocale(null);
  }, []);

  const refreshAccount = useCallback(async () => {
    const me = await accountApi.me();
    setSession((current) => (current && current.accountId === me.id ? { ...current, email: me.email } : current));
    setAccountLocale(me.preferredLanguage);
  }, []);

  const register = useCallback(
    async (input: { email: string; password: string; familyName?: string; role?: "PARENT" | "PRINCIPAL"; acceptedLegalVersion?: string }) => {
      // The language the visitor chose before signing up becomes the account's.
      const data = await authApi.register({ ...input, preferredLanguage: currentLocale() });
      // PRINCIPAL self-registration comes back 202 with no tokens: the account opens once
      // its address is confirmed (M4, AuthController). Nothing to log in to yet.
      if (!data.accessToken) return "PENDING_CONFIRMATION" as const;
      tokenStore.setSession({
        accessToken: data.accessToken,
        accessTokenExpiresAt: data.expiresAt,
        refreshToken: data.refreshToken,
        refreshTokenExpiresAt: data.refreshTokenExpiresAt,
      });
      const nextSession = toSession(data);
      setSession(nextSession);
      setAccountLocale(data.preferredLanguage);
      return nextSession;
    },
    [],
  );

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.getRefreshToken();
    tokenStore.clear();
    setSession(null);
    setAccountLocale(null);
    if (refreshToken) {
      // Best-effort: session is cleared client-side regardless of whether
      // the server-side revoke call succeeds.
      await authApi.logout(refreshToken).catch(() => undefined);
    }
  }, []);

  return (
    <AuthContext.Provider
      value={{
        session,
        isLoading,
        sessionUnavailable,
        retrySession,
        login,
        register,
        logout,
        changePassword,
        signOutEverywhere,
        refreshAccount,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
