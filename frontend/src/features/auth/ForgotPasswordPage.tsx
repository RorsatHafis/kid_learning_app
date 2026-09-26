import { useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { MailCheck } from "lucide-react";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button } from "@/components/ui/Button";
import { authApi } from "@/lib/api/endpoints";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";

/**
 * Asks for a password-reset link. The server answers the same way whether or not the
 * address has an account, so this page does too: it never says "no account found".
 */
export function ForgotPasswordPage() {
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
        setFieldError("Enter the email address you signed up with.");
      } else if (err instanceof ApiError && err.isRateLimited) {
        setError(rateLimitedMessage(err));
      } else {
        setError("We couldn't send the email just now. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  if (sentTo) {
    return (
      <AuthLayout title="Check your email" subtitle="If there's an account for that address, a link is on its way.">
        <div role="status" className="flex flex-col gap-4 text-sm text-ink">
          <p className="flex items-start gap-3 rounded-2xl bg-surface-sand p-4">
            <MailCheck size={20} className="mt-0.5 shrink-0 text-brand-greenStrong" aria-hidden />
            <span>
              We sent a reset link to <strong className="break-all">{sentTo}</strong> if it belongs to a Bookeyond Kid
              account. The link works once, for 30 minutes.
            </span>
          </p>
          <p className="text-muted">
            Nothing arrived after a few minutes? Check your spam folder, or make sure you used the address you signed up
            with.
          </p>
          <Button variant="outline" onClick={() => setSentTo(null)} className="w-fit">
            Use a different address
          </Button>
        </div>
        <p className="mt-6 text-center text-sm text-muted">
          <Link to="/login" className="font-semibold text-brand-blueStrong">
            Back to log in
          </Link>
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Forgot your password?" subtitle="We'll email you a link to choose a new one.">
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldError}
        />
        {error && (
          <p role="alert" className="text-sm font-medium text-brand-redStrong">
            {error}
          </p>
        )}
        <Button type="submit" size="lg" disabled={isSubmitting || !email.trim()} className="mt-2 w-full">
          {isSubmitting ? "Sending…" : "Send reset link"}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        Remembered it?{" "}
        <Link to="/login" className="font-semibold text-brand-blueStrong">
          Log in
        </Link>
      </p>
    </AuthLayout>
  );
}
