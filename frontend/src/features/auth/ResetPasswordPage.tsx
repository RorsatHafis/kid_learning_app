import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button, buttonClasses } from "@/components/ui/Button";
import { authApi } from "@/lib/api/endpoints";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { useAuth } from "@/lib/auth/AuthContext";
import { forgetLinkToken, readLinkToken } from "./linkToken";

const MIN_PASSWORD = 12; // identity/service/PasswordPolicy.MIN_LENGTH

/** What differs between choosing a password from a reset email and from a school invitation. */
const MODES = {
  reset: {
    title: "Choose a new password",
    subtitle: "You'll be signed out everywhere, then log in with the new password.",
    submit: "Save new password",
    submitting: "Saving…",
    send: authApi.resetPassword,
    done: "Your password has been changed and every device was signed out. Log in with your new password.",
    unusable: "It may have expired, already been used, or been replaced by a newer link.",
    incomplete: "The link is incomplete. Open it straight from the email, or ask for a new one.",
    recovery: { to: "/forgot-password", label: "Send me a new link" },
  },
  setup: {
    title: "Set up your account",
    subtitle: "Choose a password for your teacher account. You'll use it with your email address to log in.",
    submit: "Create password",
    submitting: "Creating…",
    send: authApi.completeAccountSetup,
    done: "Your account is ready. Log in to see your school's invitation.",
    unusable:
      "It may have expired (invitation links last 7 days), already been used, or been replaced by a newer invitation. Ask the school to invite you again.",
    incomplete: "The link is incomplete. Open it straight from the invitation email.",
    recovery: { to: "/forgot-password", label: "I already chose a password — reset it" },
  },
} as const;

/**
 * Chooses a password with the link from a reset email. The server ends every session on
 * success and signs nobody in, so this page sends the person to log in. A link that can't
 * be used (expired, used, replaced by a newer one) gets one honest message and a way on.
 */
export function ResetPasswordPage() {
  return <PasswordLinkPage mode="reset" />;
}

/** Chooses the first password of an account created by a school invitation. */
export function SetUpAccountPage() {
  return <PasswordLinkPage mode="setup" />;
}

function PasswordLinkPage({ mode }: { mode: keyof typeof MODES }) {
  const copy = MODES[mode];
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
      await copy.send({ token, newPassword: password });
      // Every session was ended server-side; forget this browser's too.
      if (session) await logout();
      navigate("/login", {
        replace: true,
        state: { notice: copy.done },
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
        subtitle={token ? copy.unusable : copy.incomplete}
      >
        <div className="flex flex-col gap-3">
          <Link
            to={copy.recovery.to}
            className={buttonClasses("solid", "lg", "w-full")}
          >
            {copy.recovery.label}
          </Link>
          <Link to="/login" className="text-center text-sm font-semibold text-tonle-700">
            Back to log in
          </Link>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={copy.title} subtitle={copy.subtitle}>
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
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {errors.form}
          </p>
        )}
        <Button type="submit" size="lg" disabled={isSubmitting || !password || !confirm} className="mt-2 w-full">
          {isSubmitting ? copy.submitting : copy.submit}
        </Button>
      </form>
    </AuthLayout>
  );
}
