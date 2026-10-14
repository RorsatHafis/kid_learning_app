import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { CheckCircle2 } from "lucide-react";
import { AuthLayout } from "./AuthLayout";
import { buttonClasses } from "@/components/ui/Button";
import { LoadingState } from "@/components/ui/States";
import { authApi } from "@/lib/api/endpoints";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { useAuth } from "@/lib/auth/AuthContext";
import { accountPath, roleHomePath } from "./roleHomePath";
import { forgetLinkToken, readLinkToken } from "./linkToken";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { authMessages } from "./messages";

type Outcome = { kind: "confirming" } | { kind: "confirmed" } | { kind: "unusable" } | { kind: "failed"; message: string };

// One request per token, however many times the effect runs (React StrictMode runs it
// twice in development): the link is single-use, and a second request would be refused.
const confirmations = new Map<string, Promise<void>>();
function confirmOnce(token: string) {
  let pending = confirmations.get(token);
  if (!pending) {
    pending = authApi.confirmEmail(token);
    confirmations.set(token, pending);
    pending.catch(() => confirmations.delete(token));
  }
  return pending;
}

/**
 * Opened from the confirmation email. Confirms the address straight away — the link
 * proves only that the email arrived — and works whether or not this browser is signed in.
 */
export function VerifyEmailPage() {
  const { session, isLoading } = useAuth();
  const m = useMessages(authMessages);
  const v = m.verify;
  const [token] = useState(() => readLinkToken());
  const [outcome, setOutcome] = useState<Outcome>(token ? { kind: "confirming" } : { kind: "unusable" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => forgetLinkToken(), []);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    setOutcome({ kind: "confirming" });
    confirmOnce(token).then(
      () => !cancelled && setOutcome({ kind: "confirmed" }),
      (err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 400) setOutcome({ kind: "unusable" });
        else if (err instanceof ApiError && err.isRateLimited) setOutcome({ kind: "failed", message: rateLimitedMessage(err) });
        else setOutcome({ kind: "failed", message: messagesNow(authMessages).unreachable });
      },
    );
    return () => {
      cancelled = true;
    };
  }, [token, attempt]);

  const continueLink = isLoading ? null : session ? (
    <Link
      to={roleHomePath(session.platformRole)}
      className={buttonClasses("solid", "lg", "w-full")}
    >
      {m.continue}
    </Link>
  ) : (
    <Link
      to="/login"
      className={buttonClasses("solid", "lg", "w-full")}
    >
      {m.logIn}
    </Link>
  );

  if (outcome.kind === "confirming") {
    return (
      <AuthLayout title={v.confirmingTitle} subtitle={m.oneMoment}>
        <LoadingState label={v.confirming} />
      </AuthLayout>
    );
  }

  if (outcome.kind === "confirmed") {
    return (
      <AuthLayout title={v.confirmedTitle} subtitle={v.confirmedSubtitle}>
        <div className="flex flex-col gap-4">
          <p role="status" className="flex items-start gap-2 rounded-[12px] bg-palm-soft p-3 text-sm font-semibold text-palm-deep">
            <CheckCircle2 size={18} className="mt-0.5 shrink-0" aria-hidden />
            {v.confirmed}
          </p>
          {continueLink}
        </div>
      </AuthLayout>
    );
  }

  if (outcome.kind === "failed") {
    return (
      <AuthLayout title={v.notYetTitle} subtitle={outcome.message}>
        <div className="flex flex-col gap-3">
          <button
            type="button"
            onClick={() => setAttempt((n) => n + 1)}
            className={buttonClasses("solid", "lg", "w-full")}
          >
            {m.tryAgain}
          </button>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title={m.linkUnusableTitle}
      subtitle={token ? m.linkUnusable : m.linkIncomplete}
    >
      <div className="flex flex-col gap-3 text-sm text-ink-soft">
        <p>
          {session ? v.freshSignedIn : v.freshSignedOut}
        </p>
        {session ? (
          <Link
            to={accountPath(session.platformRole) ?? roleHomePath(session.platformRole)}
            className={buttonClasses("solid", "lg", "w-full")}
          >
            {m.goToMyAccount}
          </Link>
        ) : (
          continueLink
        )}
      </div>
    </AuthLayout>
  );
}
