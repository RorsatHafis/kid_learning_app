/**
 * Creating a family account: email, password (with the policy explained) and agreement to the current legal
 * documents once they are published.
 */
import { useState, type FormEvent } from "react";
import { LegalAgreement } from "@/features/legal/Legal";
import { useLegal } from "@/features/legal/useLegal";
import { Link, useNavigate, useSearchParams } from "react-router-dom";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button } from "@/components/ui/Button";
import { cn } from "@/lib/utils/cn";
import { useAuth } from "@/lib/auth/AuthContext";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { roleHomePath } from "./roleHomePath";
import { useMessages } from "@/i18n/catalog";
import { authMessages } from "./messages";

export function RegisterPage() {
  const { register } = useAuth();
  const m = useMessages(authMessages);
  const r = m.register;
  const navigate = useNavigate();

  // ?as=principal (the landing page's "Register your school") opens on the school application.
  const [params] = useSearchParams();
  const [role, setRole] = useState<"PARENT" | "PRINCIPAL">(params.get("as") === "principal" ? "PRINCIPAL" : "PARENT");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [familyName, setFamilyName] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [pendingApproval, setPendingApproval] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const legal = useLegal();
  const [agreed, setAgreed] = useState(false);

  async function onSubmit(e: FormEvent) {
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
      const result = await register({ email, password, familyName: familyName || undefined, role, acceptedLegalVersion: version });
      if (result === "PENDING_APPROVAL") {
        setPendingApproval(true);
      } else {
        navigate(roleHomePath(result.platformRole), { replace: true });
      }
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors) {
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

  if (pendingApproval) {
    return (
      <AuthLayout title={r.pendingTitle} subtitle={r.pendingSubtitle}>
        <p className="text-center text-sm leading-relaxed text-ink-soft">
          {r.pendingBody} <span className="font-semibold text-ink">{email}</span>
        </p>
        <Link to="/" className="mt-6 block text-center text-sm font-semibold text-tonle-700">
          {r.backHome}
        </Link>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={r.title} subtitle={r.subtitle}>
      <div role="group" aria-label={r.accountType} className="mb-7 grid grid-cols-2 gap-1 rounded-[14px] bg-tonle-50 p-1 ring-1 ring-inset ring-line">
        <button
          type="button"
          aria-pressed={role === "PARENT"}
          onClick={() => setRole("PARENT")}
          className={cn("h-10 rounded-[10px] text-sm font-semibold transition-[background-color,color,box-shadow] duration-150", role === "PARENT" ? "bg-white text-tonle-900 shadow-tight ring-1 ring-line" : "text-ink-soft hover:text-tonle-900")}
        >
          {r.parent}
        </button>
        <button
          type="button"
          aria-pressed={role === "PRINCIPAL"}
          onClick={() => setRole("PRINCIPAL")}
          className={cn("h-10 rounded-[10px] text-sm font-semibold transition-[background-color,color,box-shadow] duration-150", role === "PRINCIPAL" ? "bg-white text-tonle-900 shadow-tight ring-1 ring-line" : "text-ink-soft hover:text-tonle-900")}
        >
          {r.principal}
        </button>
      </div>

      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField
          label={role === "PARENT" ? r.familyName : r.schoolName}
          value={familyName}
          onChange={(e) => setFamilyName(e.target.value)}
          error={fieldErrors.familyName}
        />
        <FormField label={m.email} type="email" required value={email} onChange={(e) => setEmail(e.target.value)} error={fieldErrors.email} />
        <FormField
          label={m.password}
          type="password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
        />
        <LegalAgreement checked={agreed} onChange={setAgreed} error={fieldErrors.acceptedLegalVersion} />
        {formError && <p className="text-sm font-medium text-brick-deep">{formError}</p>}
        {role === "PRINCIPAL" && (
          <p className="text-xs text-ink-soft">{r.principalReview}</p>
        )}
        <Button type="submit" size="lg" disabled={isSubmitting} className="mt-2 w-full">
          {isSubmitting ? r.submitting : r.submit}
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
