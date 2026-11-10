/**
 * Creating an account, step 2 (owner requirement 2026-11-10): opened from the emailed link
 * (`/register/continue#token=…`), on any device. The link is used once and turned into a short-lived registration
 * grant; the account - with the details and password chosen here - is created only with that grant.
 */
import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { CheckCircle2 } from "lucide-react";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button, buttonClasses } from "@/components/ui/Button";
import { LoadingState } from "@/components/ui/States";
import { LegalAgreement } from "@/features/legal/Legal";
import { useLegal } from "@/features/legal/useLegal";
import { authApi } from "@/lib/api/endpoints";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { useAuth } from "@/lib/auth/AuthContext";
import { messagesNow, useMessages } from "@/i18n/catalog";
import { authMessages } from "./messages";
import { forgetLinkToken, readLinkToken } from "./linkToken";
import { roleHomePath } from "./roleHomePath";
import { RoleChoice } from "./RoleChoice";
import { rememberRegistrationRole, rememberedRegistrationRole, type RegistrationRole } from "./registrationRole";

type Grant = { registrationGrant: string; email: string; grantExpiresAt: string };
type Stage =
  | { kind: "checking" }
  | { kind: "ready"; grant: Grant }
  | { kind: "unusable"; incomplete: boolean }
  | { kind: "already" }
  | { kind: "expired" }
  | { kind: "failed"; message: string };

// The grant is kept for this tab only, so reloading the form doesn't lose a link that is already used up. It proves
// one address for 30 minutes and is cleared as soon as the account exists.
const GRANT_KEY = "bk.register.grant";

function storedGrant(): Grant | null {
  try {
    const raw = window.sessionStorage.getItem(GRANT_KEY);
    if (!raw) return null;
    const grant = JSON.parse(raw) as Grant;
    return new Date(grant.grantExpiresAt).getTime() > Date.now() ? grant : (forgetGrant(), null);
  } catch {
    return null;
  }
}

function keepGrant(grant: Grant) {
  try {
    window.sessionStorage.setItem(GRANT_KEY, JSON.stringify(grant));
  } catch {
    // unavailable: the form still works until the tab is reloaded
  }
}

function forgetGrant() {
  try {
    window.sessionStorage.removeItem(GRANT_KEY);
  } catch {
    // nothing kept
  }
}

// One request per token, however many times the effect runs (StrictMode runs it twice in development): the link is
// single use, and a second request would be refused.
const verifications = new Map<string, Promise<Grant>>();
function verifyOnce(token: string) {
  let pending = verifications.get(token);
  if (!pending) {
    pending = authApi.verifyRegistration(token);
    verifications.set(token, pending);
    pending.catch(() => verifications.delete(token));
  }
  return pending;
}

export function RegisterContinuePage() {
  const { register } = useAuth();
  const m = useMessages(authMessages);
  const r = m.register;
  const navigate = useNavigate();
  const legal = useLegal();

  const [token] = useState(() => readLinkToken());
  const [stage, setStage] = useState<Stage>(() => {
    if (token) return { kind: "checking" };
    const kept = storedGrant();
    return kept ? { kind: "ready", grant: kept } : { kind: "unusable", incomplete: true };
  });
  const [attempt, setAttempt] = useState(0);

  const [role, setRole] = useState<RegistrationRole>(() => rememberedRegistrationRole());
  const [password, setPassword] = useState("");
  const [familyName, setFamilyName] = useState("");
  const [agreed, setAgreed] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => forgetLinkToken(), []);

  useEffect(() => {
    if (!token) return;
    let cancelled = false;
    setStage({ kind: "checking" });
    verifyOnce(token).then(
      (grant) => {
        keepGrant(grant);
        if (!cancelled) setStage({ kind: "ready", grant });
      },
      (err) => {
        if (cancelled) return;
        // A reload after the link was used: the grant kept for this tab still finishes the form.
        const kept = storedGrant();
        if (kept && err instanceof ApiError && err.status === 400) setStage({ kind: "ready", grant: kept });
        else if (err instanceof ApiError && err.status === 409) setStage({ kind: "already" });
        else if (err instanceof ApiError && err.status === 400) setStage({ kind: "unusable", incomplete: false });
        else if (err instanceof ApiError && err.isRateLimited) setStage({ kind: "failed", message: rateLimitedMessage(err) });
        else setStage({ kind: "failed", message: messagesNow(authMessages).unreachable });
      },
    );
    return () => {
      cancelled = true;
    };
  }, [token, attempt]);

  function chooseRole(next: RegistrationRole) {
    setRole(next);
    rememberRegistrationRole(next);
  }

  async function onSubmit(e: FormEvent, grant: Grant) {
    e.preventDefault();
    setFormError(null);
    setFieldErrors({});
    const version = legal.data?.version ?? undefined;
    if (version && !agreed) {
      setFieldErrors({ acceptedLegalVersion: r.agreeFirst });
      return;
    }
    setIsSubmitting(true);
    try {
      const result = await register({
        email: grant.email,
        password,
        familyName: role === "PARENT" ? familyName.trim() || undefined : undefined,
        role,
        acceptedLegalVersion: version,
        registrationGrant: grant.registrationGrant,
      });
      forgetGrant();
      navigate(result === "PENDING_CONFIRMATION" ? "/login" : roleHomePath(result.platformRole), { replace: true });
    } catch (err) {
      if (err instanceof ApiError && err.isRegistrationNotVerified) {
        forgetGrant();
        setStage({ kind: "expired" });
      } else if (err instanceof ApiError && err.status === 409 && !err.isPlanLimit) {
        forgetGrant();
        setStage({ kind: "already" });
      } else if (err instanceof ApiError && err.validationErrors) {
        setFieldErrors(err.validationErrors);
      } else if (err instanceof ApiError && err.isRateLimited) {
        setFormError(rateLimitedMessage(err));
      } else if (err instanceof ApiError) {
        setFormError(err.problem?.detail ?? r.failed);
      } else {
        setFormError(m.somethingWentWrong);
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  const startAgain = (
    <Link to="/register" className={buttonClasses("solid", "lg", "w-full")}>
      {r.newLink}
    </Link>
  );

  if (stage.kind === "checking") {
    return (
      <AuthLayout title={r.finishTitle} subtitle={m.oneMoment}>
        <LoadingState label={r.checkingLink} />
      </AuthLayout>
    );
  }

  if (stage.kind === "failed") {
    return (
      <AuthLayout title={r.finishTitle} subtitle={stage.message}>
        <button type="button" onClick={() => setAttempt((n) => n + 1)} className={buttonClasses("solid", "lg", "w-full")}>
          {m.tryAgain}
        </button>
      </AuthLayout>
    );
  }

  if (stage.kind === "already") {
    return (
      <AuthLayout title={r.alreadyTitle} subtitle={r.alreadyBody}>
        <div className="flex flex-col gap-3">
          <Link to="/login" className={buttonClasses("solid", "lg", "w-full")}>
            {m.logIn}
          </Link>
          <Link to="/forgot-password" className={buttonClasses("outline", "lg", "w-full")}>
            {r.forgotPassword}
          </Link>
        </div>
      </AuthLayout>
    );
  }

  if (stage.kind === "expired") {
    return (
      <AuthLayout title={m.linkUnusableTitle} subtitle={r.confirmationExpired}>
        {startAgain}
      </AuthLayout>
    );
  }

  if (stage.kind === "unusable") {
    return (
      <AuthLayout title={m.linkUnusableTitle} subtitle={stage.incomplete ? m.linkIncomplete : m.linkUnusable}>
        {startAgain}
      </AuthLayout>
    );
  }

  const { grant } = stage;
  return (
    <AuthLayout title={r.finishTitle} subtitle={r.finishSubtitle}>
      <RoleChoice role={role} onChange={chooseRole} />
      <form onSubmit={(e) => void onSubmit(e, grant)} className="flex flex-col gap-4">
        <div className="rounded-[14px] bg-palm-soft p-3 ring-1 ring-inset ring-line">
          <p className="text-xs font-semibold text-ink-soft">{r.confirmedEmail}</p>
          <p className="mt-1 flex items-center gap-2 text-sm font-semibold text-ink">
            <CheckCircle2 size={16} className="shrink-0 text-palm-deep" aria-hidden />
            <span className="break-all">{grant.email}</span>
          </p>
        </div>
        {/* A principal names their school after signing in (M4): the server has nowhere to keep it before. */}
        {role === "PARENT" && (
          <FormField
            label={r.familyName}
            autoComplete="family-name"
            value={familyName}
            onChange={(e) => setFamilyName(e.target.value)}
            error={fieldErrors.familyName}
          />
        )}
        {/* The confirmed address, for password managers - it can't be changed here. */}
        <input type="email" name="email" autoComplete="username" value={grant.email} readOnly hidden />
        <FormField
          label={m.password}
          type="password"
          autoComplete="new-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
        />
        <LegalAgreement checked={agreed} onChange={setAgreed} error={fieldErrors.acceptedLegalVersion} />
        {formError && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {formError}
          </p>
        )}
        {role === "PRINCIPAL" && <p className="text-xs text-ink-soft">{r.principalNext}</p>}
        {/* What a family is asked for - true to docs/legal/DATA-INVENTORY.md; nothing about a child before registering. */}
        {role === "PARENT" && <p className="text-xs text-ink-soft">{r.parentNext}</p>}
        <Button type="submit" size="lg" disabled={isSubmitting} className="mt-2 w-full">
          {isSubmitting ? r.submitting : r.submit}
        </Button>
      </form>
    </AuthLayout>
  );
}
