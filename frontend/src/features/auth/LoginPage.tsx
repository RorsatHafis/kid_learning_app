/**
 * Sign-in: email and password, with clear words for wrong details, a locked account and rate limits, then on to
 * the home for the person's role.
 */
import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/lib/auth/AuthContext";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { roleHomePath } from "./roleHomePath";
import { useMessages } from "@/i18n/catalog";
import { authMessages } from "./messages";
import { formatTime } from "@/i18n/format";

export function LoginPage() {
  const { login } = useAuth();
  const m = useMessages(authMessages);
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // A note handed over by the page that sent the person here (e.g. after signing out everywhere).
  const noticeKey = (location.state as { notice?: keyof typeof m.notices } | null)?.notice;
  const notice = noticeKey && noticeKey in m.notices ? m.notices[noticeKey] : null;

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    setFormError(null);
    setFieldErrors({});
    setIsSubmitting(true);
    try {
      const session = await login(email, password);
      const from = (location.state as { from?: string } | null)?.from;
      navigate(from ?? roleHomePath(session.platformRole), { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        if (err.validationErrors) {
          setFieldErrors(err.validationErrors);
        } else if (err.isRateLimited) {
          setFormError(rateLimitedMessage(err));
        } else if (err.status === 423) {
          setFormError(err.problem?.lockedUntil ? m.login.lockedUntil(formatTime(err.problem.lockedUntil)) : m.login.lockedShort);
        } else {
          // 401 for wrong credentials — GlobalExceptionHandler keeps the
          // detail generic on purpose (no "email not found" vs "wrong
          // password" distinction, to avoid account enumeration).
          setFormError(err.problem?.detail ?? m.login.failed);
        }
      } else {
        setFormError(m.somethingWentWrong);
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AuthLayout title={m.login.title} subtitle={m.login.subtitle}>
      {notice && (
        <p role="status" className="mb-4 rounded-[12px] bg-palm-soft p-3 text-sm font-semibold text-palm-deep">
          {notice}
        </p>
      )}
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField
          label={m.email}
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
        />
        <FormField
          label={m.password}
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
        />
        <Link to="/forgot-password" className="-mt-1 w-fit text-sm font-semibold text-tonle-700">
          {m.login.forgot}
        </Link>
        {formError && (
          <p role="alert" className="text-sm font-medium text-brick-deep">
            {formError}
          </p>
        )}
        <Button type="submit" size="lg" disabled={isSubmitting} className="mt-2 w-full">
          {isSubmitting ? m.login.submitting : m.logIn}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-ink-soft">
        {m.login.newHere}{" "}
        <Link to="/register" className="font-semibold text-tonle-700">
          {m.login.createAccount}
        </Link>
      </p>
    </AuthLayout>
  );
}
