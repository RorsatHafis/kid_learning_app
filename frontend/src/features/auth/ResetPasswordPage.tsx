import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button } from "@/components/ui/Button";
import { authApi } from "@/lib/api/endpoints";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { useAuth } from "@/lib/auth/AuthContext";
import { forgetLinkToken, readLinkToken } from "./linkToken";

const MIN_PASSWORD = 12; // identity/service/PasswordPolicy.MIN_LENGTH

/**
 * Chooses a new password with the link from a reset email. The server ends every
 * session on success and signs nobody in, so this page sends the person to log in.
 * A link that can't be used (expired, used, replaced by a newer one) gets one honest
 * message and a way to ask for another.
 */
export function ResetPasswordPage() {
  const navigate = useNavigate();
  const { session, logout } = useAuth();
  // Read during render (pure, safe to repeat); the fragment is dropped afterwards.
  const [token] = useState(() => readLinkToken());
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<{ password?: string; confirm?: string; form?: string }>({});
  const [linkUnusable, setLinkUnusable] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);

  useEffect(() => forgetLinkToken(), []);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!token) return;
    const local: typeof errors = {};
    if (password.length < MIN_PASSWORD) local.password = `Use at least ${MIN_PASSWORD} characters.`;
    if (confirm !== password) local.confirm = "The two passwords don't match.";
    setErrors(local);
    if (Object.keys(local).length > 0) return;

    setIsSubmitting(true);
    try {
      await authApi.resetPassword({ token, newPassword: password });
      // Every session was ended server-side; forget this browser's too.
      if (session) await logout();
      navigate("/login", {
        replace: true,
        state: { notice: "Your password has been changed and every device was signed out. Log in with your new password." },
      });
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        setLinkUnusable(true);
      } else if (err instanceof ApiError && err.status === 422) {
        setErrors({ password: err.problem?.detail ?? "Choose a different password." });
      } else if (err instanceof ApiError && err.isRateLimited) {
        setErrors({ form: rateLimitedMessage(err) });
      } else {
        setErrors({ form: "We couldn't change your password just now. Please try again." });
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!token || linkUnusable) {
    return (
      <AuthLayout
        title="This link can't be used"
        subtitle={
          token
            ? "It may have expired, already been used, or been replaced by a newer link."
            : "The link is incomplete. Open it straight from the email, or ask for a new one."
        }
      >
        <div className="flex flex-col gap-3">
          <Link
            to="/forgot-password"
            className="inline-flex items-center justify-center rounded-pill bg-brand-blueStrong px-8 py-[18px] text-base font-semibold text-white shadow-soft hover:bg-blue-700"
          >
            Send me a new link
          </Link>
          <Link to="/login" className="text-center text-sm font-semibold text-brand-blueStrong">
            Back to log in
          </Link>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title="Choose a new password" subtitle="You'll be signed out everywhere, then log in with the new password.">
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <FormField
          label={`New password (at least ${MIN_PASSWORD} characters)`}
          type="password"
          autoComplete="new-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
        />
        <FormField
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          required
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          error={errors.confirm}
        />
        {errors.form && (
          <p role="alert" className="text-sm font-medium text-brand-redStrong">
            {errors.form}
          </p>
        )}
        <Button type="submit" size="lg" disabled={isSubmitting || !password || !confirm} className="mt-2 w-full">
          {isSubmitting ? "Saving…" : "Save new password"}
        </Button>
      </form>
    </AuthLayout>
  );
}
