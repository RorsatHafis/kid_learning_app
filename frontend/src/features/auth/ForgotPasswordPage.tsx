import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { MailCheck } from "lucide-react";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button } from "@/components/ui/Button";
import { authApi } from "@/lib/api/endpoints";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { useMessages } from "@/i18n/catalog";
import { authMessages } from "./messages";

/**
 * Asks for a password-reset link. The server answers the same way whether or not the
 * address has an account, so this page does too: it never says "no account found".
 */
export function ForgotPasswordPage() {
  const m = useMessages(authMessages);
  const f = m.forgot;
  const [email, setEmail] = useState("");
  const [sentTo, setSentTo] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [fieldError, setFieldError] = useState<string | undefined>();
  const [isSubmitting, setIsSubmitting] = useState(false);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setFieldError(undefined);
    setIsSubmitting(true);
    try {
      await authApi.requestPasswordReset(email.trim());
      setSentTo(email.trim());
    } catch (err) {
      if (err instanceof ApiError && err.validationErrors?.email) {
        setFieldError(f.emailHint);
      } else if (err instanceof ApiError && err.isRateLimited) {
        setError(rateLimitedMessage(err));
      } else {
        setError(f.sendFailed);
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  if (sentTo) {
    return (
      <AuthLayout title={f.sentTitle} subtitle={f.sentSubtitle}>
        <div role="status" className="flex flex-col gap-4 text-sm text-ink">
          <p className="flex items-start gap-3 rounded-[14px] bg-tonle-50 p-4 ring-1 ring-tonle-100">
            <MailCheck size={20} className="mt-0.5 shrink-0 text-palm-deep" aria-hidden />
            <span>
              {f.sentBefore} <strong className="break-all">{sentTo}</strong> {f.sentAfter}
            </span>
          </p>
          <p className="text-ink-soft">{f.nothingArrived}</p>
          <Button variant="outline" onClick={() => setSentTo(null)} className="w-fit">
            {f.differentAddress}
          </Button>
        </div>
        <p className="mt-6 text-center text-sm text-ink-soft">
          <Link to="/login" className="font-semibold text-tonle-700">
            {m.backToLogIn}
          </Link>
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={f.title} subtitle={f.subtitle}>
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
        {error && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {error}
          </p>
        )}
        <Button type="submit" size="lg" disabled={isSubmitting || !email.trim()} className="mt-2 w-full">
          {isSubmitting ? f.submitting : f.submit}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-soft">
        {f.remembered}{" "}
        <Link to="/login" className="font-semibold text-tonle-700">
          {m.logIn}
        </Link>
      </p>
    </AuthLayout>
  );
}
