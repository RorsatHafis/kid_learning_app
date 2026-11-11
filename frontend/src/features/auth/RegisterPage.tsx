/**
 * Creating an account, step 1 (owner requirement 2026-11-10): the email is confirmed BEFORE the account exists.
 * The visitor types an address and is emailed a single-use link; the account is created from that link
 * ({@link RegisterContinuePage}), on this device or any other. The answer here is the same whether or not the
 * address already has an account - that owner is emailed how to log in instead - so the page never says which.
 */
import { useEffect, useState, type FormEvent } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { MailCheck } from "lucide-react";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button } from "@/components/ui/Button";
import { authApi } from "@/lib/api/endpoints";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { currentLocale } from "@/i18n/store";
import { useMessages } from "@/i18n/catalog";
import { authMessages } from "./messages";
import { RoleChoice } from "./RoleChoice";
import { rememberRegistrationRole, rememberedRegistrationRole, type RegistrationRole } from "./registrationRole";
import { siteApi } from "@/lib/api/site";
import { useAsync } from "@/lib/hooks/useAsync";
import { useLocale } from "@/i18n/catalog";
import { ChosenPlanSummary } from "@/features/plans/ChosenPlan";
import { plansMessages } from "@/features/plans/plans.messages";

/** The server sends at most one email a minute per address (RegistrationVerificationService). */
const RESEND_GAP_SECONDS = 60;

export function RegisterPage() {
  const m = useMessages(authMessages);
  const r = m.register;

  // ?as=principal (the landing page's "Register your school") opens on the school application.
  const [params] = useSearchParams();
  const [role, setRole] = useState<RegistrationRole>(() =>
    params.get("as") === "principal" ? "PRINCIPAL" : params.get("as") === "parent" ? "PARENT" : rememberedRegistrationRole(),
  );
  // 2026-11-11: ?plan=CODE from the plans page. Shown from the public plan records; the server checks the code again
  // and keeps it with the request - nothing here sets a price.
  const p = useMessages(plansMessages);
  const locale = useLocale();
  const planCode = params.get("plan");
  const offered = useAsync(() => (planCode ? siteApi.plans() : Promise.resolve(null)), [planCode, locale]);
  const chosen = offered.data?.find((plan) => plan.code === planCode && plan.chooseable !== false) ?? null;
  const planGone = !!planCode && offered.data !== null && offered.data !== undefined && !chosen;
  useEffect(() => {
    if (chosen) setRole(chosen.audience === "SCHOOL" ? "PRINCIPAL" : "PARENT");
  }, [chosen]);
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [sentAgain, setSentAgain] = useState(false);
  const [wait, setWait] = useState(0);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => {
    if (wait <= 0) return;
    const timer = window.setTimeout(() => setWait((s) => s - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [wait]);

  function chooseRole(next: RegistrationRole) {
    setRole(next);
    rememberRegistrationRole(next);
  }

  async function send(address: string, again: boolean) {
    setFormError(null);
    setFieldError(undefined);
    setIsSubmitting(true);
    try {
      await authApi.startRegistration(address, currentLocale(), chosen?.code);
      rememberRegistrationRole(role);
      setSentTo(address);
      setSentAgain(again);
      setWait(RESEND_GAP_SECONDS);
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors?.email) {
        setFieldError(err.validationErrors.email);
      } else if (err instanceof ApiError && err.isRateLimited) {
        setFormError(rateLimitedMessage(err));
      } else if (err instanceof ApiError && err.status === 400 && chosen) {
        // The plan stopped being offered between the plans page and now.
        setFormError(p.planGone);
      } else {
        setFormError(r.sendFailed);
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    void send(email.trim(), false);
  }

  if (sentTo) {
    return (
      <AuthLayout title={r.sentTitle} subtitle={r.sentSubtitle}>
        <div className="flex flex-col gap-4 text-sm text-ink">
          <p role="status" className="flex items-start gap-3 rounded-[14px] bg-tonle-50 p-4 ring-1 ring-tonle-100">
            <MailCheck size={20} className="mt-0.5 shrink-0 text-palm-deep" aria-hidden />
            <span>
              {r.sentBefore} <strong className="break-all">{sentTo}</strong>
            </span>
          </p>
          <p className="leading-relaxed text-ink-soft">{r.sentNext}</p>
          <p className="leading-relaxed text-ink-soft">{r.noEmail}</p>
          {sentAgain && wait > 0 && (
            <p role="status" className="font-semibold text-palm-deep">
              {r.sentAgain}
            </p>
          )}
          {formError && (
            <p role="alert" className="font-medium text-brick-deep">
              {formError}
            </p>
          )}
          <div className="flex flex-col gap-3 sm:flex-row">
            <Button
              variant="outline"
              size="lg"
              disabled={wait > 0 || isSubmitting}
              onClick={() => void send(sentTo, true)}
              className="w-full sm:w-auto"
            >
              {isSubmitting ? r.sending : wait > 0 ? r.sendAgainIn(wait) : r.sendAgain}
            </Button>
            <Button
              variant="ghost"
              size="lg"
              onClick={() => {
                setSentTo(null);
                setFormError(null);
              }}
              className="w-full sm:w-auto"
            >
              {r.differentEmail}
            </Button>
          </div>
        </div>
        <p className="mt-6 text-center text-sm text-ink-soft">
          {r.haveAccount}{" "}
          <Link to="/login" className="font-semibold text-tonle-700">
            {m.logIn}
          </Link>
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={r.title} subtitle={role === "PRINCIPAL" ? r.principalSubtitle : r.subtitle}>
      {chosen ? (
        <div className="mb-5">
          <ChosenPlanSummary
            plan={{ ...chosen, billingInterval: chosen.billingInterval }}
            changeTo={chosen.audience === "SCHOOL" ? "/plans?for=school" : "/plans"}
          />
        </div>
      ) : (
        <>
          {planGone && (
            <p role="status" className="mb-4 rounded-[12px] bg-light-soft/60 px-4 py-3 text-sm text-ink ring-1 ring-light/40">
              {p.planGone}{" "}
              <Link to="/plans" className="font-semibold text-tonle-700 underline-offset-4 hover:underline">
                {p.seePlans}
              </Link>
            </p>
          )}
          <RoleChoice role={role} onChange={chooseRole} />
        </>
      )}
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField
          label={m.email}
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldError}
        />
        <p className="text-xs leading-relaxed text-ink-soft">{r.startHint}</p>
        {formError && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {formError}
          </p>
        )}
        <Button type="submit" size="lg" disabled={isSubmitting || !email.trim()} className="mt-2 w-full">
          {isSubmitting ? r.sending : r.sendLink}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-soft">
        {r.haveAccount}{" "}
        <Link to="/login" className="font-semibold text-tonle-700">
          {m.logIn}
        </Link>
      </p>
    </AuthLayout>
  );
}
