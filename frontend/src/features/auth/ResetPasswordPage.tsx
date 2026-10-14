import { useEffect, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button, buttonClasses } from "@/components/ui/Button";
import { authApi } from "@/lib/api/endpoints";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { useAuth } from "@/lib/auth/AuthContext";
import { forgetLinkToken, readLinkToken } from "./linkToken";
import { useMessages } from "@/i18n/catalog";
import { authMessages } from "./messages";

const MIN_PASSWORD = 12; // identity/service/PasswordPolicy.MIN_LENGTH

/** What differs between choosing a password from a reset email and from a school invitation (words: the catalog). */
const MODES = {
  reset: { send: authApi.resetPassword, recoveryTo: "/forgot-password" },
  setup: { send: authApi.completeAccountSetup, recoveryTo: "/forgot-password" },
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
  const mode_ = MODES[mode];
  const m = useMessages(authMessages);
  const p = m.passwordLink;
  const copy = p[mode];
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
    if (password.length < MIN_PASSWORD) local.password = p.tooShort(MIN_PASSWORD);
    if (confirm !== password) local.confirm = p.mismatch;
    setErrors(local);
    if (Object.keys(local).length > 0) return;

    setIsSubmitting(true);
    try {
      await mode_.send({ token, newPassword: password });
      // Every session was ended server-side; forget this browser's too.
      if (session) await logout();
      navigate("/login", {
        replace: true,
        state: { notice: mode === "reset" ? "passwordReset" : "accountReady" },
      });
    } catch (err) {
      if (err instanceof ApiError && err.status === 400) {
        setLinkUnusable(true);
      } else if (err instanceof ApiError && err.status === 422) {
        setErrors({ password: err.problem?.detail ?? p.chooseDifferent });
      } else if (err instanceof ApiError && err.isRateLimited) {
        setErrors({ form: rateLimitedMessage(err) });
      } else {
        setErrors({ form: p.changeFailed });
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  if (!token || linkUnusable) {
    return (
      <AuthLayout
        title={m.linkUnusableTitle}
        subtitle={token ? copy.unusable : copy.incomplete}
      >
        <div className="flex flex-col gap-3">
          <Link
            to={mode_.recoveryTo}
            className={buttonClasses("solid", "lg", "w-full")}
          >
            {copy.recovery}
          </Link>
          <Link to="/login" className="text-center text-sm font-semibold text-tonle-700">
            {m.backToLogIn}
          </Link>
        </div>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout title={copy.title} subtitle={copy.subtitle}>
      <form onSubmit={onSubmit} noValidate className="flex flex-col gap-4">
        <FormField
          label={p.newPassword(MIN_PASSWORD)}
          type="password"
          autoComplete="new-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={errors.password}
        />
        <FormField
          label={p.confirmPassword}
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
