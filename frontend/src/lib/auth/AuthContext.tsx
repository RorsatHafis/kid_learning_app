import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { authApi } from "../api/endpoints";
import { SESSION_EXPIRED_EVENT, refreshSession } from "../api/client";
import { tokenStore } from "../api/tokenStore";
import type { PlatformRole } from "../api/types";

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
  login: (email: string, password: string) => Promise<Session>;
  register: (input: { email: string; password: string; familyName?: string; role?: "PARENT" | "PRINCIPAL" }) => Promise<Session | "PENDING_APPROVAL">;
  logout: () => Promise<void>;
}

const AuthContext = createContext<AuthContextValue | null>(null);

function toSession(auth: { accountId: string; email: string; platformRole: PlatformRole }): Session {
  return { accountId: auth.accountId, email: auth.email, platformRole: auth.platformRole };
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [session, setSession] = useState<Session | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const onSessionExpired = () => setSession(null);
    window.addEventListener(SESSION_EXPIRED_EVENT, onSessionExpired);
    return () => window.removeEventListener(SESSION_EXPIRED_EVENT, onSessionExpired);
  }, []);

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
      } else if (outcome.kind === "rejected") {
        tokenStore.clear();
      }
      // "unavailable" (offline / server error): keep the refresh token so a
      // later reload can still restore the session; show signed-out for now.
      setIsLoading(false);
    }
    void bootstrap();
    return () => {
      cancelled = true;
    };
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    const data = await authApi.login({ email, password });
    tokenStore.setSession({
      accessToken: data.accessToken,
      accessTokenExpiresAt: data.expiresAt,
      refreshToken: data.refreshToken,
      refreshTokenExpiresAt: data.refreshTokenExpiresAt,
    });
    const nextSession = toSession(data);
    setSession(nextSession);
    return nextSession;
  }, []);

  const register = useCallback(
    async (input: { email: string; password: string; familyName?: string; role?: "PARENT" | "PRINCIPAL" }) => {
      const data = await authApi.register(input);
      // PRINCIPAL self-registration comes back 202 with no tokens — pending
      // ADMIN approval, per AuthController. Nothing to log in to yet.
      if (!data.accessToken) return "PENDING_APPROVAL" as const;
      tokenStore.setSession({
        accessToken: data.accessToken,
        accessTokenExpiresAt: data.expiresAt,
        refreshToken: data.refreshToken,
        refreshTokenExpiresAt: data.refreshTokenExpiresAt,
      });
      const nextSession = toSession(data);
      setSession(nextSession);
      return nextSession;
    },
    [],
  );

  const logout = useCallback(async () => {
    const refreshToken = tokenStore.getRefreshToken();
    tokenStore.clear();
    setSession(null);
    if (refreshToken) {
      // Best-effort: session is cleared client-side regardless of whether
      // the server-side revoke call succeeds.
      await authApi.logout(refreshToken).catch(() => undefined);
    }
  }, []);

  return (
    <AuthContext.Provider value={{ session, isLoading, login, register, logout }}>{children}</AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
