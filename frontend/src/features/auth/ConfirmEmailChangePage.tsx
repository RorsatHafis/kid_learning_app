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

type Outcome =
  { kind: "confirming" } | { kind: "changed" } | { kind: "unusable" } | { kind: "taken" } | { kind: "failed"; message: string };

// One request per token, however many times the effect runs: the link is single-use.
const confirmations = new Map<string, Promise<void>>();
function confirmOnce(token: string) {
  let pending = confirmations.get(token);
  if (!pending) {
    pending = authApi.confirmEmailChange(token);
    confirmations.set(token, pending);
    pending.catch(() => confirmations.delete(token));
  }
  return pending;
}

/**
 * Opened from the email sent to a new sign-in address. Opening it is the proof that the
 * address works, so the move happens straight away, signed in or not.
 */
export function ConfirmEmailChangePage() {
  const { session, isLoading, refreshAccount } = useAuth();
  const [token] = useState(() => readLinkToken());
  const [outcome, setOutcome] = useState<Outcome>(token ? { kind: "confirming" } : { kind: "unusable" });
  const [attempt, setAttempt] = useState(0);

  useEffect(() => forgetLinkToken(), []);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    setOutcome({ kind: "confirming" });
    confirmOnce(token).then(
      () => !cancelled && setOutcome({ kind: "changed" }),
      (err) => {
        if (cancelled) return;
        if (err instanceof ApiError && err.status === 400) setOutcome({ kind: "unusable" });
        else if (err instanceof ApiError && err.status === 409) setOutcome({ kind: "taken" });
        else if (err instanceof ApiError && err.isRateLimited) setOutcome({ kind: "failed", message: rateLimitedMessage(err) });
        else setOutcome({ kind: "failed", message: "We couldn't reach Bookeyond Kid just now." });
      },
    );
    return () => {
      cancelled = true;
    };
  }, [token, attempt]);

  // A browser signed in to the account shows the new address everywhere straight away.
  useEffect(() => {
    if (outcome.kind === "changed" && session) void refreshAccount().catch(() => undefined);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [outcome.kind, session?.accountId]);

  const accountLink = isLoading ? null : session ? (
    <Link
      to={accountPath(session.platformRole) ?? roleHomePath(session.platformRole)}
      className={buttonClasses("solid", "lg", "w-full")}
    >
      Go to my account
    </Link>
  ) : (
    <Link to="/login" className={buttonClasses("solid", "lg", "w-full")}>
      Log in
    </Link>
  );

  if (outcome.kind === "confirming") {
    return (
      <AuthLayout title="Changing your email" subtitle="This only takes a moment.">
        <LoadingState label="Changing your sign-in email…" />
      </AuthLayout>
    );
  }

  if (outcome.kind === "changed") {
    return (
      <AuthLayout title="Email changed" subtitle="From now on you sign in with this address, and our emails come here.">
        <div className="flex flex-col gap-4">
          <p
            role="status"
            className="flex items-start gap-2 rounded-[12px] bg-palm-soft p-3 text-sm font-semibold text-palm-deep"
          >
            <CheckCircle2 size={18} className="mt-0.5 shrink-0" aria-hidden />
            Your sign-in email has been changed. We've let your old address know.
          </p>
          {accountLink}
        </div>
      </AuthLayout>
    );
  }

  if (outcome.kind === "taken") {
    return (
      <AuthLayout title="Email not changed" subtitle="This address now belongs to another Bookeyond Kid account.">
        <div className="flex flex-col gap-3 text-sm text-ink-soft">
          <p>You still sign in with your current address. You can choose a different one from your account page.</p>
          {accountLink}
        </div>
      </AuthLayout>
    );
  }

  if (outcome.kind === "failed") {
    return (
      <AuthLayout title="Email not changed yet" subtitle={outcome.message}>
        <button type="button" onClick={() => setAttempt((n) => n + 1)} className={buttonClasses("solid", "lg", "w-full")}>
          Try again
        </button>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout
      title="This link can't be used"
      subtitle={
        token
          ? "It may have expired, already been used, or been replaced by a newer link."
          : "The link is incomplete. Open it straight from the email."
      }
    >
      <div className="flex flex-col gap-3 text-sm text-ink-soft">
        <p>Your sign-in email hasn't changed. You can ask for a new link from your account page.</p>
        {accountLink}
      </div>
    </AuthLayout>
  );
}
