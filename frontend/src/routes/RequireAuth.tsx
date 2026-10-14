import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router-dom";
import { WifiOff } from "lucide-react";
import { useAuth } from "@/lib/auth/AuthContext";
import { Button } from "@/components/ui/Button";
import { useMessages } from "@/i18n/catalog";
import { common } from "@/components/ui/messages";

export function RequireAuth({ children }: { children: ReactNode }) {
  const { session, isLoading, sessionUnavailable, retrySession } = useAuth();
  const location = useLocation();

  // Don't redirect while the boot-time silent refresh is still resolving —
  // see AuthProvider's isLoading doc comment.
  if (isLoading) return null;

  if (!session && sessionUnavailable) {
    return <SessionUnavailable onRetry={retrySession} />;
  }

  if (!session) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }

  return <>{children}</>;
}

/** The saved sign-in exists but could not be checked just now. Never shown as "signed out". */
function SessionUnavailable({ onRetry }: { onRetry: () => void }) {
  const m = useMessages(common);
  return (
    <main className="flex min-h-screen items-center justify-center bg-surface-sand px-4">
      <div
        role="alert"
        className="flex max-w-md flex-col items-center gap-4 rounded-3xl border-[1.5px] border-hairline bg-white p-8 text-center shadow-soft"
      >
        <div className="flex h-12 w-12 items-center justify-center rounded-full bg-brand-redTint text-brand-red">
          <WifiOff size={22} aria-hidden />
        </div>
        <h1 className="text-lg font-bold text-ink">{m.unreachableTitle}</h1>
        <p className="text-sm text-muted">{m.unreachableBody}</p>
        <Button size="md" onClick={onRetry}>
          {m.tryAgain}
        </Button>
      </div>
    </main>
  );
}
