import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { AuthLayout } from "./AuthLayout";
import { FormField } from "./FormField";
import { Button } from "@/components/ui/Button";
import { useAuth } from "@/lib/auth/AuthContext";
import { ApiError, rateLimitedMessage } from "@/lib/api/ApiError";
import { roleHomePath } from "./roleHomePath";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // e.g. "You've been signed out on every device." from the account page.
  const notice = (location.state as { notice?: string } | null)?.notice;

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
          const until = err.problem?.lockedUntil ? new Date(err.problem.lockedUntil).toLocaleTimeString() : null;
          setFormError(`Too many attempts. Try again ${until ? `after ${until}` : "after a short wait"}.`);
        } else {
          // 401 for wrong credentials — GlobalExceptionHandler keeps the
          // detail generic on purpose (no "email not found" vs "wrong
          // password" distinction, to avoid account enumeration).
          setFormError(err.problem?.detail ?? "Couldn't log you in with those details.");
        }
      } else {
        setFormError("Something went wrong. Please try again.");
      }
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <AuthLayout title="Welcome back" subtitle="Log in to continue your child's learning journey.">
      {notice && (
        <p role="status" className="mb-4 rounded-2xl bg-kid-mint p-3 text-sm font-semibold text-kid-mintDeep">
          {notice}
        </p>
      )}
      <form onSubmit={onSubmit} className="flex flex-col gap-4">
        <FormField
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          error={fieldErrors.email}
        />
        <FormField
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
        />
        {formError && (
          <p role="alert" className="text-sm font-medium text-brand-red">
            {formError}
          </p>
        )}
        <Button type="submit" size="lg" disabled={isSubmitting} className="mt-2 w-full">
          {isSubmitting ? "Logging in…" : "Log in"}
        </Button>
      </form>
      <p className="mt-6 text-center text-sm text-muted">
        New to Bookeyond Kid?{" "}
        <Link to="/register" className="font-semibold text-brand-blueStrong">
          Create an account
        </Link>
      </p>
    </AuthLayout>
  );
}
